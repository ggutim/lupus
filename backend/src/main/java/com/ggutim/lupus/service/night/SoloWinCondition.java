package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import java.util.Optional;

/**
 * Checked in order after any death, alongside but separately from
 * {@link WinConditionCheck} — the first non-empty result ends the game
 * with that role winning alone, instead of a faction. Adding a role
 * with its own solo win condition means adding an implementation here,
 * not editing {@link SoloWinEvaluator} or
 * {@code com.ggutim.lupus.service.GameService}.
 */
public interface SoloWinCondition {

    Optional<Role> check(Room room, RoundEvent event);
}
