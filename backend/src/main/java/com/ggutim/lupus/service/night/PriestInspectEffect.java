package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reveals the target's alignment immediately, shown to the master as
 * soon as the priest's {@code SELECT} beat resolves.
 */
@Component
class PriestInspectEffect implements NightActionEffect {

    @Override
    public Role role() {
        return Role.PRIEST;
    }

    @Override
    public Optional<Alignment> apply(Player target) {
        return Optional.of(target.getRole().getAlignment());
    }
}
