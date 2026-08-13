package com.ggutim.lupus.room;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.room.exception.NicknameTakenException;
import com.ggutim.lupus.room.exception.RoomFullException;
import com.ggutim.lupus.room.exception.RoomNotFoundException;
import com.ggutim.lupus.web.ApiExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = PlayerController.class)
@Import(ApiExceptionHandler.class)
class PlayerControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void joinRoom_returnsCreatedPlayer() {
        Room room = new Room("ABCD", GameMode.CLASSIC, 6, Map.of(
                Role.WEREWOLF, 1, Role.PRIEST, 0, Role.VILLAGER, 5));
        when(playerService.joinRoom(eq("ABCD"), eq("Alice"))).thenReturn(new Player(room, "Alice"));

        mvc.post().uri("/api/rooms/abcd/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname":"Alice"}
                        """)
                .assertThat()
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.nickname").isEqualTo("Alice");
    }

    @Test
    void joinRoom_rejectsBlankNickname() {
        mvc.post().uri("/api/rooms/ABCD/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname":""}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void joinRoom_returnsNotFoundForUnknownRoom() {
        when(playerService.joinRoom(eq("ZZZZ"), any())).thenThrow(new RoomNotFoundException("ZZZZ"));

        mvc.post().uri("/api/rooms/ZZZZ/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname":"Alice"}
                        """)
                .assertThat()
                .hasStatus(404);
    }

    @Test
    void joinRoom_returnsConflictWhenRoomFull() {
        when(playerService.joinRoom(eq("ABCD"), any())).thenThrow(new RoomFullException("ABCD"));

        mvc.post().uri("/api/rooms/ABCD/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname":"Alice"}
                        """)
                .assertThat()
                .hasStatus(409);
    }

    @Test
    void joinRoom_returnsConflictWhenNicknameTaken() {
        when(playerService.joinRoom(eq("ABCD"), any())).thenThrow(new NicknameTakenException("Alice"));

        mvc.post().uri("/api/rooms/ABCD/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname":"Alice"}
                        """)
                .assertThat()
                .hasStatus(409);
    }
}
