package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Role;

/**
 * A player's own role and current status, only ever returned to that
 * player (authenticated via their player token) and to the master.
 *
 * <p>{@code alive} is what the player should currently see, which isn't
 * always the raw {@code Player.alive} flag: a player killed overnight is
 * reported alive here until the master moves the room past
 * {@link com.ggutim.lupus.model.GamePhase#MORNING_REVEAL}, so their own
 * screen doesn't spoil the reveal before the master narrates it.
 *
 * <p>{@code mayor} is true when this player currently holds the mayor
 * status (see {@link com.ggutim.lupus.model.Player#isMayor()}) —
 * always visible to the player themselves regardless of whether
 * they've announced it to the table, same as every other role is
 * always visible to its own holder.
 */
public record PlayerRoleResponse(Role role, boolean alive, boolean mayor) {
}
