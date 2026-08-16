package com.ggutim.lupus.service.night;

import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * No-op: the werewolves' kill is resolved separately when the room
 * leaves {@code NIGHT_ACTIONS}, not immediately on selection. See
 * {@link NightActionEffect}.
 */
@Component
class WerewolfKillEffect implements NightActionEffect {

    @Override
    public Role role() {
        return Role.WEREWOLF;
    }

    @Override
    public Optional<Alignment> apply(Player target) {
        return Optional.empty();
    }

    @Override
    public void validateTarget(Player target) {
        if (target.getRole() == Role.WEREWOLF) {
            throw new InvalidGamePhaseException("Werewolves cannot select another werewolf as their victim");
        }
    }
}
