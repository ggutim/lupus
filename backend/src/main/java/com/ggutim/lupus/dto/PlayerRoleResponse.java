package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Role;

/**
 * A player's own role, only ever returned to that player (authenticated
 * via their player token) and to the master.
 */
public record PlayerRoleResponse(Role role) {
}
