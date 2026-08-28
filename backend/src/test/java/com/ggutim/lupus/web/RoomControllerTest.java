package com.ggutim.lupus.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.dto.GameRulesResponse;
import com.ggutim.lupus.dto.ManualPlayerResponse;
import com.ggutim.lupus.dto.MasterRoomStateResponse;
import com.ggutim.lupus.dto.PlayerResponse;
import com.ggutim.lupus.dto.RoomResponse;
import com.ggutim.lupus.dto.RoomStateMessage;
import com.ggutim.lupus.dto.VillageOverviewResponse;
import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
import com.ggutim.lupus.exception.NotEnoughPlayersException;
import com.ggutim.lupus.exception.RoomAlreadyStartedException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.GameMode;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.RoomStatus;
import com.ggutim.lupus.service.GameService;
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

    @MockitoBean
    private GameService gameService;

    @Test
    void createRoom_returnsCreatedRoomWithMasterToken() {
        RoomResponse room = new RoomResponse("X7K2", "secret-token", GameMode.CLASSIC, 10, Map.of(
                Role.WEREWOLF, 3, Role.PRIEST, 1, Role.VILLAGER, 6), true, false);
        when(roomService.createRoom(any())).thenReturn(room);

        var result = mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":1,"gravediggerCount":0,"idiotCount":0,"corruptedJudgeCount":0,"survivorCount":0,"guardianCount":0,"killerCount":0,"mayorCount":0,"remoteJoin":true,"manualRoles":false}
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
                        {"gameMode":"CLASSIC","playerCount":3,"werewolfCount":1,"priestCount":0,"gravediggerCount":0,"idiotCount":0,"corruptedJudgeCount":0,"survivorCount":0,"guardianCount":0,"killerCount":0,"mayorCount":0,"remoteJoin":true,"manualRoles":false}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void createRoom_acceptsPlayerCountAtMinimum() {
        RoomResponse room = new RoomResponse("MIN4", "secret-token", GameMode.CLASSIC, 4, Map.of(
                Role.WEREWOLF, 1, Role.PRIEST, 0, Role.VILLAGER, 3), true, false);
        when(roomService.createRoom(any())).thenReturn(room);

        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":4,"werewolfCount":1,"priestCount":0,"gravediggerCount":0,"idiotCount":0,"corruptedJudgeCount":0,"survivorCount":0,"guardianCount":0,"killerCount":0,"mayorCount":0,"remoteJoin":true,"manualRoles":false}
                        """)
                .assertThat()
                .hasStatus(201);
    }

    @Test
    void createRoom_rejectsMoreThanOneCorruptedJudge() {
        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":1,"gravediggerCount":0,"idiotCount":0,"corruptedJudgeCount":2,"survivorCount":0,"guardianCount":0,"killerCount":0,"mayorCount":0,"remoteJoin":true,"manualRoles":false}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void createRoom_rejectsMoreThanOnePriest() {
        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":2,"gravediggerCount":0,"idiotCount":0,"corruptedJudgeCount":0,"survivorCount":0,"guardianCount":0,"killerCount":0,"mayorCount":0,"remoteJoin":true,"manualRoles":false}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void createRoom_rejectsMoreThanOneGravedigger() {
        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":1,"gravediggerCount":2,"idiotCount":0,"corruptedJudgeCount":0,"survivorCount":0,"guardianCount":0,"killerCount":0,"mayorCount":0,"remoteJoin":true,"manualRoles":false}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void createRoom_rejectsMoreThanOneGuardian() {
        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":1,"gravediggerCount":0,"idiotCount":0,"corruptedJudgeCount":0,"survivorCount":0,"guardianCount":2,"killerCount":0,"mayorCount":0,"remoteJoin":true,"manualRoles":false}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void createRoom_rejectsMoreThanOneKiller() {
        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":1,"gravediggerCount":0,"idiotCount":0,"corruptedJudgeCount":0,"survivorCount":0,"guardianCount":0,"killerCount":2,"mayorCount":0,"remoteJoin":true,"manualRoles":false}
                        """)
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void createRoom_rejectsMoreThanOneMayor() {
        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":1,"gravediggerCount":0,"idiotCount":0,"corruptedJudgeCount":0,"survivorCount":0,"guardianCount":0,"killerCount":0,"mayorCount":2,"remoteJoin":true,"manualRoles":false}
                        """)
                .assertThat()
                .hasStatus(400);
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
    void getPublicRoomState_returnsRoomStateWithoutAnyToken() {
        RoomStateMessage state = new RoomStateMessage("ABCD", RoomStatus.STARTED, 6, List.of());
        when(roomService.getPublicRoomState(eq("ABCD"))).thenReturn(state);

        mvc.get().uri("/api/rooms/ABCD/status")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status").isEqualTo("STARTED");
    }

    @Test
    void getPublicRoomState_returnsNotFoundForUnknownCode() {
        when(roomService.getPublicRoomState(eq("ZZZZ"))).thenThrow(new RoomNotFoundException("ZZZZ"));

        mvc.get().uri("/api/rooms/ZZZZ/status")
                .assertThat()
                .hasStatus(404);
    }

    @Test
    void getRoster_returnsMasterRosterWithRoles() {
        MasterRoomStateResponse state = new MasterRoomStateResponse("ABCD", RoomStatus.WAITING_FOR_PLAYERS, 6,
                false, true, Map.of(Role.WEREWOLF, 1, Role.VILLAGER, 5),
                List.of(new ManualPlayerResponse(1L, "ALICE", Role.WEREWOLF)));
        when(roomService.getMasterRosterState(eq("ABCD"), eq("secret-token"))).thenReturn(state);

        mvc.get().uri("/api/rooms/ABCD/roster")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.players[0].role").isEqualTo("WEREWOLF");
    }

    @Test
    void getRoster_returnsForbiddenForWrongMasterToken() {
        when(roomService.getMasterRosterState(eq("ABCD"), eq("wrong-token")))
                .thenThrow(new MasterTokenMismatchException("ABCD"));

        mvc.get().uri("/api/rooms/ABCD/roster")
                .header("X-Master-Token", "wrong-token")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void getVillageOverview_returnsRosterWithoutRoles() {
        VillageOverviewResponse overview = new VillageOverviewResponse(
                List.of(new PlayerResponse(1L, "ALICE", true, null, false),
                        new PlayerResponse(2L, "BOB", false, null, false)));
        when(gameService.getVillageOverview("ABCD")).thenReturn(overview);

        mvc.get().uri("/api/rooms/abcd/village")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.players[1].alive").isEqualTo(false);
    }

    @Test
    void getVillageOverview_returnsNotFoundForUnknownCode() {
        when(gameService.getVillageOverview("ZZZZ")).thenThrow(new RoomNotFoundException("ZZZZ"));

        mvc.get().uri("/api/rooms/ZZZZ/village")
                .assertThat()
                .hasStatus(404);
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
