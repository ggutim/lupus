package com.ggutim.lupus.room.dto;

import com.ggutim.lupus.room.Role;

/**
 * A player's own role, only ever returned to that player (authenticated
 * via their player token) and to the master.
 */
public record PlayerRoleResponse(Role role) {
}
