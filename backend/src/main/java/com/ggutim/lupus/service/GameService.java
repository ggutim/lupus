package com.ggutim.lupus.service;

import com.ggutim.lupus.dto.GameUpdatedMessage;
import com.ggutim.lupus.dto.MasterGameStateResponse;
import com.ggutim.lupus.dto.MasterPlayerView;
import com.ggutim.lupus.dto.PlayerResponse;
import com.ggutim.lupus.dto.VillageOverviewResponse;
import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.NightAction;
import com.ggutim.lupus.model.NightStepKind;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.model.RoomStatus;
import com.ggutim.lupus.repository.NightActionRepository;
import com.ggutim.lupus.repository.PlayerRepository;
import com.ggutim.lupus.repository.RoomRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives the master's "deck of cards" once a game has started: role
 * assignment, phase transitions, night/vote selections, and win
 * detection. Kept separate from {@link PlayerService} (pre-game
 * lobby: joining/kicking) and {@link RoomService} (room creation and
 * master-token validation), which this class depends on.
 *
 * <p>Which roles act at night, in what order, is data (see {@link
 * #NIGHT_ORDER}, {@link NightActionEffect}, {@link WinConditionCheck}),
 * not a phase enum value per role — a role with no living holder still
 * gets its {@link NightStepKind#WAKE_UP}/{@link NightStepKind#SELECT}
 * beats narrated, it just never requires a selection, so the table can
 * never infer a role is gone from the master silently skipping it.
 */
@Service
public class GameService {

    /** Narration order for roles with a night action. Adding a role's night turn means appending here. */
    private static final List<Role> NIGHT_ORDER = List.of(Role.WEREWOLF, Role.PRIEST);

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final NightActionRepository nightActionRepository;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<Role, NightActionEffect> nightActionEffects;
    private final List<WinConditionCheck> winConditionChecks;

    public GameService(RoomRepository roomRepository, PlayerRepository playerRepository,
                        NightActionRepository nightActionRepository, RoomService roomService,
                        SimpMessagingTemplate messagingTemplate, List<NightActionEffect> nightActionEffects,
                        List<WinConditionCheck> winConditionChecks) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.nightActionRepository = nightActionRepository;
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
        this.nightActionEffects = nightActionEffects.stream()
                .collect(Collectors.toMap(NightActionEffect::role, Function.identity()));
        this.winConditionChecks = winConditionChecks;
    }

    /**
     * Assigns roles to every joined player and moves the room into its
     * first phase. Called by {@link PlayerService#startGame} once it has
     * validated the master token and the minimum player count.
     */
    @Transactional
    public void startGame(Room room, List<Player> players) {
        assignRoles(room, players);

        room.start();
        room.setRoundNumber(1);
        room.setPhase(GamePhase.ROLES_ASSIGNED);
        roomRepository.save(room);

        broadcastGameUpdated(room);
    }

    public MasterGameStateResponse getGameState(String code, String masterToken) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        ensureGameStarted(room);
        return buildMasterGameState(room);
    }

    /**
     * The public village roster (nicknames and alive/dead status, no
     * roles) visible to any player or the master. Uses the same
     * MORNING_REVEAL-delayed visibility as {@link PlayerResponse#visibleDuring},
     * so a player checking the overview can't spoil the master's reveal.
     */
    public VillageOverviewResponse getVillageOverview(String code) {
        Room room = roomRepository.findByCode(code)
                .orElseThrow(() -> new RoomNotFoundException(code));

        List<PlayerResponse> players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .map(player -> PlayerResponse.visibleDuring(player, room.getPhase()))
                .toList();

        return new VillageOverviewResponse(players);
    }

    /**
     * Records the master's choice of target for whichever role is
     * currently active (see {@link Room#getCurrentNightRole()}), applies
     * that role's immediate effect if it has one, and re-selecting simply
     * overwrites the previous choice.
     */
    @Transactional
    public MasterGameStateResponse selectNightTarget(String code, String masterToken, Long targetId) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        requireNightSelectStep(room);

        Role role = room.getCurrentNightRole();
        Player target = requireAlivePlayerInRoom(room, targetId);
        if (role == Role.WEREWOLF && target.getRole() == Role.WEREWOLF) {
            throw new InvalidGamePhaseException("Werewolves cannot select another werewolf as their victim");
        }

        NightAction action = currentNightAction(room, role)
                .orElseGet(() -> new NightAction(room, room.getRoundNumber(), role));
        action.setTargetPlayerId(target.getId());

        NightActionEffect effect = nightActionEffects.get(role);
        if (effect != null) {
            effect.apply(target).ifPresent(action::setResultAlignment);
        }
        nightActionRepository.save(action);

        broadcastGameUpdated(room);
        return buildMasterGameState(room);
    }

    @Transactional
    public MasterGameStateResponse selectVoteVictim(String code, String masterToken, Long targetId) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        requirePhase(room, GamePhase.VOTE_SELECT_TARGET);

        if (targetId == null) {
            room.setPendingVoteVictimId(null);
        } else {
            Player target = requireAlivePlayerInRoom(room, targetId);
            room.setPendingVoteVictimId(target.getId());
        }
        roomRepository.save(room);

        broadcastGameUpdated(room);
        return buildMasterGameState(room);
    }

    @Transactional
    public MasterGameStateResponse advancePhase(String code, String masterToken) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        ensureGameStarted(room);

        switch (room.getPhase()) {
            case ROLES_ASSIGNED -> room.setPhase(GamePhase.NIGHT_START);
            case NIGHT_START -> beginNightActions(room);
            case NIGHT_ACTIONS -> advanceNightActions(room);
            case MORNING_REVEAL -> room.setPhase(GamePhase.DISCUSSION);
            case DISCUSSION -> room.setPhase(GamePhase.VOTE_SELECT_TARGET);
            case VOTE_SELECT_TARGET -> resolveVoteAndAdvance(room);
            case GAME_OVER -> throw new InvalidGamePhaseException("The game has already ended");
        }

        roomRepository.save(room);
        broadcastGameUpdated(room);
        return buildMasterGameState(room);
    }

    private void assignRoles(Room room, List<Player> players) {
        int werewolfCount = room.getRoleCounts().getOrDefault(Role.WEREWOLF, 0);
        int priestCount = room.getRoleCounts().getOrDefault(Role.PRIEST, 0);

        if (werewolfCount + priestCount > players.size()) {
            throw new InvalidRulesetException(
                    "werewolfCount and priestCount cannot exceed the number of joined players");
        }

        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int index = 0;
        for (int i = 0; i < werewolfCount; i++) {
            shuffled.get(index++).setRole(Role.WEREWOLF);
        }
        for (int i = 0; i < priestCount; i++) {
            shuffled.get(index++).setRole(Role.PRIEST);
        }
        while (index < shuffled.size()) {
            shuffled.get(index++).setRole(Role.VILLAGER);
        }

        playerRepository.saveAll(shuffled);
    }

    private void beginNightActions(Room room) {
        Role firstRole = nextNightRole(room, null);
        if (firstRole == null) {
            resolveNightAndEnterMorningReveal(room);
            return;
        }
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(firstRole);
        room.setCurrentNightStepKind(NightStepKind.WAKE_UP);
    }

    private void advanceNightActions(Room room) {
        if (room.getCurrentNightStepKind() == NightStepKind.WAKE_UP) {
            room.setCurrentNightStepKind(NightStepKind.SELECT);
            return;
        }

        Role finishedRole = room.getCurrentNightRole();
        requireSelectionIfHolderSelectable(room, finishedRole);

        Role nextRole = nextNightRole(room, finishedRole);
        if (nextRole == null) {
            resolveNightAndEnterMorningReveal(room);
            return;
        }
        room.setCurrentNightRole(nextRole);
        room.setCurrentNightStepKind(NightStepKind.WAKE_UP);
    }

    /**
     * Resolves the night's werewolf kill (deferred until now so an
     * already-chosen victim doesn't become ineligible for another
     * role's selection earlier the same night — see {@link
     * NightActionEffect}), checks for a winner, and enters
     * MORNING_REVEAL if the game continues.
     */
    private void resolveNightAndEnterMorningReveal(Room room) {
        Long victimId = currentNightAction(room, Role.WEREWOLF)
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

        Alignment winner = checkWinCondition(room);
        if (winner != null) {
            endGame(room, winner);
            return;
        }

        room.setPhase(GamePhase.MORNING_REVEAL);
    }

    private void resolveVoteAndAdvance(Room room) {
        Long voteVictimId = room.getPendingVoteVictimId();
        if (voteVictimId != null) {
            Player voted = playerRepository.findById(voteVictimId)
                    .orElseThrow(() -> new PlayerNotFoundException(voteVictimId));
            voted.kill();
            playerRepository.save(voted);
        }
        room.setPendingVoteVictimId(null);

        Alignment winner = checkWinCondition(room);
        if (winner != null) {
            endGame(room, winner);
        } else {
            room.setRoundNumber(room.getRoundNumber() + 1);
            room.setPhase(GamePhase.NIGHT_START);
        }
    }

    private void endGame(Room room, Alignment winner) {
        room.setWinner(winner);
        room.setPhase(GamePhase.GAME_OVER);
    }

    private Alignment checkWinCondition(Room room) {
        for (WinConditionCheck check : winConditionChecks) {
            Optional<Alignment> result = check.check(room);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return null;
    }

    /** The next configured role after {@code after} in {@link #NIGHT_ORDER}, or {@code null} if none remain. */
    private Role nextNightRole(Room room, Role after) {
        int startIndex = after == null ? 0 : NIGHT_ORDER.indexOf(after) + 1;
        for (int i = startIndex; i < NIGHT_ORDER.size(); i++) {
            Role candidate = NIGHT_ORDER.get(i);
            if (room.getRoleCounts().getOrDefault(candidate, 0) > 0) {
                return candidate;
            }
        }
        return null;
    }

    private void requireSelectionIfHolderSelectable(Room room, Role role) {
        if (!roleHasSelectableHolder(room, role)) {
            return;
        }
        boolean hasTarget = currentNightAction(room, role)
                .map(NightAction::getTargetPlayerId)
                .isPresent();
        if (!hasTarget) {
            throw new InvalidGamePhaseException("Select " + role + "'s target before advancing");
        }
    }

    /**
     * Whether {@code role} has a living player who could plausibly act
     * tonight — excluding whoever the werewolves have already chosen as
     * this round's victim, even though that kill isn't applied until
     * morning, so a role sharing the werewolves' target isn't asked to
     * act on a technicality.
     */
    private boolean roleHasSelectableHolder(Room room, Role role) {
        Long pendingWerewolfVictimId = currentNightAction(room, Role.WEREWOLF)
                .map(NightAction::getTargetPlayerId)
                .orElse(null);

        return playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()).stream()
                .filter(player -> !player.getId().equals(pendingWerewolfVictimId))
                .anyMatch(player -> player.getRole() == role);
    }

    private Optional<NightAction> currentNightAction(Room room, Role role) {
        return nightActionRepository.findByRoomIdAndRoundNumberAndRole(room.getId(), room.getRoundNumber(), role);
    }

    private Player requireAlivePlayerInRoom(Room room, Long playerId) {
        Player player = playerRepository.findById(playerId)
                .filter(p -> p.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new PlayerNotFoundException(playerId));

        if (!player.isAlive()) {
            throw new InvalidGamePhaseException("Player " + playerId + " is not alive");
        }
        return player;
    }

    private void requirePhase(Room room, GamePhase expected) {
        ensureGameStarted(room);
        if (room.getPhase() != expected) {
            throw new InvalidGamePhaseException(
                    "This action requires phase " + expected + " but the room is in " + room.getPhase());
        }
    }

    private void requireNightSelectStep(Room room) {
        ensureGameStarted(room);
        if (room.getPhase() != GamePhase.NIGHT_ACTIONS || room.getCurrentNightStepKind() != NightStepKind.SELECT) {
            throw new InvalidGamePhaseException("Not currently selecting a night target");
        }
    }

    private void ensureGameStarted(Room room) {
        if (room.getStatus() != RoomStatus.STARTED || room.getPhase() == null) {
            throw new InvalidGamePhaseException("The game has not started yet");
        }
    }

    private MasterGameStateResponse buildMasterGameState(Room room) {
        List<MasterPlayerView> players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .map(MasterPlayerView::from)
                .toList();

        NightAction currentAction = room.getCurrentNightRole() == null ? null
                : currentNightAction(room, room.getCurrentNightRole()).orElse(null);

        Long lastNightVictimId = currentNightAction(room, Role.WEREWOLF)
                .map(NightAction::getTargetPlayerId)
                .orElse(null);

        return new MasterGameStateResponse(
                room.getCode(),
                room.getPhase(),
                room.getRoundNumber(),
                players,
                room.getCurrentNightRole(),
                room.getCurrentNightStepKind(),
                currentAction == null ? null : currentAction.getTargetPlayerId(),
                currentAction == null ? null : currentAction.getResultAlignment(),
                lastNightVictimId,
                room.getPendingVoteVictimId(),
                room.getWinner());
    }

    private void broadcastGameUpdated(Room room) {
        messagingTemplate.convertAndSend("/topic/rooms/" + room.getCode() + "/game",
                new GameUpdatedMessage(room.getCode()));
    }
}
