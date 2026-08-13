package com.ggutim.lupus.room;

import com.ggutim.lupus.room.dto.CreateRoomRequest;
import com.ggutim.lupus.room.dto.RoomResponse;
import com.ggutim.lupus.room.dto.RoomStateMessage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        Room room = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(room));
    }

    @GetMapping("/{code}")
    public ResponseEntity<RoomStateMessage> getRoom(@PathVariable String code) {
        return ResponseEntity.ok(roomService.getRoomState(code));
    }
}
