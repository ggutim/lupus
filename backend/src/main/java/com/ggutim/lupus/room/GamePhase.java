package com.ggutim.lupus.room;

/**
 * A single step ("card") the master is guided through once a game
 * starts. Some phases are pure narration (the master just reads the
 * text and clicks next); others require the master to select a player
 * from the board before advancing.
 */
public enum GamePhase {

    /** Roles have just been assigned; tell everyone to check their role. */
    ROLES_ASSIGNED,

    /** Night falls, the village goes to sleep. */
    NIGHT_START,

    /** Narration: werewolves wake up and silently agree on a victim. */
    WEREWOLVES_WAKE_UP,

    /** Master selects who the werewolves killed. */
    WEREWOLVES_SELECT_VICTIM,

    /** Narration: the priest wakes up and picks someone to inspect. */
    PRIEST_WAKE_UP,

    /** Master selects who the priest inspects; reveals good/bad. */
    PRIEST_SELECT_TARGET,

    /** Village wakes up; announce who died overnight. */
    MORNING_REVEAL,

    /** Discussion happens at the table; master just advances when done. */
    DISCUSSION,

    /** Master selects who the village voted to eliminate (or nobody). */
    VOTE_SELECT_TARGET,

    /** A side has won; the game is over. */
    GAME_OVER
}
