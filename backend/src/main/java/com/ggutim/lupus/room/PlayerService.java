package com.ggutim.lupus.room;

import com.ggutim.lupus.room.dto.PlayerResponse;
import com.ggutim.lupus.room.dto.RoomStateMessage;
import com.ggutim.lupus.room.exception.NicknameTakenException;
import com.ggutim.lupus.room.exception.NotEnoughPlayersException;
import com.ggutim.lupus.room.exception.PlayerNotFoundException;
import com.ggutim.lupus.room.exception.RoomAlreadyStartedException;
import com.ggutim.lupus.room.exception.RoomFullException;
import com.ggutim.lupus.room.exception.RoomNotFoundException;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final GameRules gameRules;
    private final SimpMessagingTemplate messagingTemplate;

    public PlayerService(RoomRepository roomRepository, PlayerRepository playerRepository, RoomService roomService,
                          GameRules gameRules, SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.gameRules = gameRules;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Player joinRoom(String code, String nickname) {
        String normalizedNickname = nickname.toUpperCase();

        Room room = roomRepository.findByCode(code)
                .orElseThrow(() -> new RoomNotFoundException(code));

        if (room.getStatus() == RoomStatus.STARTED) {
            throw new RoomAlreadyStartedException(code);
        }

        long currentPlayerCount = playerRepository.countByRoomId(room.getId());
        if (currentPlayerCount >= room.getPlayerCount()) {
            throw new RoomFullException(code);
        }

        if (playerRepository.existsByRoomIdAndNicknameIgnoreCase(room.getId(), normalizedNickname)) {
            throw new NicknameTakenException(normalizedNickname);
        }

        Player player = playerRepository.save(new Player(room, normalizedNickname));

        broadcastRoomState(room);
        return player;
    }

    @Transactional
    public void kickPlayer(String code, Long playerId, String masterToken) {
        Room room = roomService.findRoomForMaster(code, masterToken);

        if (room.getStatus() == RoomStatus.STARTED) {
            throw new RoomAlreadyStartedException(code);
        }

        Player player = playerRepository.findById(playerId)
                .filter(p -> p.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new PlayerNotFoundException(playerId));

        playerRepository.delete(player);

        broadcastRoomState(room);
    }

    @Transactional
    public void startGame(String code, String masterToken) {
        Room room = roomService.findRoomForMaster(code, masterToken);

        if (room.getStatus() == RoomStatus.STARTED) {
            throw new RoomAlreadyStartedException(code);
        }

        long currentPlayerCount = playerRepository.countByRoomId(room.getId());
        if (currentPlayerCount < gameRules.getMinPlayers()) {
            throw new NotEnoughPlayersException(code, gameRules.getMinPlayers());
        }

        room.start();
        roomRepository.save(room);

        broadcastRoomState(room);
    }

    private void broadcastRoomState(Room room) {
        List<PlayerResponse> players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .map(PlayerResponse::from)
                .toList();

        RoomStateMessage message = new RoomStateMessage(
                room.getCode(), room.getStatus(), room.getPlayerCount(), players);

        messagingTemplate.convertAndSend("/topic/rooms/" + room.getCode(), message);
    }
}
