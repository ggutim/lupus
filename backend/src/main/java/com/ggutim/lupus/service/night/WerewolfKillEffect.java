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

    @Override
    public boolean isDeferredKill() {
        return true;
    }

    /**
     * A target with an extra life (the survivor) absorbs the
     * werewolves' kill instead of dying — specific to this mechanic,
     * not a general shield: the corrupted judge's kill still goes
     * through unconditionally (see {@link CorruptedJudgeKillEffect}).
     */
    @Override
    public boolean applyDeferredKill(Player target) {
        if (target.getExtraLives() > 0) {
            target.setExtraLives(target.getExtraLives() - 1);
            return false;
        }
        target.kill();
        return true;
    }
}
