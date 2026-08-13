package com.ggutim.lupus.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.room.dto.CreateRoomRequest;
import com.ggutim.lupus.room.exception.InvalidRulesetException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Test
    void createRoom_computesVillagerCountAsRemainder() {
        RoomService roomService = new RoomService(roomRepository);
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
        RoomService roomService = new RoomService(roomRepository);

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 6, 4, 3);

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void createRoom_allowsRoleCountsExactlyMatchingPlayerCount() {
        RoomService roomService = new RoomService(roomRepository);
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 6, 4, 2);

        Room room = roomService.createRoom(request);

        assertThat(room.getRoleCounts()).containsEntry(Role.VILLAGER, 0);
    }

    @Test
    void createRoom_generatesFourCharacterUppercaseCode() {
        RoomService roomService = new RoomService(roomRepository);
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 8, 2, 1);

        Room room = roomService.createRoom(request);

        assertThat(room.getCode()).matches("[A-Z0-9]{4}");
    }

    @Test
    void createRoom_retriesCodeGenerationOnCollision() {
        RoomService roomService = new RoomService(roomRepository);
        when(roomRepository.existsByCode(any())).thenReturn(true, false);
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC, 8, 2, 1);

        Room room = roomService.createRoom(request);

        assertThat(room.getCode()).matches("[A-Z0-9]{4}");
    }
}
