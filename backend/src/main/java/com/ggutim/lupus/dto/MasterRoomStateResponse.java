package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.RoomStatus;
import java.util.List;
import java.util.Map;

/**
 * Master-only view of a room's pre-game roster, for the narrate-only
 * setup screen. Reveals each player's role via {@link ManualPlayerResponse}
 * — never send this to anyone but the verified master.
 */
public record MasterRoomStateResponse(
        String code,
        RoomStatus status,
        int playerCount,
        boolean remoteJoin,
        boolean manualRoles,
        Map<Role, Integer> roleCounts,
        List<ManualPlayerResponse> players
) {
}
