package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * No immediate reveal — the guardian's protection only matters later
 * that same night, when the werewolves' kill is resolved (see {@link
 * NightEngine#resolveDeferredKillsAndClearState}). Targets the living,
 * including the guardian himself. The "can't protect the same player
 * two nights in a row" rule needs the previous round's {@link
 * com.ggutim.lupus.model.NightAction}, which this effect has no access
 * to — enforced by {@link NightEngine#recordSelection} instead, the
 * same way the angel's curse-block is.
 */
@Component
class GuardianProtectEffect implements NightActionEffect {

    @Override
    public Role role() {
        return Role.GUARDIAN;
    }

    @Override
    public Optional<Alignment> apply(Player target) {
        return Optional.empty();
    }
}
