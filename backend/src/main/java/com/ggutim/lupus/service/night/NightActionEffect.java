package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import java.util.Optional;

/**
 * A role's immediate night-time effect, applied when its {@code
 * SELECT} beat resolves with a target. Returns a result to show the
 * master right away, if the role has one (e.g. the priest's alignment
 * reveal) — {@link Optional#empty()} otherwise. Implementations are
 * collected by {@link NightEngine} keyed by {@link #role()}, so a new
 * role's night behavior means adding an implementation here, not
 * touching {@link NightEngine}'s control flow.
 *
 * <p>Not every consequence belongs here: the werewolves' kill is
 * deliberately deferred until the room leaves {@code NIGHT_ACTIONS} —
 * see {@link WerewolfKillEffect} — so an already-chosen victim doesn't
 * become ineligible for another role's selection later the same
 * night, and the reveal always lands at {@code MORNING_REVEAL}
 * regardless of night order.
 */
public interface NightActionEffect {

    Role role();

    Optional<Alignment> apply(Player target);

    /**
     * Whether this role selects among dead players (e.g. the
     * gravedigger) rather than the living (the default). Drives both
     * target-eligibility validation and whether a selection can be
     * required at all — a dead-target role with no dead players yet
     * simply has nothing to select, same as a role with no living
     * holder.
     */
    default boolean requiresDeadTarget() {
        return false;
    }

    /**
     * Validates {@code target} is an acceptable choice beyond the
     * generic alive/dead eligibility check (e.g. werewolves refusing
     * to select another werewolf). Throws {@link
     * com.ggutim.lupus.exception.InvalidGamePhaseException} if not.
     * No-op by default.
     */
    default void validateTarget(Player target) {
    }
}
