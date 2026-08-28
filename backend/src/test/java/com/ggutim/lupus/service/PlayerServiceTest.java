package com.ggutim.lupus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.config.GameRules;
import com.ggutim.lupus.dto.JoinRoomResponse;
import com.ggutim.lupus.dto.ManualPlayerResponse;
import com.ggutim.lupus.dto.PlayerRoleResponse;
import com.ggutim.lupus.dto.RoomStateMessage;
import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
import com.ggutim.lupus.exception.NicknameTakenException;
import com.ggutim.lupus.exception.NotEnoughPlayersException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.exception.PlayerTokenMismatchException;
import com.ggutim.lupus.exception.RoomAlreadyStartedException;
import com.ggutim.lupus.exception.RoomFullException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.GameMode;
import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.model.RoomStatus;
import com.ggutim.lupus.repository.PlayerRepository;
import com.ggutim.lupus.repository.RoomRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    private static final String MASTER_TOKEN = "secret-token";

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private RoomService roomService;

    @Mock
    private GameService gameService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RoleAssigner roleAssigner;

    private final GameRules gameRules = new GameRules();

    private PlayerService playerService() {
        return new PlayerService(roomRepository, playerRepository, roomService, gameService, gameRules,
                messagingTemplate, roleAssigner);
    }

    private Room room(int playerCount) {
        return room(playerCount, true, false);
    }

    private Room room(int playerCount, boolean remoteJoin, boolean manualRoles) {
        return new Room("ABCD", MASTER_TOKEN, GameMode.CLASSIC, playerCount, Map.of(
                Role.WEREWOLF, 1, Role.PRIEST, 0, Role.VILLAGER, playerCount - 1), remoteJoin, manualRoles);
    }

    private List<Player> players(Room room, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Player(room, "PLAYER" + i, "token" + i))
                .toList();
    }

    @Test
    void joinRoom_addsPlayerAndBroadcastsState() {
        Room room = room(6);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(2L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(false);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of());

        JoinRoomResponse response = playerService().joinRoom("ABCD", "Alice");

        assertThat(response.nickname()).isEqualTo("ALICE");
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING_FOR_PLAYERS);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/ABCD"), any(RoomStateMessage.class));
    }

    @Test
    void joinRoom_normalizesNicknameToUppercase() {
        Room room = room(6);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(2L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("BOB"))).thenReturn(false);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of());

        JoinRoomResponse response = playerService().joinRoom("ABCD", "bob");

        assertThat(response.nickname()).isEqualTo("BOB");
    }

    @Test
    void joinRoom_doesNotAutoStartWhenRoomBecomesFull() {
        Room room = room(3);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(2L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("CAROL"))).thenReturn(false);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of());

        playerService().joinRoom("ABCD", "Carol");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING_FOR_PLAYERS);

        ArgumentCaptor<RoomStateMessage> messageCaptor = ArgumentCaptor.forClass(RoomStateMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/ABCD"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().status()).isEqualTo(RoomStatus.WAITING_FOR_PLAYERS);
    }

    @Test
    void joinRoom_rejectsWhenRoomNotFound() {
        when(roomRepository.findByCode("ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService().joinRoom("ZZZZ", "Alice"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void joinRoom_rejectsWhenRoomFull() {
        Room room = room(3);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(3L);

        assertThatThrownBy(() -> playerService().joinRoom("ABCD", "Dave"))
                .isInstanceOf(RoomFullException.class);
    }

    @Test
    void joinRoom_rejectsDuplicateNickname() {
        Room room = room(6);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(2L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(true);

        assertThatThrownBy(() -> playerService().joinRoom("ABCD", "Alice"))
                .isInstanceOf(NicknameTakenException.class);
    }

    @Test
    void joinRoom_rejectsWhenRoomAlreadyStarted() {
        Room room = room(6);
        room.start();
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> playerService().joinRoom("ABCD", "Alice"))
                .isInstanceOf(RoomAlreadyStartedException.class);
    }

    @Test
    void joinRoom_rejectsWhenRoomDoesNotAcceptRemoteJoins() {
        Room room = room(6, false, false);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> playerService().joinRoom("ABCD", "Alice"))
                .isInstanceOf(InvalidRulesetException.class);
    }

    // ---------- addPlayerManually ----------

    @Test
    void addPlayerManually_rejectsWhenRoomAcceptsRemoteJoins() {
        Room room = room(6);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", null))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void addPlayerManually_randomRoom_rejectsWhenRoleProvided() {
        Room room = room(6, false, false);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(0L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(false);

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", Role.WEREWOLF))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void addPlayerManually_randomRoom_addsPlayerWithoutRole() {
        Room room = room(6, false, false);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(0L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(false);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ManualPlayerResponse response = playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", null);

        assertThat(response.nickname()).isEqualTo("ALICE");
        assertThat(response.role()).isNull();
        verify(roleAssigner, never()).assignRole(any(), any());
    }

    @Test
    void addPlayerManually_manualRoom_rejectsWhenRoleMissing() {
        Room room = room(6, false, true);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(0L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(false);

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", null))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void addPlayerManually_manualRoom_rejectsGhostAsStartingRole() {
        Room room = room(6, false, true);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(0L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(false);

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", Role.GHOST))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void addPlayerManually_manualRoom_rejectsAngelAsStartingRole() {
        Room room = room(6, false, true);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(0L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(false);

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", Role.ANGEL))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void addPlayerManually_manualRoom_rejectsWhenRolePoolExhausted() {
        Room room = room(6, false, true);
        Player existingWerewolf = new Player(room, "BOB", "bob-token");
        existingWerewolf.setRole(Role.WEREWOLF);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(1L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(false);
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of(existingWerewolf));

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", Role.WEREWOLF))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void addPlayerManually_manualRoom_assignsChosenRole() {
        Room room = room(6, false, true);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(0L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(false);
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of());
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", Role.WEREWOLF);

        verify(roleAssigner).assignRole(any(Player.class), eq(Role.WEREWOLF));
    }

    @Test
    void addPlayerManually_rejectsWhenRoomFull() {
        Room room = room(3, false, true);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(3L);

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", Role.WEREWOLF))
                .isInstanceOf(RoomFullException.class);
    }

    @Test
    void addPlayerManually_rejectsDuplicateNickname() {
        Room room = room(6, false, true);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.countByRoomId(any())).thenReturn(1L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("ALICE"))).thenReturn(true);

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", Role.WEREWOLF))
                .isInstanceOf(NicknameTakenException.class);
    }

    @Test
    void addPlayerManually_rejectsWhenRoomAlreadyStarted() {
        Room room = room(6, false, true);
        room.start();
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);

        assertThatThrownBy(() -> playerService().addPlayerManually("ABCD", MASTER_TOKEN, "Alice", Role.WEREWOLF))
                .isInstanceOf(RoomAlreadyStartedException.class);
    }

    @Test
    void kickPlayer_removesPlayerAndBroadcastsState() {
        Room room = room(6);
        setId(room, 1L);
        Player player = new Player(room, "ALICE", "alice-token");
        setId(player, 42L);

        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.findByIdAndRoomId(42L, room.getId())).thenReturn(Optional.of(player));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of());

        playerService().kickPlayer("ABCD", 42L, MASTER_TOKEN);

        verify(playerRepository).delete(player);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/ABCD"), any(RoomStateMessage.class));
    }

    @Test
    void kickPlayer_rejectsUnknownPlayer() {
        Room room = room(6);
        setId(room, 1L);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);

        assertThatThrownBy(() -> playerService().kickPlayer("ABCD", 99L, MASTER_TOKEN))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void kickPlayer_rejectsPlayerFromAnotherRoom() {
        Room room = room(6);
        setId(room, 1L);
        Room otherRoom = room(6);
        setId(otherRoom, 2L);
        Player player = new Player(otherRoom, "ALICE", "alice-token");
        setId(player, 42L);

        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);

        assertThatThrownBy(() -> playerService().kickPlayer("ABCD", 42L, MASTER_TOKEN))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void kickPlayer_rejectsWhenRoomAlreadyStarted() {
        Room room = room(6);
        room.start();
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);

        assertThatThrownBy(() -> playerService().kickPlayer("ABCD", 1L, MASTER_TOKEN))
                .isInstanceOf(RoomAlreadyStartedException.class);
    }

    @Test
    void kickPlayer_rejectsWrongMasterToken() {
        when(roomService.findRoomForMaster("ABCD", "wrong-token"))
                .thenThrow(new MasterTokenMismatchException("ABCD"));

        assertThatThrownBy(() -> playerService().kickPlayer("ABCD", 1L, "wrong-token"))
                .isInstanceOf(MasterTokenMismatchException.class);
    }

    @Test
    void startGame_startsRoomWhenEnoughPlayersJoined() {
        Room room = room(10);
        List<Player> players = players(room, gameRules.getMinPlayers());
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(players);

        playerService().startGame("ABCD", MASTER_TOKEN);

        verify(gameService).startGame(room, players);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/ABCD"), any(RoomStateMessage.class));
    }

    @Test
    void startGame_allowsStartingBeforeRoomIsFull() {
        Room room = room(10);
        List<Player> players = players(room, 5);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(players);

        playerService().startGame("ABCD", MASTER_TOKEN);

        verify(gameService).startGame(room, players);
    }

    @Test
    void startGame_rejectsWhenFewerThanMinimumPlayersJoined() {
        Room room = room(10);
        List<Player> players = players(room, gameRules.getMinPlayers() - 1);
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(players);

        assertThatThrownBy(() -> playerService().startGame("ABCD", MASTER_TOKEN))
                .isInstanceOf(NotEnoughPlayersException.class);

        verify(gameService, never()).startGame(any(), any());
    }

    @Test
    void startGame_rejectsWhenNotEnoughPlayersForSpecialRoles() {
        // room configured with 1 werewolf + 1 priest, but only 2 players joined total minus villager slack
        Room room = new Room("ABCD", MASTER_TOKEN, GameMode.CLASSIC, 10, Map.of(
                Role.WEREWOLF, 3, Role.PRIEST, 2, Role.VILLAGER, 5), true, false);
        List<Player> players = players(room, gameRules.getMinPlayers());
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(players);

        assertThatThrownBy(() -> playerService().startGame("ABCD", MASTER_TOKEN))
                .isInstanceOf(InvalidRulesetException.class);

        verify(gameService, never()).startGame(any(), any());
    }

    @Test
    void startGame_rejectsWhenManualRolesRoomNotFullyDealt() {
        Room room = room(10, false, true);
        List<Player> players = players(room, gameRules.getMinPlayers());
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(players);

        assertThatThrownBy(() -> playerService().startGame("ABCD", MASTER_TOKEN))
                .isInstanceOf(NotEnoughPlayersException.class);

        verify(gameService, never()).startGame(any(), any());
    }

    @Test
    void startGame_rejectsWhenAlreadyStarted() {
        Room room = room(10);
        room.start();
        when(roomService.findRoomForMaster("ABCD", MASTER_TOKEN)).thenReturn(room);

        assertThatThrownBy(() -> playerService().startGame("ABCD", MASTER_TOKEN))
                .isInstanceOf(RoomAlreadyStartedException.class);
    }

    @Test
    void startGame_rejectsWrongMasterToken() {
        when(roomService.findRoomForMaster("ABCD", "wrong-token"))
                .thenThrow(new MasterTokenMismatchException("ABCD"));

        assertThatThrownBy(() -> playerService().startGame("ABCD", "wrong-token"))
                .isInstanceOf(MasterTokenMismatchException.class);
    }

    @Test
    void getRole_returnsRoleForValidToken() {
        Room room = room(6);
        setId(room, 1L);
        Player player = new Player(room, "ALICE", "alice-token");
        setId(player, 42L);
        player.setRole(Role.WEREWOLF);

        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByIdAndRoomId(42L, room.getId())).thenReturn(Optional.of(player));

        PlayerRoleResponse response = playerService().getRole("ABCD", 42L, "alice-token");

        assertThat(response.role()).isEqualTo(Role.WEREWOLF);
        assertThat(response.alive()).isTrue();
    }

    @Test
    void getRole_hidesOvernightDeathDuringMorningReveal() {
        Room room = room(6);
        setId(room, 1L);
        room.setPhase(GamePhase.MORNING_REVEAL);
        Player player = new Player(room, "ALICE", "alice-token");
        setId(player, 42L);
        player.kill();

        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByIdAndRoomId(42L, room.getId())).thenReturn(Optional.of(player));

        PlayerRoleResponse response = playerService().getRole("ABCD", 42L, "alice-token");

        assertThat(response.alive()).isTrue();
    }

    @Test
    void getRole_revealsOvernightDeathOnceDiscussionStarts() {
        Room room = room(6);
        setId(room, 1L);
        room.setPhase(GamePhase.DISCUSSION);
        Player player = new Player(room, "ALICE", "alice-token");
        setId(player, 42L);
        player.kill();

        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByIdAndRoomId(42L, room.getId())).thenReturn(Optional.of(player));

        PlayerRoleResponse response = playerService().getRole("ABCD", 42L, "alice-token");

        assertThat(response.alive()).isFalse();
    }

    @Test
    void getRole_revealsVoteDeathImmediately() {
        Room room = room(6);
        setId(room, 1L);
        room.setPhase(GamePhase.NIGHT_START);
        Player player = new Player(room, "ALICE", "alice-token");
        setId(player, 42L);
        player.kill();

        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByIdAndRoomId(42L, room.getId())).thenReturn(Optional.of(player));

        PlayerRoleResponse response = playerService().getRole("ABCD", 42L, "alice-token");

        assertThat(response.alive()).isFalse();
    }

    @Test
    void getRole_rejectsWrongPlayerToken() {
        Room room = room(6);
        setId(room, 1L);
        Player player = new Player(room, "ALICE", "alice-token");
        setId(player, 42L);

        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByIdAndRoomId(42L, room.getId())).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerService().getRole("ABCD", 42L, "wrong-token"))
                .isInstanceOf(PlayerTokenMismatchException.class);
    }

    @Test
    void getRole_rejectsMissingPlayerToken() {
        Room room = room(6);
        setId(room, 1L);
        Player player = new Player(room, "ALICE", "alice-token");
        setId(player, 42L);

        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByIdAndRoomId(42L, room.getId())).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerService().getRole("ABCD", 42L, null))
                .isInstanceOf(PlayerTokenMismatchException.class);
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
