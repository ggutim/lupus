package com.ggutim.lupus.service.night;

/**
 * What just resolved, for {@link SoloWinCondition}s that care not just
 * whether someone died but how — e.g. the idiot only wins if voted
 * out, not if killed by werewolves. {@link WinConditionCheck}s ignore
 * this; the good/evil headcount doesn't care how anyone died.
 */
public record RoundEvent(Cause cause, Long victimPlayerId) {

    public enum Cause {
        NIGHT_KILL,
        VOTE_KILL
    }
}
