package com.ggutim.lupus.room;

import com.ggutim.lupus.room.dto.CreateRoomRequest;
import com.ggutim.lupus.room.exception.InvalidRulesetException;
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
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room createRoom(CreateRoomRequest request) {
        Map<Role, Integer> roleCounts = resolveRoleCounts(request);
        String code = generateUniqueCode();
        Room room = new Room(code, request.gameMode(), request.playerCount(), roleCounts);
        return roomRepository.save(room);
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
