package com.ggutim.lupus.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.room.dto.MasterGameStateResponse;
import com.ggutim.lupus.room.exception.InvalidGamePhaseException;
import com.ggutim.lupus.room.exception.MasterTokenMismatchException;
import com.ggutim.lupus.room.exception.PlayerNotFoundException;
import java.util.ArrayList;
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

    private GameService gameService() {
        return new GameService(roomRepository, playerRepository, roomService, messagingTemplate);
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

    // ---------- advancePhase: basic narration chain ----------

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
        assertThat(gameService.advancePhase(CODE, MASTER_TOKEN).phase()).isEqualTo(GamePhase.WEREWOLVES_WAKE_UP);
        assertThat(gameService.advancePhase(CODE, MASTER_TOKEN).phase())
                .isEqualTo(GamePhase.WEREWOLVES_SELECT_VICTIM);
    }

    @Test
    void advancePhase_rejectsAdvancingPastWerewolfSelectionWithoutSelection() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.WEREWOLVES_SELECT_VICTIM);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().advancePhase(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void advancePhase_goesToPriestWakeUpWhenPriestAliveAndNotTargeted() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.WEREWOLVES_SELECT_VICTIM);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        room.setPendingWerewolfVictimId(v1.getId());
        List<Player> players = List.of(wolf, priest, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.PRIEST_WAKE_UP);
    }

    @Test
    void advancePhase_skipsPriestPhaseWhenNoPriestConfigured() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.WEREWOLVES_SELECT_VICTIM);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        Player v3 = player(room, "V3", Role.VILLAGER, true);
        room.setPendingWerewolfVictimId(v1.getId());
        List<Player> players = List.of(wolf, v1, v2, v3);
        mockMasterRoom(room);
        mockPersistence(room, players);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
        assertThat(v1.isAlive()).isFalse();
        assertThat(result.lastNightVictimId()).isEqualTo(v1.getId());
    }

    @Test
    void advancePhase_skipsPriestPhaseWhenPriestIsTheVictim() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.WEREWOLVES_SELECT_VICTIM);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        room.setPendingWerewolfVictimId(priest.getId());
        List<Player> players = List.of(wolf, priest, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.MORNING_REVEAL);
        assertThat(priest.isAlive()).isFalse();
    }

    @Test
    void advancePhase_rejectsAdvancingPastPriestSelectionWithoutSelection() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.PRIEST_SELECT_TARGET);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().advancePhase(CODE, MASTER_TOKEN))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void advancePhase_fromPriestSelectionGoesToMorningReveal() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.PRIEST_SELECT_TARGET);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        room.setPendingPriestTargetId(v1.getId());
        List<Player> players = List.of(wolf, priest, v1, v2);
        mockMasterRoom(room);
        mockPersistence(room, players);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

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

    // ---------- selections ----------

    @Test
    void selectWerewolfVictim_rejectsWhenNotInCorrectPhase() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.NIGHT_START);
        mockMasterRoom(room);

        assertThatThrownBy(() -> gameService().selectWerewolfVictim(CODE, MASTER_TOKEN, 1L))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void selectWerewolfVictim_rejectsDeadTarget() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.WEREWOLVES_SELECT_VICTIM);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockMasterRoom(room);
        when(playerRepository.findById(deadVillager.getId())).thenReturn(Optional.of(deadVillager));

        assertThatThrownBy(() -> gameService().selectWerewolfVictim(CODE, MASTER_TOKEN, deadVillager.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void selectWerewolfVictim_rejectsUnknownPlayer() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.WEREWOLVES_SELECT_VICTIM);
        mockMasterRoom(room);
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService().selectWerewolfVictim(CODE, MASTER_TOKEN, 999L))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void selectWerewolfVictim_storesPendingVictim() {
        Room room = room(4, 1, 0);
        room.start();
        room.setPhase(GamePhase.WEREWOLVES_SELECT_VICTIM);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(target));

        MasterGameStateResponse result = gameService().selectWerewolfVictim(CODE, MASTER_TOKEN, target.getId());

        assertThat(result.pendingWerewolfVictimId()).isEqualTo(target.getId());
    }

    @Test
    void selectPriestTarget_revealsGoodAlignmentForVillager() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.PRIEST_SELECT_TARGET);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(target));

        MasterGameStateResponse result = gameService().selectPriestTarget(CODE, MASTER_TOKEN, target.getId());

        assertThat(result.priestCheckResult()).isEqualTo(Alignment.GOOD);
        assertThat(result.pendingPriestTargetId()).isEqualTo(target.getId());
    }

    @Test
    void selectPriestTarget_revealsEvilAlignmentForWerewolf() {
        Room room = room(4, 1, 1);
        room.start();
        room.setPhase(GamePhase.PRIEST_SELECT_TARGET);
        Player target = player(room, "WOLF", Role.WEREWOLF, true);
        mockMasterRoom(room);
        mockPersistence(room, List.of(target));

        MasterGameStateResponse result = gameService().selectPriestTarget(CODE, MASTER_TOKEN, target.getId());

        assertThat(result.priestCheckResult()).isEqualTo(Alignment.EVIL);
    }

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
        room.setPhase(GamePhase.WEREWOLVES_SELECT_VICTIM);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player lastVillager = player(room, "V1", Role.VILLAGER, true);
        room.setPendingWerewolfVictimId(lastVillager.getId());
        List<Player> players = List.of(wolf, lastVillager);
        mockMasterRoom(room);
        mockPersistence(room, players);

        MasterGameStateResponse result = gameService().advancePhase(CODE, MASTER_TOKEN);

        assertThat(result.phase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(result.winner()).isEqualTo(Alignment.EVIL);
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
