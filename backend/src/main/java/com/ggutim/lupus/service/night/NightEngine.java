package com.ggutim.lupus.service.night;

import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.model.GameMode;
import com.ggutim.lupus.model.NightAction;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.NightActionRepository;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Runs the night-turn state machine: which configured role acts next
 * (see {@link #nightOrder(Room)}), whether a selection can be required
 * of it, and recording/validating a selection against its {@link
 * NightActionEffect}. A role with no living holder — or, for a
 * dead-target role like the gravedigger, no eligible target yet —
 * still gets its turn narrated, it just never requires a selection,
 * so the table can never infer a role is gone from the master
 * silently skipping it.
 *
 * <p>Owns the {@link NightAction} history; {@link
 * com.ggutim.lupus.service.GameService} only sees this class's public
 * surface, never {@link NightActionEffect} or the raw repository.
 */
@Service
public class NightEngine {

    /**
     * Narration order for a classic-mode room — the same skeleton as
     * {@link #AFTERLIFE_NIGHT_ORDER} with the afterlife-only ghost and
     * angel turns removed, so the two modes stay in sync by
     * construction rather than as two hand-maintained lists.
     */
    private static final List<Role> CLASSIC_NIGHT_ORDER =
            List.of(Role.CORRUPTED_JUDGE, Role.GRAVEDIGGER, Role.WEREWOLF, Role.GUARDIAN, Role.PRIEST);

    /**
     * Narration order for an afterlife-mode room: the judge goes
     * first, ahead of everyone (if called at all), then the
     * gravedigger, then the ghosts' curse and the angels' protection
     * (both need at least one death to have anyone to wake), then the
     * werewolves, with the priest moving to last so the ghosts' curse
     * is already active by the time the priest inspects.
     */
    private static final List<Role> AFTERLIFE_NIGHT_ORDER =
            List.of(Role.CORRUPTED_JUDGE, Role.GRAVEDIGGER, Role.GHOST, Role.ANGEL, Role.WEREWOLF, Role.GUARDIAN,
                    Role.PRIEST);

    private final NightActionRepository nightActionRepository;
    private final PlayerRepository playerRepository;
    private final Map<Role, NightActionEffect> effects;

    NightEngine(NightActionRepository nightActionRepository, PlayerRepository playerRepository,
                List<NightActionEffect> effects) {
        this.nightActionRepository = nightActionRepository;
        this.playerRepository = playerRepository;
        this.effects = effects.stream().collect(Collectors.toMap(NightActionEffect::role, Function.identity()));
    }

    /**
     * The next configured, round-eligible role after {@code after} in
     * this room's night order (see {@link #nightOrder(Room)}), or
     * empty if none remain. "Round-eligible" is almost always true
     * (see {@link NightActionEffect#isEligibleThisRound}) — only the
     * corrupted judge (gated on the previous day's vote) and, in
     * afterlife mode, the ghosts and angels (gated on someone being
     * dead) care.
     */
    public Optional<Role> nextRole(Room room, Role after) {
        List<Role> order = nightOrder(room);
        int startIndex = after == null ? 0 : order.indexOf(after) + 1;
        for (int i = startIndex; i < order.size(); i++) {
            Role candidate = order.get(i);
            if (!isRoleConfigured(room, candidate)) {
                continue;
            }
            NightActionEffect effect = effects.get(candidate);
            if (effect == null || effect.isEligibleThisRound(room)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private List<Role> nightOrder(Room room) {
        return room.getGameMode() == GameMode.AFTERLIFE ? AFTERLIFE_NIGHT_ORDER : CLASSIC_NIGHT_ORDER;
    }

    /**
     * Whether {@code role} is even part of this room's ruleset. Ghosts
     * and angels are never a configured role count — unlike every
     * other role, they exist automatically in any afterlife-mode room,
     * contingent on someone having died (checked separately via {@link
     * NightActionEffect#isEligibleThisRound}), not on a count the
     * master picked at room creation.
     */
    private boolean isRoleConfigured(Room room, Role role) {
        if (role == Role.GHOST || role == Role.ANGEL) {
            return true;
        }
        return room.getRoleCounts().getOrDefault(role, 0) > 0;
    }

    /**
     * Records the master's choice of target for {@code role}, applies
     * its immediate effect if it has one, and re-selecting simply
     * overwrites the previous choice.
     */
    public void recordSelection(Room room, Role role, Long targetId) {
        if (role == Role.GHOST) {
            recordGhostSelection(room, targetId);
            return;
        }

        Player target = requireEligibleTarget(room, role, targetId);
        if (role == Role.GUARDIAN && target.getId().equals(previousRoundGuardianTarget(room))) {
            throw new InvalidGamePhaseException("The guardian can't protect the same player two nights in a row");
        }
        NightActionEffect effect = effects.get(role);
        if (effect != null) {
            effect.validateTarget(target);
        }

        NightAction action = findAction(room, role)
                .orElseGet(() -> new NightAction(room, room.getRoundNumber(), role));
        action.setTargetPlayerId(target.getId());

        if (effect != null) {
            effect.apply(target).ifPresent(action::setResultAlignment);
        }
        nightActionRepository.save(action);

        if (role == Role.ANGEL && isCursedThisRound(room, target.getId())) {
            target.setProtectionBlocked(true);
            playerRepository.save(target);
        }
    }

    /**
     * Ghosts curse two players collectively, toggled one click at a
     * time: an unselected player fills the first empty slot, clicking
     * an already-selected player clears that slot (shifting the second
     * into the first, if any), and a third distinct player is ignored
     * once both slots are full — the master must deselect one first.
     */
    private void recordGhostSelection(Room room, Long targetId) {
        Player target = requireEligibleTarget(room, Role.GHOST, targetId);
        NightActionEffect effect = effects.get(Role.GHOST);
        if (effect != null) {
            effect.validateTarget(target);
        }

        NightAction action = findAction(room, Role.GHOST)
                .orElseGet(() -> new NightAction(room, room.getRoundNumber(), Role.GHOST));

        if (targetId.equals(action.getTargetPlayerId())) {
            action.setTargetPlayerId(action.getSecondTargetPlayerId());
            action.setSecondTargetPlayerId(null);
        } else if (targetId.equals(action.getSecondTargetPlayerId())) {
            action.setSecondTargetPlayerId(null);
        } else if (action.getTargetPlayerId() == null) {
            action.setTargetPlayerId(target.getId());
        } else if (action.getSecondTargetPlayerId() == null) {
            action.setSecondTargetPlayerId(target.getId());
        }

        nightActionRepository.save(action);
    }

    /**
     * Throws if {@code role} has a living holder and an eligible
     * target but no selection was recorded this round; no-ops
     * otherwise (dead holder, or a dead-target role with nobody dead
     * yet — both cases where there was genuinely nothing to select).
     */
    public void requireSelectionIfNeeded(Room room, Role role) {
        if (!requiresSelection(role) || !roleHasSelectableHolder(room, role) || !roleHasEligibleTarget(room, role)) {
            return;
        }
        if (role == Role.GHOST) {
            requireGhostSelectionIfNeeded(room);
            return;
        }
        boolean hasTarget = findAction(room, role)
                .map(NightAction::getTargetPlayerId)
                .isPresent();
        if (!hasTarget) {
            throw new InvalidGamePhaseException("Select " + role + "'s target before advancing");
        }
    }

    /**
     * Ghosts must curse two players, unless fewer than two are even
     * alive to choose from — in which case as many as are available is
     * enough.
     */
    private void requireGhostSelectionIfNeeded(Room room) {
        int aliveCount = playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()).size();
        int required = Math.min(2, aliveCount);
        long selected = findAction(room, Role.GHOST)
                .map(action -> (action.getTargetPlayerId() != null ? 1 : 0)
                        + (action.getSecondTargetPlayerId() != null ? 1 : 0))
                .orElse(0);
        if (selected < required) {
            throw new InvalidGamePhaseException("Select the ghosts' curse targets before advancing");
        }
    }

    /**
     * Applies every deferred-kill role's recorded target this round
     * (the werewolves', and the corrupted judge's when active) via
     * {@link NightActionEffect#applyDeferredKill}, and clears the
     * room's current-turn state. Doesn't touch {@code phase} — that's
     * the caller's call once it's also checked for a winner. Returns
     * the ids of players who actually died — a target isn't
     * necessarily one of them (e.g. the survivor's extra life
     * absorbing a werewolf kill), so this can differ from {@link
     * #findLastNightVictims}.
     */
    public List<Long> resolveDeferredKillsAndClearState(Room room) {
        Set<Long> victimIds = new LinkedHashSet<>();
        for (Role role : nightOrder(room)) {
            NightActionEffect effect = effects.get(role);
            if (effect == null || !effect.isDeferredKill()) {
                continue;
            }
            Long targetId = findAction(room, role).map(NightAction::getTargetPlayerId).orElse(null);
            if (targetId == null) {
                continue;
            }
            if (role == Role.WEREWOLF && isProtectedThisRound(room, targetId)) {
                continue;
            }
            Player target = playerRepository.findById(targetId)
                    .orElseThrow(() -> new PlayerNotFoundException(targetId));
            boolean died = effect.applyDeferredKill(target);
            playerRepository.save(target);
            if (died) {
                victimIds.add(targetId);
            }
        }

        room.setCurrentNightRole(null);
        room.setCurrentNightStepKind(null);

        return List.copyOf(victimIds);
    }

    /**
     * Read-only lookup of this round's deferred-kill *targets* —
     * not necessarily deaths, see {@link #resolveDeferredKillsAndClearState}
     * — without applying anything. Used for DTO assembly (e.g. narrating
     * who died once the deferred kills are actually resolved at dawn).
     */
    public List<Long> findLastNightVictims(Room room) {
        return deferredKillTargetsThisRound(room);
    }

    private List<Long> deferredKillTargetsThisRound(Room room) {
        Set<Long> victimIds = new LinkedHashSet<>();
        for (Role role : nightOrder(room)) {
            NightActionEffect effect = effects.get(role);
            if (effect == null || !effect.isDeferredKill()) {
                continue;
            }
            findAction(room, role).map(NightAction::getTargetPlayerId).ifPresent(victimIds::add);
        }
        return List.copyOf(victimIds);
    }

    /**
     * Whether {@code playerId} is one of the ghosts' two curse targets
     * this round — used to flip the priest's displayed inspect result
     * (see {@code GameService}'s DTO assembly) and to burn an angel's
     * future protection eligibility (see {@link #recordSelection}).
     * The curse expires after one night for free, since it's read
     * straight off this round's {@link NightAction} and never
     * persisted anywhere durable.
     */
    public boolean isCursedThisRound(Room room, Long playerId) {
        return findAction(room, Role.GHOST)
                .map(action -> playerId.equals(action.getTargetPlayerId())
                        || playerId.equals(action.getSecondTargetPlayerId()))
                .orElse(false);
    }

    /**
     * The player the guardian protected last round, if any — {@code
     * null} on round 1, or once there's no matching {@link NightAction}
     * for the previous round. Enforced against re-selection in {@link
     * #recordSelection}; also exposed so the master's client can grey
     * that player out rather than let it round-trip as a rejected pick
     * (see {@code GameService#buildMasterGameState}).
     */
    public Long previousRoundGuardianTarget(Room room) {
        return nightActionRepository
                .findByRoomIdAndRoundNumberAndRole(room.getId(), room.getRoundNumber() - 1, Role.GUARDIAN)
                .map(NightAction::getTargetPlayerId)
                .orElse(null);
    }

    /**
     * Whether {@code playerId} is protected from the werewolves this
     * round — by the angels or the guardian, both of which use the
     * same kill-blocking mechanism — checked only against the
     * werewolves' kill (see {@link #resolveDeferredKillsAndClearState});
     * the corrupted judge's kill ignores it entirely.
     */
    private boolean isProtectedThisRound(Room room, Long playerId) {
        return findAction(room, Role.ANGEL).map(action -> playerId.equals(action.getTargetPlayerId())).orElse(false)
                || findAction(room, Role.GUARDIAN).map(action -> playerId.equals(action.getTargetPlayerId()))
                        .orElse(false);
    }

    public Optional<NightAction> findAction(Room room, Role role) {
        return nightActionRepository.findByRoomIdAndRoundNumberAndRole(room.getId(), room.getRoundNumber(), role);
    }

    private boolean requiresSelection(Role role) {
        NightActionEffect effect = effects.get(role);
        return effect == null || effect.requiresSelection();
    }

    /**
     * Whether {@code role} currently has anyone it could select at
     * all. Roles targeting the living (the default) always do; a
     * dead-target role (e.g. the gravedigger) has nothing to select
     * until at least one player has died.
     */
    private boolean roleHasEligibleTarget(Room room, Role role) {
        if (!requiresDeadTarget(role)) {
            return true;
        }
        return !playerRepository.findByRoomIdAndAliveFalseOrderByJoinedAtAsc(room.getId()).isEmpty();
    }

    private boolean requiresDeadTarget(Role role) {
        NightActionEffect effect = effects.get(role);
        return effect != null && effect.requiresDeadTarget();
    }

    /**
     * Looks up {@code targetId} within {@code room} and checks it's
     * alive or dead as required by {@code role}'s effect (living for
     * most roles, dead for e.g. the gravedigger).
     */
    private Player requireEligibleTarget(Room room, Role role, Long targetId) {
        Player player = playerRepository.findById(targetId)
                .filter(p -> p.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new PlayerNotFoundException(targetId));

        boolean requiresDead = requiresDeadTarget(role);
        if (requiresDead && player.isAlive()) {
            throw new InvalidGamePhaseException("Player " + targetId + " is not dead");
        }
        if (!requiresDead && !player.isAlive()) {
            throw new InvalidGamePhaseException("Player " + targetId + " is not alive");
        }
        return player;
    }

    /**
     * Whether {@code role} has a holder who could plausibly act
     * tonight. For every ordinary role that's a living player: a
     * deferred kill (werewolves' or the corrupted judge's) only takes
     * effect the following day — the target is still fully alive for
     * the rest of tonight, including their own turn if they hold
     * another acting role, so a pending target is not excluded here.
     *
     * <p>Ghosts and angels invert this: they only ever exist on a
     * player whose {@code role} flipped precisely because they died —
     * see {@code RoleAssigner#applyAfterlifeDeathTransition} — so their
     * holder check looks among the dead, not the living.
     */
    private boolean roleHasSelectableHolder(Room room, Role role) {
        if (role == Role.GHOST || role == Role.ANGEL) {
            return playerRepository.findByRoomIdAndAliveFalseOrderByJoinedAtAsc(room.getId()).stream()
                    .anyMatch(player -> player.getRole() == role);
        }
        return playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()).stream()
                .anyMatch(player -> player.getRole() == role);
    }
}
