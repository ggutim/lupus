package com.ggutim.lupus.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.dto.JoinRoomResponse;
import com.ggutim.lupus.dto.PlayerRoleResponse;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
import com.ggutim.lupus.exception.NicknameTakenException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.exception.PlayerTokenMismatchException;
import com.ggutim.lupus.exception.RoomAlreadyStartedException;
import com.ggutim.lupus.exception.RoomFullException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.service.PlayerService;
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
        when(playerService.joinRoom(eq("ABCD"), eq("Alice")))
                .thenReturn(new JoinRoomResponse(1L, "Alice", "player-token"));

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

    @Test
    void kickPlayer_returnsNoContent() {
        mvc.delete().uri("/api/rooms/ABCD/players/42")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatus(204);

        verify(playerService).kickPlayer(eq("ABCD"), eq(42L), eq("secret-token"));
    }

    @Test
    void kickPlayer_returnsNotFoundForUnknownPlayer() {
        doThrow(new PlayerNotFoundException(99L)).when(playerService)
                .kickPlayer(eq("ABCD"), eq(99L), any());

        mvc.delete().uri("/api/rooms/ABCD/players/99")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatus(404);
    }

    @Test
    void kickPlayer_returnsConflictWhenRoomAlreadyStarted() {
        doThrow(new RoomAlreadyStartedException("ABCD")).when(playerService)
                .kickPlayer(eq("ABCD"), eq(42L), any());

        mvc.delete().uri("/api/rooms/ABCD/players/42")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatus(409);
    }

    @Test
    void kickPlayer_returnsForbiddenForWrongMasterToken() {
        doThrow(new MasterTokenMismatchException("ABCD")).when(playerService)
                .kickPlayer(eq("ABCD"), eq(42L), eq("wrong-token"));

        mvc.delete().uri("/api/rooms/ABCD/players/42")
                .header("X-Master-Token", "wrong-token")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void kickPlayer_returnsForbiddenWhenMasterTokenMissing() {
        doThrow(new MasterTokenMismatchException("ABCD")).when(playerService)
                .kickPlayer(eq("ABCD"), eq(42L), any());

        mvc.delete().uri("/api/rooms/ABCD/players/42")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void getRole_returnsRoleForValidToken() {
        when(playerService.getRole(eq("ABCD"), eq(42L), eq("player-token")))
                .thenReturn(new PlayerRoleResponse(Role.WEREWOLF));

        mvc.get().uri("/api/rooms/ABCD/players/42/role")
                .header("X-Player-Token", "player-token")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role").isEqualTo("WEREWOLF");
    }

    @Test
    void getRole_returnsForbiddenForWrongPlayerToken() {
        doThrow(new PlayerTokenMismatchException(42L)).when(playerService)
                .getRole(eq("ABCD"), eq(42L), eq("wrong-token"));

        mvc.get().uri("/api/rooms/ABCD/players/42/role")
                .header("X-Player-Token", "wrong-token")
                .assertThat()
                .hasStatus(403);
    }
}
