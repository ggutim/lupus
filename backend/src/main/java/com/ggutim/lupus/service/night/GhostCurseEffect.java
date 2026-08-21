package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Afterlife mode only. No immediate reveal — the ghosts' curse only
 * matters later that same night, when the priest inspects a cursed
 * target (see {@link NightEngine#isCursedThisRound}). Targets the
 * living, two at a time — {@link NightEngine#recordSelection} handles
 * the two-slot selection itself, this effect is never asked to apply
 * anything.
 *
 * <p>Only part of the night's sequence once at least one player has
 * died — before that, there are no ghosts to wake up.
 */
@Component
class GhostCurseEffect implements NightActionEffect {

    private final PlayerRepository playerRepository;

    GhostCurseEffect(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public Role role() {
        return Role.GHOST;
    }

    @Override
    public Optional<Alignment> apply(Player target) {
        return Optional.empty();
    }

    @Override
    public boolean isEligibleThisRound(Room room) {
        return !playerRepository.findByRoomIdAndAliveFalseOrderByJoinedAtAsc(room.getId()).isEmpty();
    }
}
