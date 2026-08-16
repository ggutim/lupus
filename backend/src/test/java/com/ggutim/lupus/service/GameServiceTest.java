package com.ggutim.lupus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.dto.GameUpdatedMessage;
import com.ggutim.lupus.dto.MasterGameStateResponse;
import com.ggutim.lupus.dto.PlayerResponse;
import com.ggutim.lupus.dto.VillageOverviewResponse;
import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Unit tests for GameService's own orchestration — phase transitions,
 * delegation to its collaborators, and DTO assembly — with {@link
 * NightEngine}, {@link RoleAssigner}, {@link WinConditionEvaluator} and
 * {@link SoloWinEvaluator} mocked out, same as {@code PlayerServiceTest}
 * already mocks {@link GameService} itself. Their own internals (real
 * per-role wiring, win math) are covered by their dedicated test classes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServiceTest {

    private static final String MASTER_TOKEN = "secret-token";
    private static final String CODE = "ABCD";

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private RoomService roomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RoleAssigner roleAssigner;

    @Mock
    private NightEngine nightEngine;

    @Mock
    private WinConditionEvaluator winConditionEvaluator;

    @Mock
    private SoloWinEvaluator soloWinEvaluator;

    private long nextId = 1;

    private GameService gameService() {
        return new GameService(roomRepository, playerRepository, roomService, messagingTemplate,
                roleAssigner, nightEngine, winConditionEvaluator, soloWinEvaluator);
    }

    private Room newRoom() {
        Room room = new Room(CODE, MASTER_TOKEN, GameMode.CLASSIC, 4, Map.of(Role.VILLAGER, 4));
        setId(room, nextId++);
        return room;
    }

    private Room startedRoom(GamePhase phase) {
        Room room = newRoom();
        room.start();
        room.setPhase(phase);
        return room;
    }

    private Player player(Room room, String nickname, Role role, boolean alive) {
        Player player = new Player(room, nickname, nickname.toLowerCase() + "-token");
        setId(player, nextId++);
        player.setRole(role);
        if (!alive) {
            player.kill();
        }
        return player;
    }

    private void mockMasterRoom(Room room) {
        when(roomService.findRoomForMaster(CODE, MASTER_TOKEN)).thenReturn(room);
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- startGame ----------

    @Test
    void startGame_delegatesToRoleAssignerAndEntersFirstPhase() {
        Room room = newRoom();
        List<Player> players = List.of(player(room, "P1", Role.VILLAGER, true));
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        gameService().startGame(room, players);

        verify(roleAssigner).assign(room, players);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.STARTED);
        assertThat(room.getPhase()).isEqualTo(GamePhase.ROLES_ASSIGNED);
        assertThat(room.getRoundNumber()).isEqualTo(1);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/" + CODE + "/game"), any(GameUpdatedMessage.class));
    }

    // ---------- getGameState ----------

    @Test
    void getGameState_rejectsWhenGameNotStarted() {
        Room room = newRoom();
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().getGameState(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void getGameState_rejectsWrongMasterToken() {
        when(roomService.findRoomForMaster(CODE, "wrong")).thenThrow(new MasterTokenMismatchException(CODE));

        assertThatThrownBy(() -> gameService().getGameState(CODE, "wrong"))
                .isInstanceOf(MasterTokenMismatchException.class);
    }

    @Test
    void getGameState_returnsPlayersWithRoles() {
        Room room = startedRoom(GamePhase.ROLES_ASSIGNED);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player villager = player(room, "VILL", Role.VILLAGER, true);
        mockMasterRoom(room);
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(wolf, villager));

        MasterGameStateResponse state = gameService().getGameState(CODE, MASTER_TOKEN);

        assertThat(state.phase()).isEqualTo(GamePhase.ROLES_ASSIGNED);
        assertThat(state.players()).hasSize(2);
        assertThat(state.players().get(0).role()).isEqualTo(Role.WEREWOLF);
    }

    @Test
    void getGameState_includesNightActionResultAndLastVictimsFromNightEngine() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.PRIEST);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);

        NightAction priestAction = new NightAction(room, room.getRoundNumber(), Role.PRIEST);
        priestAction.setTargetPlayerId(42L);
        priestAction.setResultAlignment(Alignment.EVIL);
        when(nightEngine.findAction(room, Role.PRIEST)).thenReturn(Optional.of(priestAction));

        when(nightEngine.findLastNightVictims(room)).thenReturn(List.of(7L, 8L));

        MasterGameStateResponse state = gameService().getGameState(CODE, MASTER_TOKEN);

        assertThat(state.pendingNightActionTargetId()).isEqualTo(42L);
        assertThat(state.nightActionResult()).isEqualTo(Alignment.EVIL);
        assertThat(state.lastNightVictimIds()).containsExactly(7L, 8L);
    }

    // ---------- getVillageOverview ----------

    @Test
    void getVillageOverview_rejectsUnknownCode() {
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService().getVillageOverview(CODE))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void getVillageOverview_hidesOvernightDeathDuringMorningReveal() {
        Room room = startedRoom(GamePhase.MORNING_REVEAL);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player victim = player(room, "VILL", Role.VILLAGER, false);
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(wolf, victim));

        VillageOverviewResponse overview = gameService().getVillageOverview(CODE);

        assertThat(overview.players()).extracting(PlayerResponse::alive).containsExactly(true, true);
    }

    @Test
    void getVillageOverview_revealsDeathOnceDiscussionStarts() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player victim = player(room, "VILL", Role.VILLAGER, false);
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(wolf, victim));

        VillageOverviewResponse overview = gameService().getVillageOverview(CODE);

        assertThat(overview.players()).extracting(PlayerResponse::alive).containsExactly(true, false);
    }

    // ---------- selectNightTarget ----------

    @Test
    void selectNightTarget_rejectsWhenNotInNightSelectStep() {
        Room room = startedRoom(GamePhase.NIGHT_START);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().selectNightTarget(CODE, MASTER_TOKEN, 1L))
                .isInstanceOf(InvalidGamePhaseException.class);
        verifyNoInteractions(nightEngine);
    }

    @Test
    void selectNightTarget_delegatesToNightEngineAndReturnsUpdatedState() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);

        MasterGameStateResponse result = gameService().selectNightTarget(CODE, MASTER_TOKEN, 42L);

        verify(nightEngine).recordSelection(room, Role.WEREWOLF, 42L);
        assertThat(result.phase()).isEqualTo(GamePhase.NIGHT_ACTIONS);
    }

    // ---------- selectVoteVictim ----------

    @Test
    void selectVoteVictim_allowsNoOneVotedOut() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        mockMasterRoom(room);

        MasterGameStateResponse result = gameService().selectVoteVictim(CODE, MASTER_TOKEN, null);

        assertThat(result.pendingVoteVictimId()).isNull();
    }

    @Test
    void selectVoteVictim_storesPendingVictim() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        when(playerRepository.findById(target.getId())).thenReturn(Optional.of(target));

        MasterGameStateResponse result = gameService().selectVoteVictim(CODE, MASTER_TOKEN, target.getId());

        assertThat(result.pendingVoteVictimId()).isEqualTo(target.getId());
    }

    @Test
    void selectVoteVictim_rejectsWhenGameOver() {
        Room room = startedRoom(GamePhase.GAME_OVER);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().selectVoteVictim(CODE, MASTER_TOKEN, 1L))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    // ---------- advancePhase: skeleton phase sequencing ----------

    @Test
    void advancePhase_movesThroughSkeletonPhasesInOrder() {
        Room room = startedRoom(GamePhase.ROLES_ASSIGNED);
        mockMasterRoom(room);
        when(nightEngine.nextRole(eq(room), isNull())).thenReturn(Optional.of(Role.WEREWOLF));

        GameService gameService = gameService();

        assertThat(gameService.advancePhase(CODE, MASTER_TOKEN).phase()).isEqualTo(GamePhase.NIGHT_START);

        MasterGameStateResponse afterNightStart = gameService.advancePhase(CODE, MASTER_TOKEN);
        assertThat(afterNightStart.phase()).isEqualTo(GamePhase.NIGHT_ACTIONS);
        assertThat(afterNightStart.currentNightRole()).isEqualTo(Role.WEREWOLF);
        assertThat(afterNightStart.currentNightStepKind()).isEqualTo(NightStepKind.WAKE_UP);

        MasterGameStateResponse afterWakeUp = gameService.advancePhase(CODE, MASTER_TOKEN);
        assertThat(afterWakeUp.currentNightRole()).isEqualTo(Role.WEREWOLF);
        assertThat(afterWakeUp.currentNightStepKind()).isEqualTo(NightStepKind.SELECT);
    }

    @Test
    void advancePhase_movesToRoleReturnedByNightEngineAfterSelection() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);
        when(nightEngine.nextRole(room, Role.WEREWOLF)).thenReturn(Optional.of(Role.PRIEST));

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        verify(nightEngine).requireSelectionIfNeeded(room, Role.WEREWOLF);
        assertThat(result.phase()).isEqualTo(GamePhase.NIGHT_ACTIONS);
        assertThat(result.currentNightRole()).isEqualTo(Role.PRIEST);
        assertThat(result.currentNightStepKind()).isEqualTo(NightStepKind.WAKE_UP);
    }

    @Test
    void advancePhase_propagatesRequireSelectionFailureFromNightEngine() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);
        doThrow(new InvalidGamePhaseException("select first"))
                .when(nightEngine).requireSelectionIfNeeded(room, Role.WEREWOLF);

        assertThatThrownBy(() -> gameService().advancePhase(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void advancePhase_resolvesNightAndEntersMorningRevealWhenNoMoreRoles() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.PRIEST);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);
        when(nightEngine.nextRole(room, Role.PRIEST)).thenReturn(Optional.empty());
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        verify(nightEngine).resolveDeferredKillsAndClearState(room);
        assertThat(result.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
    }

    @Test
    void advancePhase_endsGameWhenNightResolutionDecidesAWinner() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);
        when(nightEngine.nextRole(room, Role.WEREWOLF)).thenReturn(Optional.empty());
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.of(Alignment.EVIL));

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(result.winner()).isEqualTo(Alignment.EVIL);
    }

    @Test
    void advancePhase_endsGameWithSoloWinnerWhenNightResolutionProducesOne() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);
        when(nightEngine.nextRole(room, Role.WEREWOLF)).thenReturn(Optional.empty());
        when(nightEngine.resolveDeferredKillsAndClearState(room)).thenReturn(List.of(99L));
        when(soloWinEvaluator.evaluate(room, new RoundEvent(RoundEvent.Cause.NIGHT_KILL, List.of(99L))))
                .thenReturn(Optional.of(Role.IDIOT));

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(result.winningRole()).isEqualTo(Role.IDIOT);
        assertThat(result.winner()).isNull();
        verify(winConditionEvaluator, never()).evaluate(any());
    }

    @Test
    void advancePhase_beginsNightDirectlyToMorningRevealWhenNoRolesConfigured() {
        Room room = startedRoom(GamePhase.NIGHT_START);
        mockMasterRoom(room);
        when(nightEngine.nextRole(eq(room), isNull())).thenReturn(Optional.empty());
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
    }

    @Test
    void advancePhase_discussionToVoteSelection() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        mockMasterRoom(room);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.VOTE_SELECT_TARGET);
    }

    @Test
    void advancePhase_rejectsWhenGameAlreadyOver() {
        Room room = startedRoom(GamePhase.GAME_OVER);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().advancePhase(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    // ---------- advancePhase: vote resolution ----------

    @Test
    void advancePhase_endsGameWhenVoteResolutionDecidesAWinner() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        Player voted = player(room, "WOLF", Role.WEREWOLF, true);
        room.setPendingVoteVictimId(voted.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(voted.getId())).thenReturn(Optional.of(voted));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.of(Alignment.GOOD));

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(result.winner()).isEqualTo(Alignment.GOOD);
        assertThat(voted.isAlive()).isFalse();
    }

    @Test
    void advancePhase_endsGameWithSoloWinnerWhenVoteResolutionProducesOne() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        Player voted = player(room, "IDIOT", Role.IDIOT, true);
        room.setPendingVoteVictimId(voted.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(voted.getId())).thenReturn(Optional.of(voted));
        when(soloWinEvaluator.evaluate(room, new RoundEvent(RoundEvent.Cause.VOTE_KILL, List.of(voted.getId()))))
                .thenReturn(Optional.of(Role.IDIOT));

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(result.winningRole()).isEqualTo(Role.IDIOT);
        assertThat(voted.isAlive()).isFalse();
    }

    @Test
    void advancePhase_soloWinTakesPriorityOverFactionWinAndSkipsTheFactionCheck() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        Player voted = player(room, "IDIOT", Role.IDIOT, true);
        room.setPendingVoteVictimId(voted.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(voted.getId())).thenReturn(Optional.of(voted));
        when(soloWinEvaluator.evaluate(eq(room), any())).thenReturn(Optional.of(Role.IDIOT));

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.winningRole()).isEqualTo(Role.IDIOT);
        assertThat(result.winner()).isNull();
        verify(winConditionEvaluator, never()).evaluate(any());
    }

    @Test
    void advancePhase_continuesToNextRoundWhenNoWinnerAfterVote() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        room.setRoundNumber(1);
        Player voted = player(room, "V1", Role.VILLAGER, true);
        room.setPendingVoteVictimId(voted.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(voted.getId())).thenReturn(Optional.of(voted));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.NIGHT_START);
        assertThat(result.roundNumber()).isEqualTo(2);
        assertThat(voted.isAlive()).isFalse();
    }

    @Test
    void advancePhase_allowsVoteResolutionWithNoOneVotedOut() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        mockMasterRoom(room);
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.NIGHT_START);
    }

    @Test
    void advancePhase_setsNoOneVotedOutPreviousDayWhenNoOneWasVotedOut() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        mockMasterRoom(room);
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(room.isNoOneVotedOutPreviousDay()).isTrue();
    }

    @Test
    void advancePhase_clearsNoOneVotedOutPreviousDayWhenSomeoneWasVotedOut() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        room.setNoOneVotedOutPreviousDay(true);
        Player voted = player(room, "V1", Role.VILLAGER, true);
        room.setPendingVoteVictimId(voted.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(voted.getId())).thenReturn(Optional.of(voted));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(room.isNoOneVotedOutPreviousDay()).isFalse();
    }
}
