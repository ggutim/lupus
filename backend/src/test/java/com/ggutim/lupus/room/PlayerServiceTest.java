package com.ggutim.lupus.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.room.dto.RoomStateMessage;
import com.ggutim.lupus.room.exception.NicknameTakenException;
import com.ggutim.lupus.room.exception.RoomAlreadyStartedException;
import com.ggutim.lupus.room.exception.RoomFullException;
import com.ggutim.lupus.room.exception.RoomNotFoundException;
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

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private PlayerService playerService() {
        return new PlayerService(roomRepository, playerRepository, messagingTemplate);
    }

    private Room room(int playerCount) {
        return new Room("ABCD", GameMode.CLASSIC, playerCount, Map.of(
                Role.WEREWOLF, 1, Role.PRIEST, 0, Role.VILLAGER, playerCount - 1));
    }

    @Test
    void joinRoom_addsPlayerAndBroadcastsState() {
        Room room = room(6);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(2L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("Alice"))).thenReturn(false);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of());

        Player player = playerService().joinRoom("ABCD", "Alice");

        assertThat(player.getNickname()).isEqualTo("Alice");
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING_FOR_PLAYERS);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/ABCD"), any(RoomStateMessage.class));
    }

    @Test
    void joinRoom_startsRoomWhenLastPlayerJoins() {
        Room room = room(3);
        when(roomRepository.findByCode("ABCD")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(2L);
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("Carol"))).thenReturn(false);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findByRoomIdOrderByJoinedAtAsc(any())).thenReturn(List.of());

        playerService().joinRoom("ABCD", "Carol");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.STARTED);
        verify(roomRepository).save(room);

        ArgumentCaptor<RoomStateMessage> messageCaptor = ArgumentCaptor.forClass(RoomStateMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/ABCD"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().status()).isEqualTo(RoomStatus.STARTED);
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
        when(playerRepository.existsByRoomIdAndNicknameIgnoreCase(any(), eq("Alice"))).thenReturn(true);

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
}
