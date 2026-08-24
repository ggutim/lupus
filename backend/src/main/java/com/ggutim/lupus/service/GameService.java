package com.ggutim.lupus.service;

import com.ggutim.lupus.dto.GameUpdatedMessage;
import com.ggutim.lupus.dto.KillerGuessResponse;
import com.ggutim.lupus.dto.MasterGameStateResponse;
import com.ggutim.lupus.dto.MasterPlayerView;
import com.ggutim.lupus.dto.PlayerResponse;
import com.ggutim.lupus.dto.VillageOverviewResponse;
import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.GameMode;
import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.NightAction;
import com.ggutim.lupus.model.NightStepKind;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.model.RoomStatus;
import com.ggutim.lupus.repository.PlayerRepository;
import com.ggutim.lupus.repository.RoomRepository;
import com.ggutim.lupus.service.night.NightEngine;
import com.ggutim.lupus.service.night.RoundEvent;
import com.ggutim.lupus.service.night.SoloWinEvaluator;
import com.ggutim.lupus.service.night.WinConditionEvaluator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives the master's "deck of cards" once a game has started: phase
 * transitions, night/vote selections, and win detection. Kept
 * separate from {@link PlayerService} (pre-game lobby: joining/
 * kicking) and {@link RoomService} (room creation and master-token
 * validation), which this class depends on.
 *
 * <p>The turn-by-turn night mechanics live in {@link NightEngine},
 * role assignment in {@link RoleAssigner}, and win checking in {@link
 * WinConditionEvaluator} (faction wins) and {@link SoloWinEvaluator}
 * (a single role winning alone, e.g. the idiot) — this class only owns
 * the skeleton phase sequence ({@link GamePhase}) and wires those
 * collaborators together, so it stays readable as new roles are added
 * to the collaborators without touching this control flow.
 */
@Service
public class GameService {

    /** Day phases where the village is awake, so the killer can reveal (see {@link #revealKillerAndGuess}). */
    private static final Set<GamePhase> KILLER_REVEAL_PHASES =
            Set.of(GamePhase.MORNING_REVEAL, GamePhase.DISCUSSION, GamePhase.VOTE_SELECT_TARGET);

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoleAssigner roleAssigner;
    private final NightEngine nightEngine;
    private final WinConditionEvaluator winConditionEvaluator;
    private final SoloWinEvaluator soloWinEvaluator;

