package com.ggutim.lupus.model;

/**
 * A role that can be assigned to a player. The set of available roles is
 * currently fixed, but is expected to grow over time.
 */
public enum Role {
    VILLAGER(Alignment.GOOD),
    WEREWOLF(Alignment.EVIL),
    PRIEST(Alignment.GOOD),
    GRAVEDIGGER(Alignment.GOOD),
    IDIOT(Alignment.GOOD),
    CORRUPTED_JUDGE(Alignment.EVIL),
    /** A basic villager with one extra life — see {@link #getStartingExtraLives()}. */
    SURVIVOR(Alignment.GOOD, 1),
    GUARDIAN(Alignment.GOOD),
    KILLER(Alignment.EVIL),
    MAYOR(Alignment.GOOD),
    /** Afterlife mode only — what a dead evil player becomes. Never assignable at room creation. */
    GHOST(Alignment.EVIL),
    /** Afterlife mode only — what a dead good player becomes. Never assignable at room creation. */
    ANGEL(Alignment.GOOD);

    private final Alignment alignment;
    private final int startingExtraLives;

    Role(Alignment alignment) {
        this(alignment, 0);
    }

    Role(Alignment alignment, int startingExtraLives) {
        this.alignment = alignment;
        this.startingExtraLives = startingExtraLives;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    /**
     * How many extra lives a player of this role starts with, beyond
     * the usual single life every player has — {@code 0} for every
     * role except the survivor. See {@link com.ggutim.lupus.model.Player#getExtraLives()}.
     */
    public int getStartingExtraLives() {
        return startingExtraLives;
    }
}
