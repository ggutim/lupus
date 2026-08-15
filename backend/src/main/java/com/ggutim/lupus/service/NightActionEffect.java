package com.ggutim.lupus.service;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import java.util.Optional;

/**
 * A role's immediate night-time effect, applied when its {@code
 * SELECT} beat resolves with a target. Returns a result to show the
 * master right away, if the role has one (e.g. the priest's alignment
 * reveal) — {@link Optional#empty()} otherwise. Implementations are
 * collected by {@link GameService} keyed by {@link #role()}, so a new
 * role's night behavior means adding an implementation here, not
 * touching {@code GameService}'s control flow.
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
}
