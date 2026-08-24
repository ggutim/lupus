package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;

/**
 * A player as seen by the master: includes the assigned role, unlike
 * {@link PlayerResponse} which is safe to broadcast to everyone.
 *
 * <p>{@code originalRole} is set only in afterlife-mode rooms, once
 * this player has died and {@code role} has flipped to {@code GHOST}/
 * {@code ANGEL} — it's what they were before, kept for display only.
 * {@code protectionBlocked} is afterlife-mode only too: permanently
 * true once an angel has protected this player while cursed.
 * {@code killerRevealUsed} is only meaningful for the killer: whether
 * they've already used their once-per-game reveal-and-guess power.
 */
public record MasterPlayerView(Long id, String nickname, boolean alive, Role role, Role originalRole,
        boolean protectionBlocked, boolean killerRevealUsed) {

    public static MasterPlayerView from(Player player) {
        return new MasterPlayerView(player.getId(), player.getNickname(), player.isAlive(), player.getRole(),
                player.getOriginalRole(), player.isProtectionBlocked(), player.isKillerRevealUsed());
    }
}