    public GameService(RoomRepository roomRepository, PlayerRepository playerRepository, RoomService roomService,
                        SimpMessagingTemplate messagingTemplate, RoleAssigner roleAssigner, NightEngine nightEngine,
                        WinConditionEvaluator winConditionEvaluator, SoloWinEvaluator soloWinEvaluator) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
        this.roleAssigner = roleAssigner;
        this.nightEngine = nightEngine;
        this.winConditionEvaluator = winConditionEvaluator;
        this.soloWinEvaluator = soloWinEvaluator;
    }

    /**
     * Assigns roles to every joined player and moves the room into its
     * first phase. Called by {@link PlayerService#startGame} once it has
     * validated the master token and the minimum player count.
     *
     * <p>Skipped for a narrate-only room with manually-assigned roles —
     * those players already carry their role and extra lives, set by
     * {@link PlayerService#addPlayerManually} at add-time.
     */
    @Transactional
    public void startGame(Room room, List<Player> players) {
        if (!room.isManualRoles()) {
            roleAssigner.assign(room, players);
        }

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

    @Transactional
    public MasterGameStateResponse selectNightTarget(String code, String masterToken, Long targetId) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        requireNightSelectStep(room);

        nightEngine.recordSelection(room, room.getCurrentNightRole(), targetId);

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

    /**
     * The killer's once-per-game power: reveal themselves to the table
     * and guess {@code targetPlayerId}'s exact role, in one atomic
     * action. Unlike every other role's power, this isn't a scripted
     * night turn — it's a voluntary action the killer can trigger on
     * any day the village is awake (see {@link #requireKillerRevealPhase}),
     * so it doesn't touch {@code currentNightRole}/{@code currentNightStepKind}
     * or the room's phase at all unless it ends the game.
     *
     * <p>A correct guess kills the target outright — ignoring the
     * guardian/angel's protection and the survivor's extra life,
     * exactly like the corrupted judge's kill (see {@link
     * com.ggutim.lupus.service.night.CorruptedJudgeKillEffect}) —
     * since neither mechanism is even consulted here. A wrong guess
     * kills the killer instead. Either way this uses up the power for
     * the rest of the game.
     */
    @Transactional
    public KillerGuessResponse revealKillerAndGuess(String code, String masterToken, Long targetPlayerId,
            Role guessedRole) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        ensureGameStarted(room);
        requireKillerRevealPhase(room);

        Player killer = requireAliveKillerWithUnusedReveal(room);
        Player target = requireAlivePlayerInRoom(room, targetPlayerId);
        if (target.getId().equals(killer.getId())) {
            throw new InvalidGamePhaseException("The killer cannot guess himself");
        }

        killer.setKillerRevealUsed(true);

        boolean correct = target.getRole() == guessedRole;
        Long victimId;
        if (correct) {
            target.kill();
            victimId = target.getId();
        } else {
            killer.kill();
            victimId = killer.getId();
        }
        playerRepository.save(killer);
        playerRepository.save(target);
        applyAfterlifeTransition(room, victimId);

        resolveWinnerIfAny(room, new RoundEvent(RoundEvent.Cause.KILLER_GUESS, List.of(victimId)));
        roomRepository.save(room);

        broadcastGameUpdated(room);
        return new KillerGuessResponse(correct, buildMasterGameState(room));
    }

    private void requireKillerRevealPhase(Room room) {
        if (!KILLER_REVEAL_PHASES.contains(room.getPhase())) {
            throw new InvalidGamePhaseException(
                    "The killer can only reveal while the village is awake during the day");
        }
    }

    private Player requireAliveKillerWithUnusedReveal(Room room) {
        Player killer = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .filter(player -> player.getRole() == Role.KILLER)
                .findFirst()
                .orElseThrow(() -> new InvalidGamePhaseException("This room has no killer"));

        if (!killer.isAlive()) {
            throw new InvalidGamePhaseException("The killer is dead and cannot reveal");
        }
        if (killer.isKillerRevealUsed()) {
            throw new InvalidGamePhaseException("The killer has already used their reveal this game");
        }
        return killer;
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

    private void beginNightActions(Room room) {
        Role firstRole = nightEngine.nextRole(room, null).orElse(null);
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
        nightEngine.requireSelectionIfNeeded(room, finishedRole);

        Role nextRole = nightEngine.nextRole(room, finishedRole).orElse(null);
        if (nextRole == null) {
            resolveNightAndEnterMorningReveal(room);
            return;
        }
        room.setCurrentNightRole(nextRole);
        room.setCurrentNightStepKind(NightStepKind.WAKE_UP);
    }

    /**
     * Resolves the night's deferred kills (werewolves', and the
     * corrupted judge's when active — see {@link
     * NightEngine#resolveDeferredKillsAndClearState}), checks for a
     * winner, and enters MORNING_REVEAL if the game continues.
     */
    private void resolveNightAndEnterMorningReveal(Room room) {
        List<Long> victimIds = nightEngine.resolveDeferredKillsAndClearState(room);
        for (Long victimId : victimIds) {
            applyAfterlifeTransition(room, victimId);
        }

        if (resolveWinnerIfAny(room, new RoundEvent(RoundEvent.Cause.NIGHT_KILL, victimIds))) {
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
            applyAfterlifeTransition(room, voteVictimId);
        }
        room.setPendingVoteVictimId(null);

        // Public trigger for the corrupted judge's conditional night turn — see NightActionEffect#isEligibleThisRound.
        room.setNoOneVotedOutPreviousDay(voteVictimId == null);

        if (resolveWinnerIfAny(room, new RoundEvent(RoundEvent.Cause.VOTE_KILL,
                voteVictimId == null ? List.of() : List.of(voteVictimId)))) {
            return;
        }

        room.setRoundNumber(room.getRoundNumber() + 1);
        room.setPhase(GamePhase.NIGHT_START);
    }

    /**
     * Checks for a solo win first (e.g. the idiot voted out), then a
     * faction win, ending the game and returning {@code true} if
     * either fires. A solo win takes priority since it's the more
     * specific outcome, though in practice the two can't both fire
     * from the same death today.
     */
    private boolean resolveWinnerIfAny(Room room, RoundEvent event) {
        Optional<Role> soloWinner = soloWinEvaluator.evaluate(room, event);
        if (soloWinner.isPresent()) {
            endGameWithSoloWinner(room, soloWinner.get());
            return true;
        }

        Optional<Alignment> winner = winConditionEvaluator.evaluate(room);
        if (winner.isPresent()) {
            endGame(room, winner.get());
            return true;
        }

        return false;
    }

    private void endGame(Room room, Alignment winner) {
        room.setWinner(winner);
        room.setPhase(GamePhase.GAME_OVER);
    }

    private void endGameWithSoloWinner(Room room, Role role) {
        room.setWinningRole(role);
        room.setPhase(GamePhase.GAME_OVER);
    }

    /**
     * Afterlife mode only: turns a just-died player into a ghost/angel
     * (no-op in classic mode or for the idiot — see {@link
     * RoleAssigner#applyAfterlifeDeathTransition}). Called from both
     * places a player can die: the night's deferred-kill resolution and
     * the day vote.
     */
    private void applyAfterlifeTransition(Room room, Long playerId) {
        if (room.getGameMode() != GameMode.AFTERLIFE) {
            return;
        }
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException(playerId));
        roleAssigner.applyAfterlifeDeathTransition(room, player);
        playerRepository.save(player);
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
                : nightEngine.findAction(room, room.getCurrentNightRole()).orElse(null);

        Alignment nightActionResult = currentAction == null ? null : currentAction.getResultAlignment();
        boolean nightActionResultCursed = false;
        if (currentAction != null && nightActionResult != null && currentAction.getTargetPlayerId() != null
                && nightEngine.isCursedThisRound(room, currentAction.getTargetPlayerId())) {
            nightActionResult = flip(nightActionResult);
            nightActionResultCursed = true;
        }

        List<Long> lastNightVictimIds = nightEngine.findLastNightVictims(room);

        Long guardianBlockedPlayerId = room.getCurrentNightRole() == Role.GUARDIAN
                ? nightEngine.previousRoundGuardianTarget(room)
                : null;

        return MasterGameStateResponse.from(room, players, currentAction, lastNightVictimIds, nightActionResult,
                nightActionResultCursed, guardianBlockedPlayerId);
    }

    /**
     * How a cursed player appears to the priest — the opposite of what
     * their (now flipped-to-ghost/angel, but alignment-preserving) role
     * actually is.
     */
    private Alignment flip(Alignment alignment) {
        return alignment == Alignment.GOOD ? Alignment.EVIL : Alignment.GOOD;
    }

    private void broadcastGameUpdated(Room room) {
        AfterCommit.run(() -> messagingTemplate.convertAndSend("/topic/rooms/" + room.getCode() + "/game",
                new GameUpdatedMessage(room.getCode())));
    }
}
