package com.ggutim.lupus.room;

import com.ggutim.lupus.room.dto.CreateRoomRequest;
import com.ggutim.lupus.room.dto.PlayerResponse;
import com.ggutim.lupus.room.dto.RoomStateMessage;
import com.ggutim.lupus.room.exception.InvalidRulesetException;
import com.ggutim.lupus.room.exception.MasterTokenMismatchException;
import com.ggutim.lupus.room.exception.RoomNotFoundException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 4;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;
    private static final int MASTER_TOKEN_BYTES = 32;

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomRepository roomRepository, PlayerRepository playerRepository) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
    }

    @Transactional
    public Room createRoom(CreateRoomRequest request) {
        Map<Role, Integer> roleCounts = resolveRoleCounts(request);
        String code = generateUniqueCode();
        String masterToken = generateMasterToken();
        Room room = new Room(code, masterToken, request.gameMode(), request.playerCount(), roleCounts);
        return roomRepository.save(room);
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

    private String generateMasterToken() {
        byte[] bytes = new byte[MASTER_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
