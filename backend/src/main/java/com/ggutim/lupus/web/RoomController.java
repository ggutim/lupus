package com.ggutim.lupus.web;

import com.ggutim.lupus.dto.CreateRoomRequest;
import com.ggutim.lupus.dto.RoomResponse;
import com.ggutim.lupus.dto.RoomStateMessage;
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

    public RoomController(RoomService roomService, PlayerService playerService) {
        this.roomService = roomService;
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request));
    }

    @GetMapping("/{code}")
    public ResponseEntity<RoomStateMessage> getRoom(@PathVariable String code,
                                                     @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        return ResponseEntity.ok(roomService.getRoomState(code, masterToken));
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<Void> startGame(@PathVariable String code,
                                           @RequestHeader(value = "X-Master-Token", required = false) String masterToken) {
        playerService.startGame(code.toUpperCase(), masterToken);
        return ResponseEntity.noContent().build();
    }
}
