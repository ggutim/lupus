package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reveals a dead player's alignment immediately, shown to the master
 * as soon as the gravedigger's {@code SELECT} beat resolves. Targets
 * the dead rather than the living — see {@link #requiresDeadTarget()}
 * — so tonight's still-pending werewolf victim (not yet applied until
 * {@code NIGHT_ACTIONS} ends, see {@link WerewolfKillEffect}) is never
 * a choice here, only players who died in an earlier round or vote.
 */
@Component
class GravediggerInspectEffect implements NightActionEffect {

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
}
