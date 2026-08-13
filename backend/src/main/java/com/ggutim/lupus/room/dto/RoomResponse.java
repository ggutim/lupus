package com.ggutim.lupus.room.dto;

import com.ggutim.lupus.room.GameMode;
import com.ggutim.lupus.room.Role;
import com.ggutim.lupus.room.Room;
import java.util.Map;

/**
 * Public view of a freshly created room, returned to the master.
 */
public record RoomResponse(
        String code,
        GameMode gameMode,
        int playerCount,
        Map<Role, Integer> roleCounts
) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getCode(), room.getGameMode(), room.getPlayerCount(), room.getRoleCounts());
    }
}
