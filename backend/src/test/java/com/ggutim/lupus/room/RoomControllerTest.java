package com.ggutim.lupus.room;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.room.dto.RoomStateMessage;
import com.ggutim.lupus.room.exception.RoomNotFoundException;
import com.ggutim.lupus.web.ApiExceptionHandler;
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

    @Test
    void createRoom_returnsCreatedRoom() {
        Room room = new Room("X7K2", GameMode.CLASSIC, 10, Map.of(
                Role.WEREWOLF, 3, Role.PRIEST, 1, Role.VILLAGER, 6));
        when(roomService.createRoom(any())).thenReturn(room);

        mvc.post().uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"gameMode":"CLASSIC","playerCount":10,"werewolfCount":3,"priestCount":1}
                        """)
                .assertThat()
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.code").isEqualTo("X7K2");
    }

    @Test
    void createRoom_rejectsPlayerCountBelowMinimum() {
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
        Room room = new Room("MIN4", GameMode.CLASSIC, 4, Map.of(
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
    void getRoom_returnsRoomState() {
        RoomStateMessage state = new RoomStateMessage("ABCD", RoomStatus.WAITING_FOR_PLAYERS, 6, List.of());
        when(roomService.getRoomState(eq("ABCD"))).thenReturn(state);

        mvc.get().uri("/api/rooms/ABCD")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.code").isEqualTo("ABCD");
    }

    @Test
    void getRoom_returnsNotFoundForUnknownCode() {
        when(roomService.getRoomState(eq("ZZZZ"))).thenThrow(new RoomNotFoundException("ZZZZ"));

        mvc.get().uri("/api/rooms/ZZZZ")
                .assertThat()
                .hasStatus(404);
    }
}
