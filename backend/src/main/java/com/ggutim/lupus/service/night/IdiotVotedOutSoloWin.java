package com.ggutim.lupus.service.night;

import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The idiot wins alone if and only if the village votes them out — a
 * night kill (or any other death) does nothing special, see {@link
 * RoundEvent.Cause}.
 */
@Component
class IdiotVotedOutSoloWin implements SoloWinCondition {

    private final PlayerRepository playerRepository;

    IdiotVotedOutSoloWin(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public Optional<Role> check(Room room, RoundEvent event) {
        if (event.cause() != RoundEvent.Cause.VOTE_KILL) {
            return Optional.empty();
        }
        for (Long victimId : event.victimPlayerIds()) {
            Optional<Role> idiot = playerRepository.findById(victimId)
                    .filter(player -> player.getRole() == Role.IDIOT)
                    .map(Player::getRole);
            if (idiot.isPresent()) {
                return idiot;
            }
        }
        return Optional.empty();
    }
}
