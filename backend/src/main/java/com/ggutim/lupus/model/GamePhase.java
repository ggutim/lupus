package com.ggutim.lupus.model;

/**
 * A single step ("card") the master is guided through once a game
 * starts. This is the fixed skeleton that never varies by ruleset —
 * the per-role night beats (which role wakes up, in what order) are
 * data-driven via {@link Room#getCurrentNightRole()} and
 * {@link NightStepKind}, not additional enum values here, so adding a
 * role never means adding a phase.
 */
public enum GamePhase {

    /** Roles have just been assigned; tell everyone to check their role. */
    ROLES_ASSIGNED,

    /** Night falls, the village goes to sleep. */
    NIGHT_START,

    /**
     * Walking through each configured role's night turn in order. See
     * {@link Room#getCurrentNightRole()} and
     * {@link Room#getCurrentNightStepKind()} for which role/beat is
     * currently active.
     */
    NIGHT_ACTIONS,

    /** Village wakes up; announce who died overnight. */
    MORNING_REVEAL,

    /** Discussion happens at the table; master just advances when done. */
    DISCUSSION,

    /** Master selects who the village voted to eliminate (or nobody). */
    VOTE_SELECT_TARGET,

    /** A side has won; the game is over. */
    GAME_OVER
}
