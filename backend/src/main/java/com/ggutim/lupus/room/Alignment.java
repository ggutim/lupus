package com.ggutim.lupus.room;

/**
 * Which side a {@link Role} plays for. Used for the priest's "good or
 * evil" check and for win-condition checks, so both stay generic as more
 * roles are added later.
 */
public enum Alignment {
    GOOD,
    EVIL
}
