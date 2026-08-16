package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Runs the configured {@link WinConditionCheck}s in order and returns
 * the first non-empty result, if any.
 */
@Service
public class WinConditionEvaluator {

    private final List<WinConditionCheck> checks;

    WinConditionEvaluator(List<WinConditionCheck> checks) {
        this.checks = checks;
    }

    public Optional<Alignment> evaluate(Room room) {
        for (WinConditionCheck check : checks) {
            Optional<Alignment> result = check.check(room);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
