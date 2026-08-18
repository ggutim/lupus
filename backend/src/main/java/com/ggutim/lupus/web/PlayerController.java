package com.ggutim.lupus.web;

import com.ggutim.lupus.dto.AddPlayerManualRequest;
import com.ggutim.lupus.dto.JoinRoomRequest;
import com.ggutim.lupus.dto.JoinRoomResponse;
import com.ggutim.lupus.dto.ManualPlayerResponse;
import com.ggutim.lupus.dto.PlayerRoleResponse;
import com.ggutim.lupus.service.PlayerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{code}/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<JoinRoomResponse> joinRoom(@PathVariable String code,
                                                      @Valid @RequestBody JoinRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playerService.joinRoom(code.toUpperCase(), request.nickname()));
    }

    @PostMapping("/manual")
    public ResponseEntity<ManualPlayerResponse> addPlayerManually(
            @PathVariable String code, @Valid @RequestBody AddPlayerManualRequest request,
            @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playerService.addPlayerManually(code.toUpperCase(), masterToken, request.nickname(),
                        request.role()));
    }

    @DeleteMapping("/{playerId}")
    public ResponseEntity<Void> kickPlayer(@PathVariable String code, @PathVariable Long playerId,
                                            @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        playerService.kickPlayer(code.toUpperCase(), playerId, masterToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{playerId}/role")
    public ResponseEntity<PlayerRoleResponse> getRole(@PathVariable String code, @PathVariable Long playerId,
                                                       @RequestHeader(value = "X-Player-Token", required = false) String playerToken) {
        return ResponseEntity.ok(playerService.getRole(code.toUpperCase(), playerId, playerToken));
    }
}
