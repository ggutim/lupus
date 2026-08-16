package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
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

    /**
     * Whether this role's kill is resolved once the room leaves
     * {@code NIGHT_ACTIONS} rather than immediately on selection (see
     * {@link WerewolfKillEffect}) — and therefore counted among the
     * round's reported deaths. {@code false} for non-kill effects
     * (e.g. the priest's reveal).
     */
    default boolean isDeferredKill() {
        return false;
    }

    /**
     * Whether the master must make a selection for this role before
     * advancing, whenever it has a living holder and an eligible
     * target. {@code true} for every role today; a role whose power
     * is optional (e.g. the corrupted judge "can decide if kill
     * somebody or not") overrides this to {@code false}.
     */
    default boolean requiresSelection() {
        return true;
    }

    /**
     * Whether this role is even part of tonight's sequence at all —
     * distinct from having a living holder or an eligible target.
     * {@code true} for every role today; a role whose turn only
     * exists on some nights (e.g. the corrupted judge, gated on
     * whether anyone was voted out the previous day — a fact already
     * public at the table, unlike a role's living/dead status) checks
     * {@code room} here.
     */
    default boolean isEligibleThisRound(Room room) {
        return true;
    }
}
