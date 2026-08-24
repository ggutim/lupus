package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reveals a dead player's alignment immediately, shown to the master
 * as soon as the gravedigger's {@code SELECT} beat resolves. Targets
 * the dead rather than the living — see {@link #requiresDeadTarget()}
 * — so tonight's still-pending werewolf victim (not yet applied until
 * {@code NIGHT_ACTIONS} ends, see {@link WerewolfKillEffect}) is never
 * a choice here, only players who died in an earlier round or vote.
 *
 * <p>Only part of the night's sequence once at least one player has
 * died — before that, there's nobody to dig up, so the turn isn't
 * narrated at all (mirrors {@link GhostCurseEffect}).
 */
@Component
class GravediggerInspectEffect implements NightActionEffect {

    private final PlayerRepository playerRepository;

    GravediggerInspectEffect(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public Role role() {
        return Role.GRAVEDIGGER;
    }

    @Override
    public boolean requiresDeadTarget() {
        return true;
    }

    @Override
    public Optional<Alignment> apply(Player target) {
        return Optional.of(target.getRole().getAlignment());
    }

    @Override
    public boolean isEligibleThisRound(Room room) {
        return !playerRepository.findByRoomIdAndAliveFalseOrderByJoinedAtAsc(room.getId()).isEmpty();
    }
}
