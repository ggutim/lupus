package com.ggutim.lupus.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.dto.MasterGameStateResponse;
import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.service.GameService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = GameController.class)
@Import(ApiExceptionHandler.class)
class GameControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private GameService gameService;

    private MasterGameStateResponse state(GamePhase phase) {
        return new MasterGameStateResponse(
                "ABCD", phase, 1, List.of(), null, null, null, null, null, false, null, null, null, null, true);
    }

    @Test
    void getGameState_returnsStateWithValidToken() {
        when(gameService.getGameState(eq("ABCD"), eq("secret-token"))).thenReturn(state(GamePhase.ROLES_ASSIGNED));

        mvc.get().uri("/api/rooms/ABCD/game")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.phase").isEqualTo("ROLES_ASSIGNED");
    }

    @Test
    void getGameState_returnsForbiddenForWrongToken() {
        when(gameService.getGameState(eq("ABCD"), eq("wrong")))
                .thenThrow(new MasterTokenMismatchException("ABCD"));

        mvc.get().uri("/api/rooms/ABCD/game")
                .header("X-Master-Token", "wrong")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void advancePhase_returnsUpdatedState() {
        when(gameService.advancePhase(eq("ABCD"), eq("secret-token"))).thenReturn(state(GamePhase.NIGHT_START));

        mvc.post().uri("/api/rooms/ABCD/game/advance")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.phase").isEqualTo("NIGHT_START");
    }

    @Test
    void advancePhase_returnsConflictWhenInvalidPhase() {
        doThrow(new InvalidGamePhaseException("nope")).when(gameService).advancePhase(eq("ABCD"), any());

        mvc.post().uri("/api/rooms/ABCD/game/advance")
                .header("X-Master-Token", "secret-token")
                .assertThat()
                .hasStatus(409);
    }

    @Test
    void selectNightTarget_returnsUpdatedState() {
        when(gameService.selectNightTarget(eq("ABCD"), eq("secret-token"), eq(7L)))
                .thenReturn(state(GamePhase.NIGHT_ACTIONS));

        mvc.post().uri("/api/rooms/ABCD/game/select-night-target")
                .header("X-Master-Token", "secret-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"playerId":7}
                        """)
                .assertThat()
                .hasStatusOk();

        verify(gameService).selectNightTarget(eq("ABCD"), eq("secret-token"), eq(7L));
    }

    @Test
    void selectNightTarget_returnsNotFoundForUnknownPlayer() {
        doThrow(new PlayerNotFoundException(99L)).when(gameService)
                .selectNightTarget(eq("ABCD"), any(), eq(99L));

        mvc.post().uri("/api/rooms/ABCD/game/select-night-target")
                .header("X-Master-Token", "secret-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"playerId":99}
                        """)
                .assertThat()
                .hasStatus(404);
    }

    @Test
    void selectVoteVictim_allowsNullPlayerId() {
        when(gameService.selectVoteVictim(eq("ABCD"), eq("secret-token"), isNull()))
                .thenReturn(state(GamePhase.VOTE_SELECT_TARGET));

        mvc.post().uri("/api/rooms/ABCD/game/select-vote-victim")
                .header("X-Master-Token", "secret-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"playerId":null}
                        """)
                .assertThat()
                .hasStatusOk();
    }
}
