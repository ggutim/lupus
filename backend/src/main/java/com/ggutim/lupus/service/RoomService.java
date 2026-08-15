package com.ggutim.lupus.service;

import com.ggutim.lupus.config.GameRules;
import com.ggutim.lupus.dto.CreateRoomRequest;
import com.ggutim.lupus.dto.GameRulesResponse;
import com.ggutim.lupus.dto.PlayerResponse;
import com.ggutim.lupus.dto.RoomResponse;
import com.ggutim.lupus.dto.RoomStateMessage;
import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.exception.MasterTokenMismatchException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import com.ggutim.lupus.repository.RoomRepository;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 4;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GameRules gameRules;
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomRepository roomRepository, PlayerRepository playerRepository, GameRules gameRules) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.gameRules = gameRules;
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        validatePlayerCountRange(request.playerCount());
        Map<Role, Integer> roleCounts = resolveRoleCounts(request);
        String code = generateUniqueCode();
        String masterToken = SecretTokens.generate();
        Room room = new Room(code, masterToken, request.gameMode(), request.playerCount(), roleCounts);
        Room saved = roomRepository.save(room);
        return RoomResponse.from(saved, masterToken);
    }

    public GameRulesResponse getGameRules() {
        return GameRulesResponse.from(gameRules);
    }

    public RoomStateMessage getRoomState(String code, String masterToken) {
        Room room = findRoomForMaster(code, masterToken);

        var players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .map(PlayerResponse::from)
                .toList();

        return new RoomStateMessage(room.getCode(), room.getStatus(), room.getPlayerCount(), players);
    }

    /**
     * Looks up a room by code and verifies the caller-provided master
     * token matches, for use by other services guarding master-only
     * actions on the same room (e.g. kicking a player).
     */
    Room findRoomForMaster(String code, String masterToken) {
        Room room = roomRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new RoomNotFoundException(code));

        if (masterToken == null || !room.hasMasterToken(masterToken)) {
            throw new MasterTokenMismatchException(code);
        }

        return room;
    }

    private void validatePlayerCountRange(int playerCount) {
        if (playerCount < gameRules.getMinPlayers() || playerCount > gameRules.getMaxPlayers()) {
            throw new InvalidRulesetException(
                    "playerCount must be between " + gameRules.getMinPlayers()
                            + " and " + gameRules.getMaxPlayers());
        }
    }

    private Map<Role, Integer> resolveRoleCounts(CreateRoomRequest request) {
        int playerCount = request.playerCount();
        int werewolfCount = request.werewolfCount();
        int priestCount = request.priestCount();

        if (werewolfCount + priestCount > playerCount) {
            throw new InvalidRulesetException(
                    "werewolfCount and priestCount cannot exceed playerCount");
        }

        int villagerCount = playerCount - werewolfCount - priestCount;

        Map<Role, Integer> roleCounts = new EnumMap<>(Role.class);
        roleCounts.put(Role.WEREWOLF, werewolfCount);
        roleCounts.put(Role.PRIEST, priestCount);
        roleCounts.put(Role.VILLAGER, villagerCount);
        return roleCounts;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (!roomRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique room code, please retry");
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }
}
