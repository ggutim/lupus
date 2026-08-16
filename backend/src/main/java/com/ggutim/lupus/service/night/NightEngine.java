package com.ggutim.lupus.service.night;

import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.model.NightAction;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.NightActionRepository;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Runs the night-turn state machine: which configured role acts next
 * (see {@link #NIGHT_ORDER}), whether a selection can be required of
 * it, and recording/validating a selection against its {@link
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

    /** Narration order for roles with a night action. Adding a role's night turn means appending here. */
    private static final List<Role> NIGHT_ORDER = List.of(Role.WEREWOLF, Role.PRIEST, Role.GRAVEDIGGER);

    private final NightActionRepository nightActionRepository;
    private final PlayerRepository playerRepository;
    private final Map<Role, NightActionEffect> effects;

    NightEngine(NightActionRepository nightActionRepository, PlayerRepository playerRepository,
                List<NightActionEffect> effects) {
        this.nightActionRepository = nightActionRepository;
        this.playerRepository = playerRepository;
        this.effects = effects.stream().collect(Collectors.toMap(NightActionEffect::role, Function.identity()));
    }

    /** The next configured role after {@code after} in {@link #NIGHT_ORDER}, or empty if none remain. */
    public Optional<Role> nextRole(Room room, Role after) {
        int startIndex = after == null ? 0 : NIGHT_ORDER.indexOf(after) + 1;
        for (int i = startIndex; i < NIGHT_ORDER.size(); i++) {
            Role candidate = NIGHT_ORDER.get(i);
            if (room.getRoleCounts().getOrDefault(candidate, 0) > 0) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Records the master's choice of target for {@code role}, applies
     * its immediate effect if it has one, and re-selecting simply
     * overwrites the previous choice.
     */
    public void recordSelection(Room room, Role role, Long targetId) {
        Player target = requireEligibleTarget(room, role, targetId);
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
    }

    /**
     * Throws if {@code role} has a living holder and an eligible
     * target but no selection was recorded this round; no-ops
     * otherwise (dead holder, or a dead-target role with nobody dead
     * yet — both cases where there was genuinely nothing to select).
     */
    public void requireSelectionIfNeeded(Room room, Role role) {
        if (!roleHasSelectableHolder(room, role) || !roleHasEligibleTarget(room, role)) {
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
     * Applies the werewolves' deferred kill (if a victim was chosen
     * this round) and clears the room's current-turn state. Doesn't
     * touch {@code phase} — that's the caller's call once it's also
     * checked for a winner.
     */
    public void resolveDeferredKillAndClearState(Room room) {
        Long victimId = findAction(room, Role.WEREWOLF)
                .map(NightAction::getTargetPlayerId)
                .orElse(null);

        if (victimId != null) {
            Player victim = playerRepository.findById(victimId)
                    .orElseThrow(() -> new PlayerNotFoundException(victimId));
            victim.kill();
            playerRepository.save(victim);
        }

        room.setCurrentNightRole(null);
        room.setCurrentNightStepKind(null);
    }

    public Optional<NightAction> findAction(Room room, Role role) {
        return nightActionRepository.findByRoomIdAndRoundNumberAndRole(room.getId(), room.getRoundNumber(), role);
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
     * Whether {@code role} has a living player who could plausibly act
     * tonight — excluding whoever the werewolves have already chosen as
     * this round's victim, even though that kill isn't applied until
     * morning, so a role sharing the werewolves' target isn't asked to
     * act on a technicality.
     */
    private boolean roleHasSelectableHolder(Room room, Role role) {
        Long pendingWerewolfVictimId = findAction(room, Role.WEREWOLF)
                .map(NightAction::getTargetPlayerId)
                .orElse(null);

        return playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()).stream()
                .filter(player -> !player.getId().equals(pendingWerewolfVictimId))
                .anyMatch(player -> player.getRole() == role);
    }
}
