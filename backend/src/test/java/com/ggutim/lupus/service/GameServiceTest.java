package com.ggutim.lupus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.dto.MasterGameStateResponse;
import com.ggutim.lupus.dto.PlayerResponse;
import com.ggutim.lupus.dto.VillageOverviewResponse;
import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
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
import com.ggutim.lupus.repository.NightActionRepository;
import com.ggutim.lupus.repository.PlayerRepository;
import com.ggutim.lupus.repository.RoomRepository;
import java.util.ArrayList;
import java.util.HashMap;
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

    private long nextId = 1;

    /**
     * Real {@link NightActionEffect}/{@link WinConditionCheck}
     * implementations (not mocks) so these tests exercise the actual
     * werewolf/priest wiring, backed by an in-memory fake repository
     * since {@code GameService} both writes and reads {@link
     * NightAction} rows within a single call.
     */
    private GameService gameService() {
        Map<String, NightAction> store = new HashMap<>();
        NightActionRepository nightActionRepository = mock(NightActionRepository.class);
        when(nightActionRepository.save(any())).thenAnswer(invocation -> {
            NightAction action = invocation.getArgument(0);
            store.put(nightActionKey(action.getRoom().getId(), action.getRoundNumber(), action.getRole()), action);
            return action;
        });
        when(nightActionRepository.findByRoomIdAndRoundNumberAndRole(any(), anyInt(), any())).thenAnswer(invocation ->
                Optional.ofNullable(store.get(nightActionKey(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)))));

        List<NightActionEffect> effects = List.of(new WerewolfKillEffect(), new PriestInspectEffect());
        List<WinConditionCheck> winConditions = List.of(new GoodEvilHeadcountWinCondition(playerRepository));

        return new GameService(roomRepository, playerRepository, nightActionRepository, roomService,
                messagingTemplate, effects, winConditions);
    }

    private String nightActionKey(Long roomId, int round, Role role) {
        return roomId + ":" + round + ":" + role;
    }

    private Room room(int playerCount, int werewolfCount, int priestCount) {
        int villagerCount = playerCount - werewolfCount - priestCount;
        Room room = new Room(CODE, MASTER_TOKEN, GameMode.CLASSIC, playerCount, Map.of(
                Role.WEREWOLF, werewolfCount, Role.PRIEST, priestCount, Role.VILLAGER, villagerCount));
        setId(room, nextId++);
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

    private void mockPersistence(Room room, List<Player> players) {
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())).thenReturn(players);
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()))
                .thenAnswer(invocation -> players.stream().filter(Player::isAlive).toList());
        for (Player player : players) {
            when(playerRepository.findById(player.getId())).thenReturn(Optional.of(player));
        }
    }

    // ---------- startGame / role assignment ----------

    @Test
    void startGame_assignsConfiguredRoleCountsAndEntersFirstPhase() {
        Room room = room(6, 2, 1);
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Player player = new Player(room, "P" + i, "token" + i);
            setId(player, nextId++);
            players.add(player);
        }
        mockPersistence(room, players);

        gameService().startGame(room, players);

        assertThat(room.getStatus()).isEqualTo(RoomStatus.STARTED);
        assertThat(room.getPhase()).isEqualTo(GamePhase.ROLES_ASSIGNED);
        assertThat(room.getRoundNumber()).isEqualTo(1);

        Map<Role, Long> counts = players.stream()
                .collect(java.util.stream.Collectors.groupingBy(Player::getRole, java.util.stream.Collectors.counting()));
        assertThat(counts.getOrDefault(Role.WEREWOLF, 0L)).isEqualTo(2);
        assertThat(counts.getOrDefault(Role.PRIEST, 0L)).isEqualTo(1);
        assertThat(counts.getOrDefault(Role.VILLAGER, 0L)).isEqualTo(3);
    }

    // ---------- getGameState ----------

    @Test
    void getGameState_rejectsWhenGameNotStarted() {
        Room room = room(6, 1, 0);
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
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.ROLES_ASSIGNED);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player villager = player(room, "VILL", Role.VILLAGER, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(wolf, villager));

        MasterGameStateResponse state = gameService().getGameState(CODE, MASTER_TOKEN);

        assertThat(state.phase()).isEqualTo(GamePhase.ROLES_ASSIGNED);
        assertThat(state.players()).hasSize(2);
        assertThat(state.players().get(0).role()).isEqualTo(Role.WEREWOLF);
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
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.MORNING_REVEAL);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player victim = player(room, "VILL", Role.VILLAGER, false);
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.of(room));
        mockPersistence(room, List.of(wolf, victim));

        VillageOverviewResponse overview = gameService().getVillageOverview(CODE);

        assertThat(overview.players()).extracting(PlayerResponse::alive).containsExactly(true, true);
    }

    @Test
    void getVillageOverview_revealsDeathOnceDiscussionStarts() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.DISCUSSION);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player victim = player(room, "VILL", Role.VILLAGER, false);
        when(roomRepository.findByCode(CODE)).thenReturn(Optional.of(room));
        mockPersistence(room, List.of(wolf, victim));

        VillageOverviewResponse overview = gameService().getVillageOverview(CODE);

        assertThat(overview.players()).extracting(PlayerResponse::alive).containsExactly(true, false);
    }

    // ---------- advancePhase: narration chain ----------

    @Test
    void advancePhase_movesThroughNarrationPhasesInOrder() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.ROLES_ASSIGNED);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        List<Player> players = List.of(wolf, priest, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

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
    void advancePhase_rejectsAdvancingPastNightSelectionWithoutSelectionWhenRoleAlive() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(wolf));

        assertThatThrownBy(() -> gameService().advancePhase(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void advancePhase_movesToNextConfiguredRoleAfterWerewolvesSelect() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        List<Player> players = List.of(wolf, priest, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

        GameService gameService = gameService();
        gameService.selectNightTarget(CODE, MASTER_TOKEN, v1.getId());
        MasterGameStateResponse result = gameService.advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.NIGHT_ACTIONS);
        assertThat(result.currentNightRole()).isEqualTo(Role.PRIEST);
        assertThat(result.currentNightStepKind()).isEqualTo(NightStepKind.WAKE_UP);
    }

    @Test
    void advancePhase_goesStraightToMorningRevealWhenNoOtherRoleConfigured() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        Player v3 = player(room, "V3", Role.VILLAGER, true);
        List<Player> players = List.of(wolf, v1, v2, v3);
        mockMasterRoom(room);
        mockPersistence(room, players);

        GameService gameService = gameService();
        gameService.selectNightTarget(CODE, MASTER_TOKEN, v1.getId());
        MasterGameStateResponse result = gameService.advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
        assertThat(v1.isAlive()).isFalse();
        assertThat(result.lastNightVictimId()).isEqualTo(v1.getId());
    }

    @Test
    void advancePhase_stillNarratesPriestTurnEvenWhenPriestIsTonightsVictim() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        List<Player> players = List.of(wolf, priest, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

        GameService gameService = gameService();
        gameService.selectNightTarget(CODE, MASTER_TOKEN, priest.getId());
        MasterGameStateResponse afterWerewolves = gameService.advancePhase(CODE, MASTER_TOKEN);

        assertThat(afterWerewolves.phase()).isEqualTo(GamePhase.NIGHT_ACTIONS);
        assertThat(afterWerewolves.currentNightRole()).isEqualTo(Role.PRIEST);
        assertThat(afterWerewolves.currentNightStepKind()).isEqualTo(NightStepKind.WAKE_UP);

        MasterGameStateResponse afterPriestWakeUp = gameService.advancePhase(CODE, MASTER_TOKEN);
        assertThat(afterPriestWakeUp.currentNightStepKind()).isEqualTo(NightStepKind.SELECT);

        // no selection is made for the priest — they're already this round's victim — advancing must not throw
        MasterGameStateResponse afterPriestSelect = gameService.advancePhase(CODE, MASTER_TOKEN);
        assertThat(afterPriestSelect.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
        assertThat(priest.isAlive()).isFalse();
    }

    @Test
    void advancePhase_stillNarratesRoleWhoseHolderDiedInAnEarlierRound() {
        Room room = room(5, 1, 1);
        room.start();
        room.setPhase(GamePhase.NIGHT_START);
        room.setRoundNumber(2);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player deadPriest = player(room, "PRIEST", Role.PRIEST, false);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        Player v3 = player(room, "V3", Role.VILLAGER, true);
        List<Player> players = List.of(wolf, deadPriest, v1, v2, v3);
        mockMasterRoom(room);
        mockPersistence(room, players);

        GameService gameService = gameService();
        gameService.advancePhase(CODE, MASTER_TOKEN); // NIGHT_START -> NIGHT_ACTIONS, WEREWOLF, WAKE_UP
        gameService.advancePhase(CODE, MASTER_TOKEN); // WAKE_UP -> SELECT
        gameService.selectNightTarget(CODE, MASTER_TOKEN, v1.getId());
        MasterGameStateResponse afterWerewolves = gameService.advancePhase(CODE, MASTER_TOKEN);

        // the priest died last round, but their turn is still narrated this round (fixes #19)
        assertThat(afterWerewolves.currentNightRole()).isEqualTo(Role.PRIEST);
        assertThat(afterWerewolves.currentNightStepKind()).isEqualTo(NightStepKind.WAKE_UP);

        MasterGameStateResponse afterPriestWakeUp = gameService.advancePhase(CODE, MASTER_TOKEN);
        assertThat(afterPriestWakeUp.currentNightStepKind()).isEqualTo(NightStepKind.SELECT);

        MasterGameStateResponse afterPriestSelect = gameService.advancePhase(CODE, MASTER_TOKEN);
        assertThat(afterPriestSelect.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
    }

    @Test
    void advancePhase_rejectsAdvancingPastPriestSelectionWithoutSelectionWhenPriestAlive() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.PRIEST);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        List<Player> players = List.of(wolf, priest);
        mockMasterRoom(room);
        mockPersistence(room, players);

        assertThatThrownBy(() -> gameService().advancePhase(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void advancePhase_fromPriestSelectionGoesToMorningRevealWhenNoMoreRoles() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.PRIEST);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        List<Player> players = List.of(wolf, priest, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

        GameService gameService = gameService();
        gameService.selectNightTarget(CODE, MASTER_TOKEN, v1.getId());
        MasterGameStateResponse result = gameService.advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
    }

    @Test
    void advancePhase_discussionToVoteSelection() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.DISCUSSION);
        mockMasterRoom(room);
        mockPersistence(room, List.of());

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.VOTE_SELECT_TARGET);
    }

    @Test
    void advancePhase_rejectsWhenGameAlreadyOver() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.GAME_OVER);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().advancePhase(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    // ---------- selectNightTarget ----------

    @Test
    void selectNightTarget_rejectsWhenNotInNightActionsPhase() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_START);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().selectNightTarget(CODE, MASTER_TOKEN, 1L))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void selectNightTarget_rejectsWhenStillOnWakeUpBeat() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.WAKE_UP);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().selectNightTarget(CODE, MASTER_TOKEN, 1L))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void selectNightTarget_rejectsDeadTarget() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockMasterRoom(room);
        when(playerRepository.findById(deadVillager.getId())).thenReturn(Optional.of(deadVillager));

        assertThatThrownBy(() -> gameService().selectNightTarget(CODE, MASTER_TOKEN, deadVillager.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void selectNightTarget_rejectsAnotherWerewolfAsWerewolfTarget() {
        Room room = room(4, 2, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player otherWolf = player(room, "WOLF2", Role.WEREWOLF, true);
        mockMasterRoom(room);
        when(playerRepository.findById(otherWolf.getId())).thenReturn(Optional.of(otherWolf));

        assertThatThrownBy(() -> gameService().selectNightTarget(CODE, MASTER_TOKEN, otherWolf.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void selectNightTarget_rejectsUnknownPlayer() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        mockMasterRoom(room);
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService().selectNightTarget(CODE, MASTER_TOKEN, 999L))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void selectNightTarget_storesPendingWerewolfVictim() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(target));

        MasterGameStateResponse result = gameService().selectNightTarget(CODE, MASTER_TOKEN, target.getId());

        assertThat(result.pendingNightActionTargetId()).isEqualTo(target.getId());
    }

    @Test
    void selectNightTarget_priestRevealsGoodAlignmentForVillager() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.PRIEST);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(target));

        MasterGameStateResponse result = gameService().selectNightTarget(CODE, MASTER_TOKEN, target.getId());

        assertThat(result.nightActionResult()).isEqualTo(Alignment.GOOD);
        assertThat(result.pendingNightActionTargetId()).isEqualTo(target.getId());
    }

    @Test
    void selectNightTarget_priestRevealsEvilAlignmentForWerewolf() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.PRIEST);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player target = player(room, "WOLF", Role.WEREWOLF, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(target));

        MasterGameStateResponse result = gameService().selectNightTarget(CODE, MASTER_TOKEN, target.getId());

        assertThat(result.nightActionResult()).isEqualTo(Alignment.EVIL);
    }

    // ---------- selectVoteVictim ----------

    @Test
    void selectVoteVictim_allowsNoOneVotedOut() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.VOTE_SELECT_TARGET);
        mockMasterRoom(room);
        mockPersistence(room, List.of());

        MasterGameStateResponse result = gameService().selectVoteVictim(CODE, MASTER_TOKEN, null);

        assertThat(result.pendingVoteVictimId()).isNull();
    }

    @Test
    void selectVoteVictim_storesPendingVictim() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.VOTE_SELECT_TARGET);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(target));

        MasterGameStateResponse result = gameService().selectVoteVictim(CODE, MASTER_TOKEN, target.getId());

        assertThat(result.pendingVoteVictimId()).isEqualTo(target.getId());
    }

    @Test
    void selectVoteVictim_rejectsWhenGameOver() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.GAME_OVER);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().selectVoteVictim(CODE, MASTER_TOKEN, 1L))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    // ---------- win conditions ----------

    @Test
    void advancePhase_goodWinsWhenLastWerewolfKilledByVote() {
        Room room = room(3, 1, 0);
        room.start();
        room.setPhase(GamePhase.VOTE_SELECT_TARGET);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        room.setPendingVoteVictimId(wolf.getId());
        List<Player> players = List.of(wolf, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(result.winner()).isEqualTo(Alignment.GOOD);
        assertThat(wolf.isAlive()).isFalse();
    }

    @Test
    void advancePhase_evilWinsWhenWerewolvesEqualVillagersAfterVote() {
        Room room = room(4, 2, 0);
        room.start();
        room.setPhase(GamePhase.VOTE_SELECT_TARGET);
        Player wolf1 = player(room, "WOLF1", Role.WEREWOLF, true);
        Player wolf2 = player(room, "WOLF2", Role.WEREWOLF, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        room.setPendingVoteVictimId(v1.getId());
        List<Player> players = List.of(wolf1, wolf2, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(result.winner()).isEqualTo(Alignment.EVIL);
    }

    @Test
    void advancePhase_continuesToNextRoundWhenNoWinnerAfterVote() {
        Room room = room(5, 1, 0);
        room.start();
        room.setPhase(GamePhase.VOTE_SELECT_TARGET);
        room.setRoundNumber(1);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        Player v3 = player(room, "V3", Role.VILLAGER, true);
        room.setPendingVoteVictimId(v1.getId());
        List<Player> players = List.of(wolf, v1, v2, v3);
        mockMasterRoom(room);
        mockPersistence(room, players);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.NIGHT_START);
        assertThat(result.roundNumber()).isEqualTo(2);
        assertThat(v1.isAlive()).isFalse();
    }

    @Test
    void advancePhase_endsGameImmediatelyWhenNightKillDecidesIt() {
        Room room = room(2, 1, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_ACTIONS);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player lastVillager = player(room, "V1", Role.VILLAGER, true);
        List<Player> players = List.of(wolf, lastVillager);
        mockMasterRoom(room);
        mockPersistence(room, players);

        GameService gameService = gameService();
        gameService.selectNightTarget(CODE, MASTER_TOKEN, lastVillager.getId());
        MasterGameStateResponse result = gameService.advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(result.winner()).isEqualTo(Alignment.EVIL);
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
}
