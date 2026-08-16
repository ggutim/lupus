package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Room;
import java.util.Optional;

/**
 * Checked in order after any death; the first non-empty result ends
 * the game. Adding a role with its own win condition (e.g. a solo
 * role) means adding an implementation here, not editing {@link
 * WinConditionEvaluator}.
 */
public interface WinConditionCheck {

    Optional<Alignment> check(Room room);
}
