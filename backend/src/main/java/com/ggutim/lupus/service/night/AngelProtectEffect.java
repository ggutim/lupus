package com.ggutim.lupus.service.night;

import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Afterlife mode only. No immediate reveal — the protection only
 * matters when the werewolves' kill is resolved (see {@link
 * NightEngine#resolveDeferredKillsAndClearState}), which skips a
 * protected target for the werewolves specifically, not for the
 * corrupted judge.
 *
 * <p>Only part of the night's sequence once at least one player has
 * died — before that, there are no angels to wake up.
 */
@Component
class AngelProtectEffect implements NightActionEffect {

    private final PlayerRepository playerRepository;

    AngelProtectEffect(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public Role role() {
        return Role.ANGEL;
    }

    @Override
    public Optional<Alignment> apply(Player target) {
        return Optional.empty();
    }

    /**
     * A player who was once protected while cursed can never be
     * protected again (see {@link NightEngine#recordSelection}, which
     * sets the flag) — every other target is fair game, repeatedly.
     */
    @Override
    public void validateTarget(Player target) {
        if (target.isProtectionBlocked()) {
            throw new InvalidGamePhaseException(
                    "Player " + target.getId() + " can no longer be protected by angels");
        }
    }

    @Override
    public boolean isEligibleThisRound(Room room) {
        return !playerRepository.findByRoomIdAndAliveFalseOrderByJoinedAtAsc(room.getId()).isEmpty();
    }
}
