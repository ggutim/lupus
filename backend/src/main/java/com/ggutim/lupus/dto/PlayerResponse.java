package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;

/**
 * Public view of a player, safe to broadcast to everyone in a room.
 * Never includes a player's true role except where it has been made
 * public by an explicit in-game action:
 *
 * <p>{@code revealedRole} is non-null only for the killer once they've
 * used their reveal-and-guess power (right or wrong — either way
 * everyone now knows), or for the original mayor once they've chosen
 * to reveal. It's null for every other role, always, no matter how
 * this player died or what anyone else can infer.
 *
 * <p>{@code mayor} is true only when this player currently holds the
 * mayor status <em>and</em> that fact is itself public — never leaks a
 * still-secret mayor. A successor mayor is public from the moment
 * they're named (the hand-off happens in front of the table), so this
 * flips to {@code true} immediately for them without a separate reveal.
 */
public record PlayerResponse(Long id, String nickname, boolean alive, Role revealedRole, boolean mayor) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getNickname(), player.isAlive(),
                revealedRole(player), publiclyMayor(player));
    }

    /**
     * Like {@link #from}, but delays revealing an overnight kill until
     * the room moves past {@link GamePhase#MORNING_REVEAL}, so a player
     * checking the village overview can't spoil the master's reveal.
     */
    public static PlayerResponse visibleDuring(Player player, GamePhase phase) {
        boolean visiblyAlive = player.isAlive() || phase == GamePhase.MORNING_REVEAL;
        return new PlayerResponse(player.getId(), player.getNickname(), visiblyAlive,
                revealedRole(player), publiclyMayor(player));
    }

    private static Role revealedRole(Player player) {
        if (player.getRole() == Role.KILLER && player.isKillerRevealUsed()) {
            return Role.KILLER;
        }
        if (player.getRole() == Role.MAYOR && player.isMayor() && player.isMayorRevealed()) {
            return Role.MAYOR;
        }
        return null;
    }

    private static boolean publiclyMayor(Player player) {
        return player.isMayor() && player.isMayorRevealed();
    }
}
