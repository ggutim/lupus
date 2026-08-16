package com.ggutim.lupus.service.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.GameMode;
import com.ggutim.lupus.model.NightAction;
import com.ggutim.lupus.model.NightStepKind;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.NightActionRepository;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NightEngineTest {

    private static final String CODE = "ABCD";

    @Mock
    private PlayerRepository playerRepository;

    private long nextId = 1;

    /** Real effect implementations (not mocks) so these tests exercise the actual per-role wiring. */
    private NightEngine nightEngine() {
        Map<String, NightAction> store = new HashMap<>();
        NightActionRepository nightActionRepository = mock(NightActionRepository.class);
        when(nightActionRepository.save(any())).thenAnswer(invocation -> {
            NightAction action = invocation.getArgument(0);
            store.put(key(action.getRoom().getId(), action.getRoundNumber(), action.getRole()), action);
            return action;
        });
        when(nightActionRepository.findByRoomIdAndRoundNumberAndRole(any(), anyInt(), any())).thenAnswer(invocation ->
                Optional.ofNullable(store.get(key(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)))));

        List<NightActionEffect> effects =
                List.of(new WerewolfKillEffect(), new PriestInspectEffect(), new GravediggerInspectEffect());
        return new NightEngine(nightActionRepository, playerRepository, effects);
    }

    private String key(Long roomId, int round, Role role) {
        return roomId + ":" + round + ":" + role;
    }

    private Room room(int werewolfCount, int priestCount, int gravediggerCount) {
        Room room = new Room(CODE, "token", GameMode.CLASSIC, 10, Map.of(
                Role.WEREWOLF, werewolfCount, Role.PRIEST, priestCount,
                Role.GRAVEDIGGER, gravediggerCount, Role.VILLAGER, 0));
        setId(room, nextId++);
        return room;
    }

    private Player player(Room room, String nickname, Role role, boolean alive) {
        Player player = new Player(room, nickname, nickname.toLowerCase() + "-token");
        setId(player, nextId++);
        player.setRole(role);
        if (!alive) {
            player.kill();
        }
        return player;
    }

    private void mockPlayers(Room room, List<Player> players) {
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()))
                .thenAnswer(invocation -> players.stream().filter(Player::isAlive).toList());
        when(playerRepository.findByRoomIdAndAliveFalseOrderByJoinedAtAsc(room.getId()))
                .thenAnswer(invocation -> players.stream().filter(p -> !p.isAlive()).toList());
        for (Player player : players) {
            when(playerRepository.findById(player.getId())).thenReturn(Optional.of(player));
        }
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

    // ---------- nextRole ----------

    @Test
    void nextRole_returnsFirstConfiguredRoleWhenAfterIsNull() {
        Room room = room(1, 1, 1);

        assertThat(nightEngine().nextRole(room, null)).contains(Role.WEREWOLF);
    }

    @Test
    void nextRole_skipsUnconfiguredRoles() {
        Room room = room(1, 0, 1);

        assertThat(nightEngine().nextRole(room, Role.WEREWOLF)).contains(Role.GRAVEDIGGER);
    }

    @Test
    void nextRole_returnsGravediggerAfterPriestWhenBothConfigured() {
        Room room = room(1, 1, 1);

        assertThat(nightEngine().nextRole(room, Role.PRIEST)).contains(Role.GRAVEDIGGER);
    }

    @Test
    void nextRole_returnsEmptyWhenNoMoreRolesConfigured() {
        Room room = room(1, 0, 0);

        assertThat(nightEngine().nextRole(room, Role.WEREWOLF)).isEmpty();
    }

    // ---------- requireSelectionIfNeeded ----------

    @Test
    void requireSelectionIfNeeded_throwsWhenHolderAliveAndNoSelectionMade() {
        Room room = room(1, 0, 0);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        mockPlayers(room, List.of(wolf));

        assertThatThrownBy(() -> nightEngine().requireSelectionIfNeeded(room, Role.WEREWOLF))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void requireSelectionIfNeeded_doesNotThrowWhenHolderDead() {
        Room room = room(1, 1, 0);
        Player deadPriest = player(room, "PRIEST", Role.PRIEST, false);
        mockPlayers(room, List.of(deadPriest));

        nightEngine().requireSelectionIfNeeded(room, Role.PRIEST);
    }

    @Test
    void requireSelectionIfNeeded_doesNotThrowWhenSelectionWasMade() {
        Room room = room(1, 0, 0);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(wolf, v1));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, v1.getId());

        engine.requireSelectionIfNeeded(room, Role.WEREWOLF);
    }

    @Test
    void requireSelectionIfNeeded_doesNotThrowForDeadTargetRoleWithNoDeadPlayersYet() {
        Room room = room(0, 0, 1);
        Player gravedigger = player(room, "GRAVE", Role.GRAVEDIGGER, true);
        mockPlayers(room, List.of(gravedigger));

        nightEngine().requireSelectionIfNeeded(room, Role.GRAVEDIGGER);
    }

    @Test
    void requireSelectionIfNeeded_throwsForDeadTargetRoleWhenDeadPlayerExistsAndNoSelectionMade() {
        Room room = room(0, 0, 1);
        Player gravedigger = player(room, "GRAVE", Role.GRAVEDIGGER, true);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockPlayers(room, List.of(gravedigger, deadVillager));

        assertThatThrownBy(() -> nightEngine().requireSelectionIfNeeded(room, Role.GRAVEDIGGER))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    // ---------- recordSelection ----------

    @Test
    void recordSelection_rejectsDeadTargetForAliveTargetRole() {
        Room room = room(1, 0, 0);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockPlayers(room, List.of(deadVillager));

        assertThatThrownBy(() -> nightEngine().recordSelection(room, Role.WEREWOLF, deadVillager.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void recordSelection_rejectsAliveTargetForDeadTargetRole() {
        Room room = room(0, 0, 1);
        Player aliveVillager = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(aliveVillager));

        assertThatThrownBy(() -> nightEngine().recordSelection(room, Role.GRAVEDIGGER, aliveVillager.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void recordSelection_rejectsUnknownPlayer() {
        Room room = room(1, 0, 0);
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nightEngine().recordSelection(room, Role.WEREWOLF, 999L))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void recordSelection_rejectsAnotherWerewolfAsWerewolfTarget() {
        Room room = room(2, 0, 0);
        Player otherWolf = player(room, "WOLF2", Role.WEREWOLF, true);
        mockPlayers(room, List.of(otherWolf));

        assertThatThrownBy(() -> nightEngine().recordSelection(room, Role.WEREWOLF, otherWolf.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void recordSelection_storesTarget() {
        Room room = room(1, 0, 0);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(target));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, target.getId());

        assertThat(engine.findAction(room, Role.WEREWOLF))
                .get().extracting(NightAction::getTargetPlayerId).isEqualTo(target.getId());
    }

    @Test
    void recordSelection_priestRevealsGoodAlignmentForVillager() {
        Room room = room(0, 1, 0);
        Player target = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(target));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.PRIEST, target.getId());

        assertThat(engine.findAction(room, Role.PRIEST))
                .get().extracting(NightAction::getResultAlignment).isEqualTo(Alignment.GOOD);
    }

    @Test
    void recordSelection_priestRevealsEvilAlignmentForWerewolf() {
        Room room = room(0, 1, 0);
        Player target = player(room, "WOLF", Role.WEREWOLF, true);
        mockPlayers(room, List.of(target));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.PRIEST, target.getId());

        assertThat(engine.findAction(room, Role.PRIEST))
                .get().extracting(NightAction::getResultAlignment).isEqualTo(Alignment.EVIL);
    }

    @Test
    void recordSelection_gravediggerRevealsGoodAlignmentForDeadVillager() {
        Room room = room(0, 0, 1);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockPlayers(room, List.of(deadVillager));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GRAVEDIGGER, deadVillager.getId());

        assertThat(engine.findAction(room, Role.GRAVEDIGGER))
                .get().extracting(NightAction::getResultAlignment).isEqualTo(Alignment.GOOD);
    }

    @Test
    void recordSelection_gravediggerRevealsEvilAlignmentForDeadWerewolf() {
        Room room = room(0, 0, 1);
        Player deadWolf = player(room, "DEADWOLF", Role.WEREWOLF, false);
        mockPlayers(room, List.of(deadWolf));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GRAVEDIGGER, deadWolf.getId());

        assertThat(engine.findAction(room, Role.GRAVEDIGGER))
                .get().extracting(NightAction::getResultAlignment).isEqualTo(Alignment.EVIL);
    }

    @Test
    void recordSelection_overwritesPreviousSelectionOnReselect() {
        Room room = room(1, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1, v2));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, v1.getId());
        engine.recordSelection(room, Role.WEREWOLF, v2.getId());

        assertThat(engine.findAction(room, Role.WEREWOLF))
                .get().extracting(NightAction::getTargetPlayerId).isEqualTo(v2.getId());
    }

    // ---------- resolveDeferredKillAndClearState ----------

    @Test
    void resolveDeferredKillAndClearState_killsRecordedWerewolfVictim() {
        Room room = room(1, 0, 0);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(victim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, victim.getId());
        engine.resolveDeferredKillAndClearState(room);

        assertThat(victim.isAlive()).isFalse();
    }

    @Test
    void resolveDeferredKillAndClearState_noOpWhenNoVictimRecorded() {
        Room room = room(1, 0, 0);

        nightEngine().resolveDeferredKillAndClearState(room);
    }

    @Test
    void resolveDeferredKillAndClearState_clearsCurrentNightRoleAndStepKind() {
        Room room = room(1, 0, 0);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);

        nightEngine().resolveDeferredKillAndClearState(room);

        assertThat(room.getCurrentNightRole()).isNull();
        assertThat(room.getCurrentNightStepKind()).isNull();
    }

    // ---------- findAction ----------

    @Test
    void findAction_returnsEmptyWhenNoneRecorded() {
        Room room = room(1, 0, 0);

        assertThat(nightEngine().findAction(room, Role.WEREWOLF)).isEmpty();
    }
}
