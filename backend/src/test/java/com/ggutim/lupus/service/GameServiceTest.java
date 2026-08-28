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
import com.ggutim.lupus.dto.KillerGuessResponse;
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
        Room room = new Room(CODE, MASTER_TOKEN, GameMode.CLASSIC, 4, Map.of(Role.VILLAGER, 4), true, false);
        setId(room, nextId++);
        return room;
    }

    private Room startedRoom(GamePhase phase) {
        Room room = newRoom();
        room.start();
        room.setPhase(phase);
        return room;
    }

    private Room afterlifeStartedRoom(GamePhase phase) {
        Room room = new Room(CODE, MASTER_TOKEN, GameMode.AFTERLIFE, 4, Map.of(Role.VILLAGER, 4), true, false);
        setId(room, nextId++);
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

    @Test
    void startGame_skipsRoleAssignerWhenRolesWereAssignedManually() {
        Room room = new Room(CODE, MASTER_TOKEN, GameMode.CLASSIC, 1, Map.of(Role.VILLAGER, 1), false, true);
        setId(room, nextId++);
        List<Player> players = List.of(player(room, "P1", Role.VILLAGER, true));
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        gameService().startGame(room, players);

        verify(roleAssigner, never()).assign(any(), any());
        assertThat(room.getStatus()).isEqualTo(RoomStatus.STARTED);
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

    @Test
    void getGameState_flipsNightActionResultAndSetsCursedFlagWhenTargetIsCursed() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.PRIEST);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);

        NightAction priestAction = new NightAction(room, room.getRoundNumber(), Role.PRIEST);
        priestAction.setTargetPlayerId(42L);
        priestAction.setResultAlignment(Alignment.EVIL);
        when(nightEngine.findAction(room, Role.PRIEST)).thenReturn(Optional.of(priestAction));
        when(nightEngine.isCursedThisRound(room, 42L)).thenReturn(true);

        MasterGameStateResponse state = gameService().getGameState(CODE, MASTER_TOKEN);

        assertThat(state.nightActionResult()).isEqualTo(Alignment.GOOD);
        assertThat(state.nightActionResultCursed()).isTrue();
    }

    @Test
    void getGameState_doesNotFlipNightActionResultWhenTargetIsNotCursed() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.PRIEST);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);

        NightAction priestAction = new NightAction(room, room.getRoundNumber(), Role.PRIEST);
        priestAction.setTargetPlayerId(42L);
        priestAction.setResultAlignment(Alignment.EVIL);
        when(nightEngine.findAction(room, Role.PRIEST)).thenReturn(Optional.of(priestAction));
        when(nightEngine.isCursedThisRound(room, 42L)).thenReturn(false);

        MasterGameStateResponse state = gameService().getGameState(CODE, MASTER_TOKEN);

        assertThat(state.nightActionResult()).isEqualTo(Alignment.EVIL);
        assertThat(state.nightActionResultCursed()).isFalse();
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

    @Test
    void getVillageOverview_hidesEveryRoleByDefault() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        Player mayor = player(room, "MAYOR", Role.MAYOR, true);
        mayor.setMayor(true);
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(wolf, killer, mayor));

        VillageOverviewResponse overview = gameService().getVillageOverview(CODE);

        assertThat(overview.players()).extracting(PlayerResponse::revealedRole).containsOnlyNulls();
        assertThat(overview.players()).extracting(PlayerResponse::mayor).containsOnly(false);
    }

    @Test
    void getVillageOverview_showsKillerRoleOnceTheyHaveRevealed() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        killer.setKillerRevealUsed(true);
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(killer));

        VillageOverviewResponse overview = gameService().getVillageOverview(CODE);

        assertThat(overview.players().get(0).revealedRole()).isEqualTo(Role.KILLER);
    }

    @Test
    void getVillageOverview_showsMayorTagOnlyOnceRevealed() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player mayor = player(room, "MAYOR", Role.MAYOR, true);
        mayor.setMayor(true);
        mayor.setMayorRevealed(true);
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(mayor));

        VillageOverviewResponse overview = gameService().getVillageOverview(CODE);

        assertThat(overview.players().get(0).mayor()).isTrue();
        assertThat(overview.players().get(0).revealedRole()).isEqualTo(Role.MAYOR);
    }

    @Test
    void getVillageOverview_showsMayorTagOnCurrentHolderNotTheDeadOriginal() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player deadMayor = player(room, "MAYOR", Role.MAYOR, false);
        deadMayor.setMayor(false);
        deadMayor.setMayorRevealed(true);
        Player successor = player(room, "WOLF", Role.WEREWOLF, true);
        successor.setMayor(true);
        successor.setMayorRevealed(true);
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(deadMayor, successor));

        VillageOverviewResponse overview = gameService().getVillageOverview(CODE);

        assertThat(overview.players().get(0).mayor()).isFalse();
        assertThat(overview.players().get(1).mayor()).isTrue();
        assertThat(overview.players().get(1).revealedRole()).isNull();
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
        when(playerRepository.findByIdAndRoomId(target.getId(), room.getId())).thenReturn(Optional.of(target));

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

    // ---------- revealKillerAndGuess ----------

    @Test
    void revealKillerAndGuess_rejectsDuringNight() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, 1L, Role.VILLAGER))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealKillerAndGuess_rejectsWhenRoomHasNoKiller() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.VILLAGER))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealKillerAndGuess_rejectsWhenKillerIsDead() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, false);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));

        assertThatThrownBy(() -> gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.VILLAGER))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealKillerAndGuess_rejectsWhenAlreadyUsed() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        killer.setKillerRevealUsed(true);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));

        assertThatThrownBy(() -> gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.VILLAGER))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealKillerAndGuess_rejectsGuessingHimself() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));
        when(playerRepository.findByIdAndRoomId(killer.getId(), room.getId())).thenReturn(Optional.of(killer));

        assertThatThrownBy(() -> gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, killer.getId(), Role.KILLER))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealKillerAndGuess_correctGuessKillsTargetAndSparesKiller() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        Player target = player(room, "PRIEST", Role.PRIEST, true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));
        when(playerRepository.findByIdAndRoomId(target.getId(), room.getId())).thenReturn(Optional.of(target));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        KillerGuessResponse response =
                gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.PRIEST);

        assertThat(response.correct()).isTrue();
        assertThat(target.isAlive()).isFalse();
        assertThat(killer.isAlive()).isTrue();
        assertThat(killer.isKillerRevealUsed()).isTrue();
    }

    @Test
    void revealKillerAndGuess_correctGuessIgnoresSurvivorExtraLife() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        Player target = player(room, "SURV", Role.SURVIVOR, true);
        target.setExtraLives(1);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));
        when(playerRepository.findByIdAndRoomId(target.getId(), room.getId())).thenReturn(Optional.of(target));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.SURVIVOR);

        assertThat(target.isAlive()).isFalse();
        assertThat(target.getExtraLives()).isEqualTo(1);
    }

    @Test
    void revealKillerAndGuess_wrongGuessKillsKillerAndSparesTarget() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        Player target = player(room, "PRIEST", Role.PRIEST, true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));
        when(playerRepository.findByIdAndRoomId(target.getId(), room.getId())).thenReturn(Optional.of(target));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        KillerGuessResponse response =
                gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.GRAVEDIGGER);

        assertThat(response.correct()).isFalse();
        assertThat(killer.isAlive()).isFalse();
        assertThat(target.isAlive()).isTrue();
        assertThat(killer.isKillerRevealUsed()).isTrue();
    }

    @Test
    void revealKillerAndGuess_setsPendingMayorSuccessionWhenTargetWasMayor() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        Player target = player(room, "MAYOR", Role.MAYOR, true);
        target.setMayor(true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));
        when(playerRepository.findByIdAndRoomId(target.getId(), room.getId())).thenReturn(Optional.of(target));
        when(playerRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(killer));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        KillerGuessResponse response =
                gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.MAYOR);

        assertThat(response.gameState().pendingMayorSuccessionPlayerId()).isEqualTo(target.getId());
    }

    @Test
    void revealKillerAndGuess_doesNotSetPendingMayorSuccessionWhenGameEnds() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        Player target = player(room, "MAYOR", Role.MAYOR, true);
        target.setMayor(true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));
        when(playerRepository.findByIdAndRoomId(target.getId(), room.getId())).thenReturn(Optional.of(target));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.of(Alignment.GOOD));

        KillerGuessResponse response =
                gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.MAYOR);

        assertThat(response.gameState().pendingMayorSuccessionPlayerId()).isNull();
        verify(playerRepository, never()).findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId());
    }

    @Test
    void revealKillerAndGuess_endsGameWhenWinConditionMet() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player killer = player(room, "KILLER", Role.KILLER, true);
        Player target = player(room, "PRIEST", Role.PRIEST, true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndRole(room.getId(), Role.KILLER)).thenReturn(Optional.of(killer));
        when(playerRepository.findByIdAndRoomId(target.getId(), room.getId())).thenReturn(Optional.of(target));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.of(Alignment.EVIL));

        KillerGuessResponse response =
                gameService().revealKillerAndGuess(CODE, MASTER_TOKEN, target.getId(), Role.PRIEST);

        assertThat(response.gameState().phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(response.gameState().winner()).isEqualTo(Alignment.EVIL);
    }

    // ---------- revealMayor ----------

    @Test
    void revealMayor_rejectsDuringNight() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().revealMayor(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealMayor_rejectsWhenRoomHasNoMayor() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player villager = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().revealMayor(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealMayor_rejectsWhenMayorIsDead() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player mayor = player(room, "MAYOR", Role.MAYOR, false);
        mayor.setMayor(true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndMayorTrue(room.getId())).thenReturn(Optional.of(mayor));

        assertThatThrownBy(() -> gameService().revealMayor(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealMayor_rejectsWhenAlreadyRevealed() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player mayor = player(room, "MAYOR", Role.MAYOR, true);
        mayor.setMayor(true);
        mayor.setMayorRevealed(true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndMayorTrue(room.getId())).thenReturn(Optional.of(mayor));

        assertThatThrownBy(() -> gameService().revealMayor(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void revealMayor_marksMayorAsRevealed() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player mayor = player(room, "MAYOR", Role.MAYOR, true);
        mayor.setMayor(true);
        mockMasterRoom(room);
        when(playerRepository.findFirstByRoomIdAndMayorTrue(room.getId())).thenReturn(Optional.of(mayor));

        gameService().revealMayor(CODE, MASTER_TOKEN);

        assertThat(mayor.isMayorRevealed()).isTrue();
    }

    // ---------- assignMayorSuccessor ----------

    @Test
    void assignMayorSuccessor_rejectsWhenNoPendingSuccession() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().assignMayorSuccessor(CODE, MASTER_TOKEN, 1L))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void assignMayorSuccessor_rejectsDeadSuccessor() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player deadMayor = player(room, "MAYOR", Role.MAYOR, false);
        Player successor = player(room, "V1", Role.VILLAGER, false);
        room.setPendingMayorSuccessionPlayerId(deadMayor.getId());
        mockMasterRoom(room);
        when(playerRepository.findByIdAndRoomId(successor.getId(), room.getId())).thenReturn(Optional.of(successor));

        assertThatThrownBy(() -> gameService().assignMayorSuccessor(CODE, MASTER_TOKEN, successor.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void assignMayorSuccessor_rejectsNamingTheDeadMayorAsTheirOwnSuccessor() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player deadMayor = player(room, "MAYOR", Role.MAYOR, false);
        room.setPendingMayorSuccessionPlayerId(deadMayor.getId());
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().assignMayorSuccessor(CODE, MASTER_TOKEN, deadMayor.getId()))
                .isInstanceOf(InvalidGamePhaseException.class)
                .hasMessageContaining("cannot name themselves");
    }

    @Test
    void assignMayorSuccessor_transfersMayorStatusToSuccessorKeepingTheirOwnRole() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player deadMayor = player(room, "MAYOR", Role.MAYOR, false);
        deadMayor.setMayor(true);
        deadMayor.setMayorRevealed(true);
        Player successor = player(room, "WOLF", Role.WEREWOLF, true);
        room.setPendingMayorSuccessionPlayerId(deadMayor.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(deadMayor.getId())).thenReturn(Optional.of(deadMayor));
        when(playerRepository.findByIdAndRoomId(successor.getId(), room.getId())).thenReturn(Optional.of(successor));

        MasterGameStateResponse result = gameService().assignMayorSuccessor(CODE, MASTER_TOKEN, successor.getId());

        assertThat(deadMayor.isMayor()).isFalse();
        assertThat(successor.isMayor()).isTrue();
        assertThat(successor.isMayorRevealed()).isTrue();
        assertThat(successor.getRole()).isEqualTo(Role.WEREWOLF);
        assertThat(room.getPendingMayorSuccessionPlayerId()).isNull();
        assertThat(result.pendingMayorSuccessionPlayerId()).isNull();
    }

    @Test
    void assignMayorSuccessor_isImmediatelyPublicEvenIfTheOriginalMayorWasNeverRevealed() {
        Room room = startedRoom(GamePhase.DISCUSSION);
        Player deadMayor = player(room, "MAYOR", Role.MAYOR, false);
        deadMayor.setMayor(true);
        deadMayor.setMayorRevealed(false);
        Player successor = player(room, "V1", Role.VILLAGER, true);
        room.setPendingMayorSuccessionPlayerId(deadMayor.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(deadMayor.getId())).thenReturn(Optional.of(deadMayor));
        when(playerRepository.findByIdAndRoomId(successor.getId(), room.getId())).thenReturn(Optional.of(successor));

        gameService().assignMayorSuccessor(CODE, MASTER_TOKEN, successor.getId());

        assertThat(successor.isMayorRevealed()).isTrue();
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
    void advancePhase_appliesAfterlifeTransitionToNightKillVictimsInAfterlifeMode() {
        Room room = afterlifeStartedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        when(nightEngine.nextRole(room, Role.WEREWOLF)).thenReturn(Optional.empty());
        when(nightEngine.resolveDeferredKillsAndClearState(room)).thenReturn(List.of(victim.getId()));
        when(playerRepository.findById(victim.getId())).thenReturn(Optional.of(victim));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        gameService().advancePhase(CODE, MASTER_TOKEN);

        verify(roleAssigner).applyAfterlifeDeathTransition(room, victim);
    }

    @Test
    void advancePhase_doesNotApplyAfterlifeTransitionInClassicMode() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);
        when(nightEngine.nextRole(room, Role.WEREWOLF)).thenReturn(Optional.empty());
        when(nightEngine.resolveDeferredKillsAndClearState(room)).thenReturn(List.of(99L));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        gameService().advancePhase(CODE, MASTER_TOKEN);

        verify(roleAssigner, never()).applyAfterlifeDeathTransition(any(), any());
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

    @Test
    void advancePhase_rejectsWhenPendingMayorSuccession() {
        Room room = startedRoom(GamePhase.MORNING_REVEAL);
        room.setPendingMayorSuccessionPlayerId(5L);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().advancePhase(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void advancePhase_setsPendingMayorSuccessionWhenNightKillVictimWasMayor() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player mayor = player(room, "MAYOR", Role.MAYOR, true);
        mayor.setMayor(true);
        Player other = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        when(nightEngine.nextRole(room, Role.WEREWOLF)).thenReturn(Optional.empty());
        when(nightEngine.resolveDeferredKillsAndClearState(room)).thenReturn(List.of(mayor.getId()));
        when(playerRepository.findById(mayor.getId())).thenReturn(Optional.of(mayor));
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(other));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.pendingMayorSuccessionPlayerId()).isEqualTo(mayor.getId());
        assertThat(result.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
    }

    @Test
    void advancePhase_skipsMayorSuccessionWhenNoOtherPlayerIsAlive() {
        Room room = startedRoom(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player mayor = player(room, "MAYOR", Role.MAYOR, true);
        mayor.setMayor(true);
        mockMasterRoom(room);
        when(nightEngine.nextRole(room, Role.WEREWOLF)).thenReturn(Optional.empty());
        when(nightEngine.resolveDeferredKillsAndClearState(room)).thenReturn(List.of(mayor.getId()));
        when(playerRepository.findById(mayor.getId())).thenReturn(Optional.of(mayor));
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId())).thenReturn(List.of());
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.pendingMayorSuccessionPlayerId()).isNull();
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
    void advancePhase_appliesAfterlifeTransitionToVoteKillVictimInAfterlifeMode() {
        Room room = afterlifeStartedRoom(GamePhase.VOTE_SELECT_TARGET);
        Player voted = player(room, "WOLF", Role.WEREWOLF, true);
        room.setPendingVoteVictimId(voted.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(voted.getId())).thenReturn(Optional.of(voted));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        gameService().advancePhase(CODE, MASTER_TOKEN);

        verify(roleAssigner).applyAfterlifeDeathTransition(room, voted);
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
    void advancePhase_setsPendingMayorSuccessionWhenVoteVictimWasMayor() {
        Room room = startedRoom(GamePhase.VOTE_SELECT_TARGET);
        Player mayor = player(room, "MAYOR", Role.MAYOR, true);
        mayor.setMayor(true);
        Player other = player(room, "V1", Role.VILLAGER, true);
        room.setPendingVoteVictimId(mayor.getId());
        mockMasterRoom(room);
        when(playerRepository.findById(mayor.getId())).thenReturn(Optional.of(mayor));
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(other));
        when(winConditionEvaluator.evaluate(room)).thenReturn(Optional.empty());

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.pendingMayorSuccessionPlayerId()).isEqualTo(mayor.getId());
        assertThat(result.phase()).isEqualTo(GamePhase.NIGHT_START);
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
