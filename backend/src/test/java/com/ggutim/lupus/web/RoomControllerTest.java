package com.ggutim.lupus.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.dto.GameRulesResponse;
import com.ggutim.lupus.dto.RoomResponse;
import com.ggutim.lupus.dto.RoomStateMessage;
import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
import com.ggutim.lupus.exception.NotEnoughPlayersException;
import com.ggutim.lupus.exception.RoomAlreadyStartedException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.GameMode;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.RoomStatus;
import com.ggutim.lupus.service.PlayerService;
import com.ggutim.lupus.service.RoomService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = RoomController.class)
@Import(ApiExceptionHandler.class)
class RoomControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void createRoom_returnsCreatedRoomWithMasterToken() {
        RoomResponse room = new RoomResponse("X7K2", "secret-token", GameMode.CLASSIC, 10, Map.of(
                Role.WEREWOLF, 3, Role.PRIEST, 1, Role.VILLAGER, 6));
        when(roomService.createRoom(any())).thenReturn(room);

        var result = mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":1}
                        """)
                .assertThat()
                .hasStatus(201);

        result.bodyJson().extractingPath("$.code").isEqualTo("X7K2");
        result.bodyJson().extractingPath("$.masterToken").isEqualTo("secret-token");
    }

    @Test
    void createRoom_rejectsPlayerCountOutOfConfiguredRange() {
        when(roomService.createRoom(any())).thenThrow(
                new InvalidRulesetException("playerCount must be between 4 and 30"));

        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":3,"werewolfCount":1,"priestCount":0}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void createRoom_acceptsPlayerCountAtMinimum() {
        RoomResponse room = new RoomResponse("MIN4", "secret-token", GameMode.CLASSIC, 4, Map.of(
                Role.WEREWOLF, 1, Role.PRIEST, 0, Role.VILLAGER, 3));
        when(roomService.createRoom(any())).thenReturn(room);

        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":4,"werewolfCount":1,"priestCount":0}
                        """)
                .assertThat()
                .hasStatus(201);
    }

    @Test
    void createRoom_rejectsMissingGameMode() {
        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"playerCount":10,"werewolfCount":3,"priestCount":1}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void getRules_returnsConfiguredPlayerBounds() {
        when(roomService.getGameRules()).thenReturn(new GameRulesResponse(4, 30));

        mvc.get().uri("/api/rooms/rules")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.minPlayers").isEqualTo(4);
    }

    @Test
    void getRoom_returnsRoomState() {
        RoomStateMessage state = new RoomStateMessage("ABCD", RoomStatus.WAITING_FOR_PLAYERS, 6, List.of());
        when(roomService.getRoomState(eq("ABCD"), eq("secret-token"))).thenReturn(state);

        mvc.get().uri("/api/rooms/ABCD")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.code").isEqualTo("ABCD");
    }

    @Test
    void getRoom_returnsNotFoundForUnknownCode() {
        when(roomService.getRoomState(eq("ZZZZ"), any())).thenThrow(new RoomNotFoundException("ZZZZ"));

        mvc.get().uri("/api/rooms/ZZZZ")
                .header("X-Master-Token", "any-token")
                .assertThat()
                .hasStatus(404);
    }

    @Test
    void getRoom_returnsForbiddenForWrongMasterToken() {
        when(roomService.getRoomState(eq("ABCD"), eq("wrong-token")))
                .thenThrow(new MasterTokenMismatchException("ABCD"));

        mvc.get().uri("/api/rooms/ABCD")
                .header("X-Master-Token", "wrong-token")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void getRoom_returnsForbiddenWhenMasterTokenMissing() {
        when(roomService.getRoomState(eq("ABCD"), any()))
                .thenThrow(new MasterTokenMismatchException("ABCD"));

        mvc.get().uri("/api/rooms/ABCD")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void startGame_returnsNoContent() {
        mvc.post().uri("/api/rooms/ABCD/start")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatus(204);

        verify(playerService).startGame(eq("ABCD"), eq("secret-token"));
    }

    @Test
    void startGame_returnsForbiddenWhenMasterTokenInvalid() {
        doThrow(new MasterTokenMismatchException("ABCD")).when(playerService)
                .startGame(eq("ABCD"), any());

        mvc.post().uri("/api/rooms/ABCD/start")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void startGame_returnsConflictWhenNotEnoughPlayers() {
        doThrow(new NotEnoughPlayersException("ABCD", 4)).when(playerService)
                .startGame(eq("ABCD"), eq("secret-token"));

        mvc.post().uri("/api/rooms/ABCD/start")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatus(409);
    }

    @Test
    void startGame_returnsConflictWhenAlreadyStarted() {
        doThrow(new RoomAlreadyStartedException("ABCD")).when(playerService)
                .startGame(eq("ABCD"), eq("secret-token"));

        mvc.post().uri("/api/rooms/ABCD/start")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatus(409);
    }
}
