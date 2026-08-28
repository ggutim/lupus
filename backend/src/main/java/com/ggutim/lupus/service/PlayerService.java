package com.ggutim.lupus.service;

import com.ggutim.lupus.config.GameRules;
import com.ggutim.lupus.dto.JoinRoomResponse;
import com.ggutim.lupus.dto.ManualPlayerResponse;
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
    private final RoleAssigner roleAssigner;

    public PlayerService(RoomRepository roomRepository, PlayerRepository playerRepository, RoomService roomService,
                          GameService gameService, GameRules gameRules, SimpMessagingTemplate messagingTemplate,
                          RoleAssigner roleAssigner) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.gameService = gameService;
        this.gameRules = gameRules;
        this.messagingTemplate = messagingTemplate;
        this.roleAssigner = roleAssigner;
    }

    @Transactional
    public JoinRoomResponse joinRoom(String code, String nickname) {
        String normalizedNickname = nickname.toUpperCase();

        Room room = roomRepository.findByCode(code)
                .orElseThrow(() -> new RoomNotFoundException(code));

        if (!room.isRemoteJoin()) {
            throw new InvalidRulesetException("Room " + code + " does not accept remote joins");
        }

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

    /**
     * Master-driven counterpart to {@link #joinRoom}, for a narrate-only
     * room's roster screen. {@code role} is required when {@link
     * Room#isManualRoles()} and forbidden otherwise, so a room's role
     * assignment is always either fully random or fully manual, never a
     * mix. No broadcast: unlike a remote room, nobody else is watching
     * this room's state before it starts.
     */
    @Transactional
    public ManualPlayerResponse addPlayerManually(String code, String masterToken, String nickname, Role role) {
        Room room = roomService.findRoomForMaster(code, masterToken);

        if (room.isRemoteJoin()) {
            throw new InvalidRulesetException("Room " + code + " accepts remote joins, not manual entry");
        }

        if (room.getStatus() == RoomStatus.STARTED) {
            throw new RoomAlreadyStartedException(code);
        }

        long currentPlayerCount = playerRepository.countByRoomId(room.getId());
        if (currentPlayerCount >= room.getPlayerCount()) {
            throw new RoomFullException(code);
        }

        String normalizedNickname = nickname.toUpperCase();
        if (playerRepository.existsByRoomIdAndNicknameIgnoreCase(room.getId(), normalizedNickname)) {
            throw new NicknameTakenException(normalizedNickname);
        }

        if (room.isManualRoles()) {
            if (role == null) {
                throw new InvalidRulesetException("A role is required when assigning roles manually");
            }
            if (role == Role.GHOST || role == Role.ANGEL) {
                throw new InvalidRulesetException(
                        "Ghost and angel are afterlife-mode roles a dead player becomes, not a starting role");
            }
            long assignedForRole = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                    .filter(p -> p.getRole() == role)
                    .count();
            if (assignedForRole >= room.getRoleCounts().getOrDefault(role, 0)) {
                throw new InvalidRulesetException("No remaining " + role + " slots in this room's ruleset");
            }
        } else if (role != null) {
            throw new InvalidRulesetException("Roles are assigned randomly in this room, not per player");
        }

        Player player = new Player(room, normalizedNickname, SecretTokens.generate());
        if (role != null) {
            roleAssigner.assignRole(player, role);
        }
        playerRepository.save(player);

        return ManualPlayerResponse.from(player);
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

        // Manual dealing has no equivalent to RoleAssigner's guarantee that every
        // declared special role gets filled first — require the whole pool to be
        // dealt rather than risk starting with an unfilled role.
        if (room.isManualRoles() && players.size() < room.getPlayerCount()) {
            throw new NotEnoughPlayersException(code, room.getPlayerCount());
        }

        int specialRoleCount = 0;
        for (Role role : Role.values()) {
            if (role != Role.VILLAGER) {
                specialRoleCount += room.getRoleCounts().getOrDefault(role, 0);
            }
        }
        if (specialRoleCount > players.size()) {
            throw new InvalidRulesetException(
                    "Not enough players joined to fill the configured roles");
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

        boolean visiblyAlive = PlayerResponse.visibleDuring(player, room.getPhase()).alive();
        return new PlayerRoleResponse(player.getRole(), visiblyAlive, player.isMayor());
    }

    private void broadcastRoomState(Room room) {
        List<PlayerResponse> players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .map(PlayerResponse::from)
                .toList();

        RoomStateMessage message = new RoomStateMessage(
                room.getCode(), room.getStatus(), room.getPlayerCount(), players);

        AfterCommit.run(() -> messagingTemplate.convertAndSend("/topic/rooms/" + room.getCode(), message));
    }
}
