package com.ggutim.lupus.model;

/**
 * A game mode determines which rules and roles are available when
 * configuring a {@link Room}'s ruleset.
 */
public enum GameMode {
    CLASSIC,
    /** Dead players keep playing as ghosts (evil) or angels (good) — see {@link Role#GHOST}/{@link Role#ANGEL}. */
    AFTERLIFE
}
