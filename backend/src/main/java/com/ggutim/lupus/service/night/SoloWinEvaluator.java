package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Runs the configured {@link SoloWinCondition}s in order and returns
 * the first role that wins alone, if any.
 */
@Service
public class SoloWinEvaluator {

    private final List<SoloWinCondition> conditions;

    SoloWinEvaluator(List<SoloWinCondition> conditions) {
        this.conditions = conditions;
    }

    public Optional<Role> evaluate(Room room, RoundEvent event) {
        for (SoloWinCondition condition : conditions) {
            Optional<Role> result = condition.check(room, event);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
