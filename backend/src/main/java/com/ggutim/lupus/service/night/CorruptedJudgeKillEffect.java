package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Only part of the night's sequence following a day where nobody was
 * voted out (see {@link Room#isNoOneVotedOutPreviousDay()}); when
 * active, may kill any living player, including a werewolf — no
 * {@link #validateTarget} restriction, unlike {@link WerewolfKillEffect}.
 * The kill is deferred, like the werewolves', and optional: {@link
 * #requiresSelection()} is {@code false}.
 */
@Component
class CorruptedJudgeKillEffect implements NightActionEffect {

    @Override
    public Role role() {
        return Role.CORRUPTED_JUDGE;
    }

    @Override
    public Optional<Alignment> apply(Player target) {
        return Optional.empty();
    }

    @Override
    public boolean isDeferredKill() {
        return true;
    }

    @Override
    public boolean requiresSelection() {
        return false;
    }

    @Override
    public boolean isEligibleThisRound(Room room) {
        return room.isNoOneVotedOutPreviousDay();
    }
}
