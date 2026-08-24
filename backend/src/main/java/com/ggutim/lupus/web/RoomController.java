package com.ggutim.lupus.web;

import com.ggutim.lupus.dto.CreateRoomRequest;
import com.ggutim.lupus.dto.GameRulesResponse;
import com.ggutim.lupus.dto.MasterRoomStateResponse;
import com.ggutim.lupus.dto.RoomResponse;
import com.ggutim.lupus.dto.RoomStateMessage;
import com.ggutim.lupus.dto.VillageOverviewResponse;
import com.ggutim.lupus.service.GameService;
import com.ggutim.lupus.service.PlayerService;
import com.ggutim.lupus.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final PlayerService playerService;
    private final GameService gameService;

    public RoomController(RoomService roomService, PlayerService playerService, GameService gameService) {
        this.roomService = roomService;
        this.playerService = playerService;
        this.gameService = gameService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request));
    }

    @GetMapping("/rules")
    public ResponseEntity<GameRulesResponse> getRules() {
        return ResponseEntity.ok(roomService.getGameRules());
    }

    @GetMapping("/{code}")
    public ResponseEntity<RoomStateMessage> getRoom(@PathVariable String code,
                                                     @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        return ResponseEntity.ok(roomService.getRoomState(code, masterToken));
    }

    @GetMapping("/{code}/roster")
    public ResponseEntity<MasterRoomStateResponse> getRoster(@PathVariable String code,
            @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        return ResponseEntity.ok(roomService.getMasterRosterState(code.toUpperCase(), masterToken));
    }

    @GetMapping("/{code}/village")
    public ResponseEntity<VillageOverviewResponse> getVillageOverview(@PathVariable String code) {
        return ResponseEntity.ok(gameService.getVillageOverview(code.toUpperCase()));
    }

    /**
     * Public counterpart to {@link #getRoom} for a player's own
     * client — same safe fields, no master token. Used to catch up on
     * room state (has the game started?) whenever the client's
     * WebSocket connects or reconnects, in case a push was missed.
     */
    @GetMapping("/{code}/status")
    public ResponseEntity<RoomStateMessage> getPublicRoomState(@PathVariable String code) {
        return ResponseEntity.ok(roomService.getPublicRoomState(code.toUpperCase()));
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<Void> startGame(@PathVariable String code,
                                           @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        playerService.startGame(code.toUpperCase(), masterToken);
        return ResponseEntity.noContent().build();
    }
}
