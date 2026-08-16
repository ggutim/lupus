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
    CORRUPTED_JUDGE(Alignment.EVIL);

    private final Alignment alignment;

    Role(Alignment alignment) {
        this.alignment = alignment;
    }

    public Alignment getAlignment() {
        return alignment;
    }
}
