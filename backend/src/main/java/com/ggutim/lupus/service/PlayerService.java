package com.ggutim.lupus.service;

import com.ggutim.lupus.config.GameRules;
import com.ggutim.lupus.dto.JoinRoomResponse;
import com.ggutim.lupus.dto.PlayerResponse;
import com.ggutim.lupus.dto.PlayerRoleResponse;
import com.ggutim.lupus.dto.RoomStateMessage;
import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.exception.NicknameTakenException;
import com.ggutim.lupus.exception.NotEnoughPlayersException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.exception.PlayerTokenMismatchException;
import com.ggutim.lupus.exception.RoomAlreadyStartedException;
import com.ggutim.lupus.exception.RoomFullException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.model.RoomStatus;
import com.ggutim.lupus.repository.PlayerRepository;
import com.ggutim.lupus.repository.RoomRepository;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final GameService gameService;
    private final GameRules gameRules;
    private final SimpMessagingTemplate messagingTemplate;

    public PlayerService(RoomRepository roomRepository, PlayerRepository playerRepository, RoomService roomService,
                          GameService gameService, GameRules gameRules, SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.gameService = gameService;
        this.gameRules = gameRules;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public JoinRoomResponse joinRoom(String code, String nickname) {
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

        String playerToken = SecretTokens.generate();
        Player player = playerRepository.save(new Player(room, normalizedNickname, playerToken));

        broadcastRoomState(room);
        return JoinRoomResponse.from(player, playerToken);
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

        List<Player> players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId());
        if (players.size() < gameRules.getMinPlayers()) {
            throw new NotEnoughPlayersException(code, gameRules.getMinPlayers());
        }

        int specialRoleCount = room.getRoleCounts().getOrDefault(Role.WEREWOLF, 0)
                + room.getRoleCounts().getOrDefault(Role.PRIEST, 0);
        if (specialRoleCount > players.size()) {
            throw new InvalidRulesetException(
                    "Not enough players joined to fill the configured werewolf and priest roles");
        }

        gameService.startGame(room, players);

        broadcastRoomState(room);
    }

    public PlayerRoleResponse getRole(String code, Long playerId, String playerToken) {
        Room room = roomRepository.findByCode(code)
                .orElseThrow(() -> new RoomNotFoundException(code));

        Player player = playerRepository.findById(playerId)
                .filter(p -> p.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new PlayerNotFoundException(playerId));

        if (playerToken == null || !player.hasPlayerToken(playerToken)) {
            throw new PlayerTokenMismatchException(playerId);
        }

        boolean visiblyAlive = player.isAlive() || room.getPhase() == GamePhase.MORNING_REVEAL;
        return new PlayerRoleResponse(player.getRole(), visiblyAlive);
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
