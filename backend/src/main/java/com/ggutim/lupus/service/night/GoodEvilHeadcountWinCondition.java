package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Today's only win condition: good wins once no evil players remain
 * alive, evil wins once it's at least as numerous as good.
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

        long aliveEvil = alivePlayers.stream()
                .filter(player -> player.getRole().getAlignment() == Alignment.EVIL)
                .count();
        long aliveGood = alivePlayers.size() - aliveEvil;

        if (aliveEvil == 0) {
            return Optional.of(Alignment.GOOD);
        }
        if (aliveEvil >= aliveGood) {
            return Optional.of(Alignment.EVIL);
        }
        return Optional.empty();
    }
}
