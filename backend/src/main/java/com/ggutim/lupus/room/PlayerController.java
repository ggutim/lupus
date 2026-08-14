package com.ggutim.lupus.room;

import com.ggutim.lupus.room.dto.JoinRoomRequest;
import com.ggutim.lupus.room.dto.JoinRoomResponse;
import com.ggutim.lupus.room.dto.PlayerRoleResponse;
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
        Player player = playerService.joinRoom(code.toUpperCase(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JoinRoomResponse.from(player, player.getPlayerToken()));
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
