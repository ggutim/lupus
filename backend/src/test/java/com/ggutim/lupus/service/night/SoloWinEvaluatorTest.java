package com.ggutim.lupus.service.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
class SoloWinEvaluatorTest {

    @Mock
    private PlayerRepository playerRepository;

    private long nextId = 1;

    private Room room() {
        Room room = new Room("ABCD", "token", GameMode.CLASSIC, 4, Map.of(Role.VILLAGER, 4), true, false);
        setId(room, nextId++);
        return room;
    }

    private Player player(Room room, Role role) {
        Player player = new Player(room, "P" + nextId, "token" + nextId);
        setId(player, nextId++);
        player.setRole(role);
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
    void evaluate_returnsFirstMatchingConditionResult() {
        SoloWinEvaluator evaluator = new SoloWinEvaluator(List.of(
                (room, event) -> Optional.empty(),
                (room, event) -> Optional.of(Role.IDIOT),
                (room, event) -> Optional.of(Role.VILLAGER)));

        assertThat(evaluator.evaluate(room(), new RoundEvent(RoundEvent.Cause.VOTE_KILL, List.of(1L))))
                .contains(Role.IDIOT);
    }

    @Test
    void evaluate_returnsEmptyWhenNoConditionMatches() {
        SoloWinEvaluator evaluator = new SoloWinEvaluator(List.of((room, event) -> Optional.empty()));

        assertThat(evaluator.evaluate(room(), new RoundEvent(RoundEvent.Cause.VOTE_KILL, List.of(1L)))).isEmpty();
    }

    @Test
    void evaluate_idiotVotedOut_winsWhenVoteKillsTheIdiot() {
        Room room = room();
        Player idiot = player(room, Role.IDIOT);
        when(playerRepository.findById(idiot.getId())).thenReturn(Optional.of(idiot));

        SoloWinEvaluator evaluator = new SoloWinEvaluator(List.of(new IdiotVotedOutSoloWin(playerRepository)));

        assertThat(evaluator.evaluate(room, new RoundEvent(RoundEvent.Cause.VOTE_KILL, List.of(idiot.getId()))))
                .contains(Role.IDIOT);
    }

    @Test
    void evaluate_idiotVotedOut_emptyWhenIdiotKilledByWerewolvesInstead() {
        Room room = room();
        Player idiot = player(room, Role.IDIOT);

        SoloWinEvaluator evaluator = new SoloWinEvaluator(List.of(new IdiotVotedOutSoloWin(playerRepository)));

        assertThat(evaluator.evaluate(room, new RoundEvent(RoundEvent.Cause.NIGHT_KILL, List.of(idiot.getId()))))
                .isEmpty();
    }

    @Test
    void evaluate_idiotVotedOut_emptyWhenVotedPlayerIsNotTheIdiot() {
        Room room = room();
        Player villager = player(room, Role.VILLAGER);
        when(playerRepository.findById(villager.getId())).thenReturn(Optional.of(villager));

        SoloWinEvaluator evaluator = new SoloWinEvaluator(List.of(new IdiotVotedOutSoloWin(playerRepository)));

        assertThat(evaluator.evaluate(room, new RoundEvent(RoundEvent.Cause.VOTE_KILL, List.of(villager.getId()))))
                .isEmpty();
    }

    @Test
    void evaluate_idiotVotedOut_emptyWhenNoOneWasVotedOut() {
        Room room = room();

        SoloWinEvaluator evaluator = new SoloWinEvaluator(List.of(new IdiotVotedOutSoloWin(playerRepository)));

        assertThat(evaluator.evaluate(room, new RoundEvent(RoundEvent.Cause.VOTE_KILL, List.of()))).isEmpty();
    }
}
