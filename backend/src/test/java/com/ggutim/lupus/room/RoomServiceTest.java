package com.ggutim.lupus.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.room.dto.CreateRoomRequest;
import com.ggutim.lupus.room.dto.RoomStateMessage;
import com.ggutim.lupus.room.exception.InvalidRulesetException;
import com.ggutim.lupus.room.exception.MasterTokenMismatchException;
import com.ggutim.lupus.room.exception.RoomNotFoundException;
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
        return new RoomService(roomRepository, playerRepository);
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

        Room room = roomService.createRoom(request);

        assertThat(room.getRoleCounts())
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
    void createRoom_allowsRoleCountsExactlyMatchingPlayerCount() {
        RoomService roomService = roomService();
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 6, 4, 2);

        Room room = roomService.createRoom(request);

        assertThat(room.getRoleCounts()).containsEntry(Role.VILLAGER, 0);
    }

    @Test
    void createRoom_generatesFourCharacterUppercaseCode() {
        RoomService roomService = roomService();
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 8, 2, 1);

        Room room = roomService.createRoom(request);

        assertThat(room.getCode()).matches("[A-Z0-9]{4}");
    }

    @Test
    void createRoom_retriesCodeGenerationOnCollision() {
        RoomService roomService = roomService();
        when(roomRepository.existsByCode(any())).thenReturn(true, false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 8, 2, 1);

        Room room = roomService.createRoom(request);

        assertThat(room.getCode()).matches("[A-Z0-9]{4}");
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

        Room roomA = roomService().createRoom(request);
        Room roomB = roomService().createRoom(request);

        assertThat(roomA.hasMasterToken(roomA.getMasterToken())).isTrue();
        assertThat(roomA.getMasterToken()).isNotEqualTo(roomB.getMasterToken());
    }
}
