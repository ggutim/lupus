package com.ggutim.lupus.service.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.GameMode;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WinConditionEvaluatorTest {

    @Mock
    private PlayerRepository playerRepository;

    private long nextId = 1;

    private Room room() {
        Room room = new Room("ABCD", "token", GameMode.CLASSIC, 4, Map.of(Role.VILLAGER, 4));
        setId(room, nextId++);
        return room;
    }

    private Player player(Room room, Role role, boolean alive) {
        Player player = new Player(room, "P" + nextId, "token" + nextId);
        setId(player, nextId++);
        player.setRole(role);
        if (!alive) {
            player.kill();
        }
        return player;
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void evaluate_returnsFirstMatchingCheckResult() {
        WinConditionEvaluator evaluator = new WinConditionEvaluator(List.of(
                room -> Optional.empty(),
                room -> Optional.of(Alignment.EVIL),
                room -> Optional.of(Alignment.GOOD)));

        assertThat(evaluator.evaluate(room())).contains(Alignment.EVIL);
    }

    @Test
    void evaluate_returnsEmptyWhenNoCheckMatches() {
        WinConditionEvaluator evaluator = new WinConditionEvaluator(List.of(room -> Optional.empty()));

        assertThat(evaluator.evaluate(room())).isEmpty();
    }

    @Test
    void evaluate_goodEvilHeadcount_goodWinsWhenNoEvilAlive() {
        Room room = room();
        Player v1 = player(room, Role.VILLAGER, true);
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId())).thenReturn(List.of(v1));

        WinConditionEvaluator evaluator = new WinConditionEvaluator(
                List.of(new GoodEvilHeadcountWinCondition(playerRepository)));

        assertThat(evaluator.evaluate(room)).contains(Alignment.GOOD);
    }

    @Test
    void evaluate_goodEvilHeadcount_evilWinsWhenAtLeastAsNumerousAsGood() {
        Room room = room();
        Player wolf = player(room, Role.WEREWOLF, true);
        Player v1 = player(room, Role.VILLAGER, true);
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()))
                .thenReturn(List.of(wolf, v1));

        WinConditionEvaluator evaluator = new WinConditionEvaluator(
                List.of(new GoodEvilHeadcountWinCondition(playerRepository)));

        assertThat(evaluator.evaluate(room)).contains(Alignment.EVIL);
    }

    @Test
    void evaluate_goodEvilHeadcount_emptyWhenGoodOutnumbersEvil() {
        Room room = room();
        Player wolf = player(room, Role.WEREWOLF, true);
        Player v1 = player(room, Role.VILLAGER, true);
        Player v2 = player(room, Role.VILLAGER, true);
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()))
                .thenReturn(List.of(wolf, v1, v2));

        WinConditionEvaluator evaluator = new WinConditionEvaluator(
                List.of(new GoodEvilHeadcountWinCondition(playerRepository)));

        assertThat(evaluator.evaluate(room)).isEmpty();
    }
}
