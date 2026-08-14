package com.ggutim.lupus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.config.GameRules;
import com.ggutim.lupus.dto.CreateRoomRequest;
import com.ggutim.lupus.dto.RoomResponse;
import com.ggutim.lupus.dto.RoomStateMessage;
import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.GameMode;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PlayerRepository playerRepository;

    private RoomService roomService() {
        GameRules gameRules = new GameRules();
        gameRules.setMinPlayers(4);
        gameRules.setMaxPlayers(30);
        return new RoomService(roomRepository, playerRepository, gameRules);
    }

    private Room room(String code, String masterToken, int playerCount) {
        return new Room(code, masterToken, GameMode.CLASSIC, playerCount, Map.of(
                Role.WEREWOLF, 1, Role.PRIEST, 0, Role.VILLAGER, playerCount - 1));
    }

    @Test
    void createRoom_computesVillagerCountAsRemainder() {
        RoomService roomService = roomService();
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 10, 3, 1);

        RoomResponse room = roomService.createRoom(request);

        assertThat(room.roleCounts())
                .containsEntry(Role.WEREWOLF, 3)
                .containsEntry(Role.PRIEST, 1)
                .containsEntry(Role.VILLAGER, 6);
    }

    @Test
    void createRoom_rejectsRoleCountsExceedingPlayerCount() {
        RoomService roomService = roomService();

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 6, 4, 3);

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void createRoom_rejectsPlayerCountBelowConfiguredMinimum() {
        RoomService roomService = roomService();

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 3, 1, 0);

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void createRoom_rejectsPlayerCountAboveConfiguredMaximum() {
        RoomService roomService = roomService();

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 31, 1, 0);

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void createRoom_respectsConfiguredPlayerCountRange() {
        GameRules gameRules = new GameRules();
        gameRules.setMinPlayers(2);
        gameRules.setMaxPlayers(3);
        RoomService roomService = new RoomService(roomRepository, playerRepository, gameRules);
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 3, 1, 0);

        RoomResponse room = roomService.createRoom(request);

        assertThat(room.playerCount()).isEqualTo(3);
    }

    @Test
    void createRoom_allowsRoleCountsExactlyMatchingPlayerCount() {
        RoomService roomService = roomService();
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 6, 4, 2);

        RoomResponse room = roomService.createRoom(request);

        assertThat(room.roleCounts()).containsEntry(Role.VILLAGER, 0);
    }

    @Test
    void createRoom_generatesFourCharacterUppercaseCode() {
        RoomService roomService = roomService();
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 8, 2, 1);

        RoomResponse room = roomService.createRoom(request);

        assertThat(room.code()).matches("[A-Z0-9]{4}");
    }

    @Test
    void createRoom_retriesCodeGenerationOnCollision() {
        RoomService roomService = roomService();
        when(roomRepository.existsByCode(any())).thenReturn(true, false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 8, 2, 1);

        RoomResponse room = roomService.createRoom(request);

        assertThat(room.code()).matches("[A-Z0-9]{4}");
    }

    @Test
    void getRoomState_returnsStateForExistingRoomWithValidToken() {
        Room room = room("ABCD", "secret-token", 6);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of());

        RoomStateMessage state = roomService().getRoomState("abcd", "secret-token");

        assertThat(state.code()).isEqualTo("ABCD");
        assertThat(state.status()).isEqualTo(RoomStatus.WAITING_FOR_PLAYERS);
        assertThat(state.playerCount()).isEqualTo(6);
        assertThat(state.players()).isEmpty();
    }

    @Test
    void getRoomState_rejectsUnknownCode() {
        when(roomRepository.findByCode("ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService().getRoomState("zzzz", "any-token"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void getRoomState_rejectsWrongMasterToken() {
        Room room = room("ABCD", "secret-token", 6);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService().getRoomState("ABCD", "wrong-token"))
                .isInstanceOf(MasterTokenMismatchException.class);
    }

    @Test
    void getRoomState_rejectsMissingMasterToken() {
        Room room = room("ABCD", "secret-token", 6);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService().getRoomState("ABCD", null))
                .isInstanceOf(MasterTokenMismatchException.class);
    }

    @Test
    void createRoom_generatesUniqueMasterTokenPerRoom() {
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 8, 2, 1);

        RoomResponse roomA = roomService().createRoom(request);
        RoomResponse roomB = roomService().createRoom(request);

        assertThat(roomA.masterToken()).isNotBlank();
        assertThat(roomA.masterToken()).isNotEqualTo(roomB.masterToken());
    }
}
