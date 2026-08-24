package com.ggutim.lupus.web;

import com.ggutim.lupus.dto.KillerGuessRequest;
import com.ggutim.lupus.dto.KillerGuessResponse;
import com.ggutim.lupus.dto.MasterGameStateResponse;
import com.ggutim.lupus.dto.SelectTargetRequest;
import jakarta.validation.Valid;
import com.ggutim.lupus.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Master-only endpoints driving the in-progress game: advancing phases
 * and making night/vote selections. All require the room's master
 * token; the resulting full game state (including roles) is returned
 * directly in the response rather than fetched separately, since only
 * the master ever calls these.
 */
@RestController
@RequestMapping("/api/rooms/{code}/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public ResponseEntity<MasterGameStateResponse> getGameState(
            @PathVariable String code,
            @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        return ResponseEntity.ok(gameService.getGameState(code.toUpperCase(), masterToken));
    }

    @PostMapping("/advance")
    public ResponseEntity<MasterGameStateResponse> advancePhase(
            @PathVariable String code,
            @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        return ResponseEntity.ok(gameService.advancePhase(code.toUpperCase(), masterToken));
    }

    @PostMapping("/select-night-target")
    public ResponseEntity<MasterGameStateResponse> selectNightTarget(
            @PathVariable String code,
            @RequestHeader(value = "X-Master-Token", required = false) String masterToken,
            @RequestBody SelectTargetRequest request) {
        return ResponseEntity.ok(gameService.selectNightTarget(code.toUpperCase(), masterToken, request.playerId()));
    }

    @PostMapping("/select-vote-victim")
    public ResponseEntity<MasterGameStateResponse> selectVoteVictim(
            @PathVariable String code,
            @RequestHeader(value = "X-Master-Token", required = false) String masterToken,
            @RequestBody SelectTargetRequest request) {
        return ResponseEntity.ok(gameService.selectVoteVictim(code.toUpperCase(), masterToken, request.playerId()));
    }

    @PostMapping("/killer-guess")
    public ResponseEntity<KillerGuessResponse> revealKillerAndGuess(
            @PathVariable String code,
            @RequestHeader(value = "X-Master-Token", required = false) String masterToken,
            @Valid @RequestBody KillerGuessRequest request) {
        return ResponseEntity.ok(gameService.revealKillerAndGuess(code.toUpperCase(), masterToken,
                request.targetPlayerId(), request.guessedRole()));
    }
}
