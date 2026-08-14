package com.ggutim.lupus.room.dto;

import com.ggutim.lupus.room.GameMode;
import com.ggutim.lupus.room.Role;
import com.ggutim.lupus.room.Room;
import java.util.Map;

/**
 * View of a freshly created room, returned to the master. Includes the
 * master token exactly once — the client is responsible for storing it
 * to authenticate subsequent master-only actions on this room, since it
 * cannot be retrieved again afterwards.
 */
public record RoomResponse(
        String code,
        String masterToken,
        GameMode gameMode,
        int playerCount,
        Map<Role, Integer> roleCounts
) {

    public static RoomResponse from(Room room, String masterToken) {
        return new RoomResponse(
                room.getCode(), masterToken, room.getGameMode(), room.getPlayerCount(), room.getRoleCounts());
    }
}
