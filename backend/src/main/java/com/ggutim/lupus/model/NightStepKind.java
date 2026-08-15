package com.ggutim.lupus.model;

/**
 * Which beat of a role's night turn is active while {@link Room} is in
 * {@link GamePhase#NIGHT_ACTIONS}.
 */
public enum NightStepKind {

    /** Narration: the role wakes up (or is narrated as waking up, even if its last holder is dead). */
    WAKE_UP,

    /** Master selects the role's target, if a living player still holds the role. */
    SELECT
}
