package com.ggutim.lupus.service.night;

import java.util.List;

/**
 * What just resolved, for {@link SoloWinCondition}s that care not just
 * whether someone died but how — e.g. the idiot only wins if voted
 * out, not if killed by werewolves. {@link WinConditionCheck}s ignore
 * this; the good/evil headcount doesn't care how anyone died.
 *
 * <p>{@code victimPlayerIds} is empty when nobody died. A vote event
 * is always 0 or 1 victims (single-target voting); a night event can
 * now be 0, 1, or more (the werewolves and, when active, the
 * corrupted judge each kill independently). A killer-guess event is
 * always exactly 1 victim — either the guessed player (correct guess)
 * or the killer himself (wrong guess).
 */
public record RoundEvent(Cause cause, List<Long> victimPlayerIds) {

    public enum Cause {
        NIGHT_KILL,
        VOTE_KILL,
        KILLER_GUESS
    }
}
