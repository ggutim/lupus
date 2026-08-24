package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Today's only win condition: good wins once no evil players remain
 * alive at all; evil wins once the werewolves specifically are at
 * least as numerous as good — an evil-aligned but non-werewolf role
 * (the corrupted judge) is still a threat that blocks a good win, but
 * doesn't count toward the wolves' numeric parity with the village.
 */
@Component
class GoodEvilHeadcountWinCondition implements WinConditionCheck {

    private final PlayerRepository playerRepository;

    GoodEvilHeadcountWinCondition(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public Optional<Alignment> check(Room room) {
        List<Player> alivePlayers = playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId());

        long aliveGood = alivePlayers.stream()
                .filter(player -> player.getRole().getAlignment() == Alignment.GOOD)
                .count();
        long aliveEvil = alivePlayers.size() - aliveGood;
        long aliveWerewolves = alivePlayers.stream()
                .filter(player -> player.getRole() == Role.WEREWOLF)
                .count();

        if (aliveEvil == 0) {
            return Optional.of(Alignment.GOOD);
        }
        if (aliveWerewolves >= aliveGood) {
            return Optional.of(Alignment.EVIL);
        }
        return Optional.empty();
    }
}
