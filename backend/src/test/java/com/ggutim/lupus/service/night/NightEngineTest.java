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

        List<NightActionEffect> effects = List.of(new WerewolfKillEffect(), new PriestInspectEffect(),
                new GravediggerInspectEffect(playerRepository), new CorruptedJudgeKillEffect(),
                new GhostCurseEffect(playerRepository), new AngelProtectEffect(playerRepository),
                new GuardianProtectEffect());
        return new NightEngine(nightActionRepository, playerRepository, effects);
    }

    private String key(Long roomId, int round, Role role) {
        return roomId + ":" + round + ":" + role;
    }

    private Room room(int werewolfCount, int priestCount, int gravediggerCount) {
        return room(werewolfCount, priestCount, gravediggerCount, 0);
    }

    private Room room(int werewolfCount, int priestCount, int gravediggerCount, int corruptedJudgeCount) {
        Room room = new Room(CODE, "token", GameMode.CLASSIC, 10, Map.of(
                Role.WEREWOLF, werewolfCount, Role.PRIEST, priestCount,
                Role.GRAVEDIGGER, gravediggerCount, Role.CORRUPTED_JUDGE, corruptedJudgeCount, Role.VILLAGER, 0),
                true, false);
        setId(room, nextId++);
        return room;
    }

    private Room afterlifeRoom(int werewolfCount, int priestCount, int gravediggerCount, int corruptedJudgeCount) {
        Room room = new Room(CODE, "token", GameMode.AFTERLIFE, 10, Map.of(
                Role.WEREWOLF, werewolfCount, Role.PRIEST, priestCount,
                Role.GRAVEDIGGER, gravediggerCount, Role.CORRUPTED_JUDGE, corruptedJudgeCount, Role.VILLAGER, 0),
                true, false);
        setId(room, nextId++);
        return room;
    }

    private Room roomWithGuardian(int werewolfCount, int priestCount, int guardianCount) {
        Room room = new Room(CODE, "token", GameMode.CLASSIC, 10, Map.of(
                Role.WEREWOLF, werewolfCount, Role.PRIEST, priestCount, Role.GUARDIAN, guardianCount,
                Role.VILLAGER, 0),
                true, false);
        setId(room, nextId++);
        return room;
    }

    private Room afterlifeRoomWithGuardian(int werewolfCount, int priestCount, int guardianCount) {
        Room room = new Room(CODE, "token", GameMode.AFTERLIFE, 10, Map.of(
                Role.WEREWOLF, werewolfCount, Role.PRIEST, priestCount, Role.GUARDIAN, guardianCount,
                Role.VILLAGER, 0),
                true, false);
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
            when(playerRepository.findByIdAndRoomId(player.getId(), room.getId())).thenReturn(Optional.of(player));
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
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockPlayers(room, List.of(deadVillager));

        NightEngine engine = nightEngine();
        assertThat(engine.nextRole(room, null)).contains(Role.GRAVEDIGGER);
        assertThat(engine.nextRole(room, Role.GRAVEDIGGER)).contains(Role.WEREWOLF);
        assertThat(engine.nextRole(room, Role.WEREWOLF)).isEmpty();
    }

    @Test
    void nextRole_returnsPriestAfterWerewolfWhenBothConfigured() {
        Room room = room(1, 1, 1);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockPlayers(room, List.of(deadVillager));

        assertThat(nightEngine().nextRole(room, Role.WEREWOLF)).contains(Role.PRIEST);
    }

    @Test
    void nextRole_skipsGravediggerWhenNoOneHasDiedYet() {
        Room room = room(1, 0, 1);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        mockPlayers(room, List.of(wolf));

        assertThat(nightEngine().nextRole(room, null)).contains(Role.WEREWOLF);
    }

    @Test
    void nextRole_returnsEmptyWhenNoMoreRolesConfigured() {
        Room room = room(1, 0, 0);

        assertThat(nightEngine().nextRole(room, Role.WEREWOLF)).isEmpty();
    }

    @Test
    void nextRole_skipsCorruptedJudgeWhenPreviousDayHadAnElimination() {
        Room room = room(1, 0, 0, 1);
        room.setNoOneVotedOutPreviousDay(false);

        assertThat(nightEngine().nextRole(room, null)).contains(Role.WEREWOLF);
    }

    @Test
    void nextRole_includesCorruptedJudgeWhenPreviousDayHadNoElimination() {
        Room room = room(1, 0, 0, 1);
        room.setNoOneVotedOutPreviousDay(true);

        assertThat(nightEngine().nextRole(room, null)).contains(Role.CORRUPTED_JUDGE);
    }

    @Test
    void nextRole_classicOrderIsJudgeThenGravediggerThenWerewolfThenPriest() {
        Room room = room(1, 1, 1, 1);
        room.setNoOneVotedOutPreviousDay(true);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockPlayers(room, List.of(deadVillager));

        NightEngine engine = nightEngine();
        assertThat(engine.nextRole(room, null)).contains(Role.CORRUPTED_JUDGE);
        assertThat(engine.nextRole(room, Role.CORRUPTED_JUDGE)).contains(Role.GRAVEDIGGER);
        assertThat(engine.nextRole(room, Role.GRAVEDIGGER)).contains(Role.WEREWOLF);
        assertThat(engine.nextRole(room, Role.WEREWOLF)).contains(Role.PRIEST);
        assertThat(engine.nextRole(room, Role.PRIEST)).isEmpty();
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

    @Test
    void requireSelectionIfNeeded_neverThrowsForCorruptedJudgeEvenWhenHolderAliveAndTargetExists() {
        Room room = room(0, 0, 0, 1);
        room.setNoOneVotedOutPreviousDay(true);
        Player judge = player(room, "JUDGE", Role.CORRUPTED_JUDGE, true);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(judge, v1));

        nightEngine().requireSelectionIfNeeded(room, Role.CORRUPTED_JUDGE);
    }

    @Test
    void requireSelectionIfNeeded_throwsWhenSoleHolderIsPendingWerewolfKillTargetAndNoSelectionMade() {
        // A werewolf kill only takes effect the following day, so the priest
        // is still fully able (and required) to act on the same night they've
        // been targeted — the pending target must not be excluded as a holder.
        Room room = room(1, 1, 0);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player priest = player(room, "PRIEST", Role.PRIEST, true);
        mockPlayers(room, List.of(wolf, priest));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, priest.getId());

        assertThatThrownBy(() -> engine.requireSelectionIfNeeded(room, Role.PRIEST))
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
    void recordSelection_allowsCorruptedJudgeToTargetAWerewolf() {
        Room room = room(1, 0, 0, 1);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        mockPlayers(room, List.of(wolf));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.CORRUPTED_JUDGE, wolf.getId());

        assertThat(engine.findAction(room, Role.CORRUPTED_JUDGE))
                .get().extracting(NightAction::getTargetPlayerId).isEqualTo(wolf.getId());
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

    // ---------- resolveDeferredKillsAndClearState ----------

    @Test
    void resolveDeferredKillsAndClearState_killsRecordedWerewolfVictimAndReturnsItsId() {
        Room room = room(1, 0, 0);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(victim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, victim.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(victim.isAlive()).isFalse();
        assertThat(victimIds).containsExactly(victim.getId());
    }

    @Test
    void resolveDeferredKillsAndClearState_survivorAbsorbsWerewolfKillAndKeepsNoVictimReported() {
        Room room = room(1, 0, 0);
        Player survivor = player(room, "S1", Role.SURVIVOR, true);
        survivor.setExtraLives(1);
        mockPlayers(room, List.of(survivor));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, survivor.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(survivor.isAlive()).isTrue();
        assertThat(survivor.getExtraLives()).isZero();
        assertThat(victimIds).isEmpty();
    }

    @Test
    void resolveDeferredKillsAndClearState_survivorDiesOnWerewolfKillWithNoLivesLeft() {
        Room room = room(1, 0, 0);
        Player survivor = player(room, "S1", Role.SURVIVOR, true);
        survivor.setExtraLives(0);
        mockPlayers(room, List.of(survivor));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, survivor.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(survivor.isAlive()).isFalse();
        assertThat(victimIds).containsExactly(survivor.getId());
    }

    @Test
    void resolveDeferredKillsAndClearState_corruptedJudgeKillIgnoresSurvivorExtraLife() {
        Room room = room(0, 0, 0, 1);
        Player survivor = player(room, "S1", Role.SURVIVOR, true);
        survivor.setExtraLives(1);
        mockPlayers(room, List.of(survivor));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.CORRUPTED_JUDGE, survivor.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(survivor.isAlive()).isFalse();
        assertThat(survivor.getExtraLives()).isEqualTo(1);
        assertThat(victimIds).containsExactly(survivor.getId());
    }

    @Test
    void findLastNightVictims_stillReturnsTargetEvenWhenTheKillWasAbsorbed() {
        Room room = room(1, 0, 0);
        Player survivor = player(room, "S1", Role.SURVIVOR, true);
        survivor.setExtraLives(1);
        mockPlayers(room, List.of(survivor));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, survivor.getId());
        engine.resolveDeferredKillsAndClearState(room);

        assertThat(engine.findLastNightVictims(room)).containsExactly(survivor.getId());
        assertThat(survivor.isAlive()).isTrue();
    }

    @Test
    void resolveDeferredKillsAndClearState_returnsEmptyWhenNoVictimRecorded() {
        Room room = room(1, 0, 0);

        assertThat(nightEngine().resolveDeferredKillsAndClearState(room)).isEmpty();
    }

    @Test
    void resolveDeferredKillsAndClearState_clearsCurrentNightRoleAndStepKind() {
        Room room = room(1, 0, 0);
        room.setCurrentNightRole(Role.WEREWOLF);
        room.setCurrentNightStepKind(NightStepKind.SELECT);

        nightEngine().resolveDeferredKillsAndClearState(room);

        assertThat(room.getCurrentNightRole()).isNull();
        assertThat(room.getCurrentNightStepKind()).isNull();
    }

    @Test
    void resolveDeferredKillsAndClearState_returnsBothVictimsWhenWerewolfAndJudgeTargetDifferentPlayers() {
        Room room = room(1, 0, 0, 1);
        Player wolfVictim = player(room, "V1", Role.VILLAGER, true);
        Player judgeVictim = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(wolfVictim, judgeVictim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, wolfVictim.getId());
        engine.recordSelection(room, Role.CORRUPTED_JUDGE, judgeVictim.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(wolfVictim.isAlive()).isFalse();
        assertThat(judgeVictim.isAlive()).isFalse();
        assertThat(victimIds).containsExactlyInAnyOrder(wolfVictim.getId(), judgeVictim.getId());
    }

    @Test
    void resolveDeferredKillsAndClearState_dedupesWhenWerewolfAndJudgeTargetTheSamePlayer() {
        Room room = room(1, 0, 0, 1);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(victim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, victim.getId());
        engine.recordSelection(room, Role.CORRUPTED_JUDGE, victim.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(victim.isAlive()).isFalse();
        assertThat(victimIds).containsExactly(victim.getId());
    }

    // ---------- findLastNightVictims ----------

    @Test
    void findLastNightVictims_returnsRecordedTargetsWithoutApplyingTheKill() {
        Room room = room(1, 0, 0);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(victim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.WEREWOLF, victim.getId());

        assertThat(engine.findLastNightVictims(room)).containsExactly(victim.getId());
        assertThat(victim.isAlive()).isTrue();
    }

    // ---------- afterlife mode: night order ----------

    @Test
    void nextRole_afterlifeOrderIsJudgeThenGravediggerThenGhostThenAngelThenWerewolfThenPriest() {
        Room room = afterlifeRoom(1, 1, 1, 1);
        room.setNoOneVotedOutPreviousDay(true);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockPlayers(room, List.of(deadVillager));

        NightEngine engine = nightEngine();
        assertThat(engine.nextRole(room, null)).contains(Role.CORRUPTED_JUDGE);
        assertThat(engine.nextRole(room, Role.CORRUPTED_JUDGE)).contains(Role.GRAVEDIGGER);
        assertThat(engine.nextRole(room, Role.GRAVEDIGGER)).contains(Role.GHOST);
        assertThat(engine.nextRole(room, Role.GHOST)).contains(Role.ANGEL);
        assertThat(engine.nextRole(room, Role.ANGEL)).contains(Role.WEREWOLF);
        assertThat(engine.nextRole(room, Role.WEREWOLF)).contains(Role.PRIEST);
        assertThat(engine.nextRole(room, Role.PRIEST)).isEmpty();
    }

    @Test
    void nextRole_skipsGhostAndAngelWhenNobodyDeadYet() {
        Room room = afterlifeRoom(1, 1, 0, 0);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        mockPlayers(room, List.of(wolf));

        assertThat(nightEngine().nextRole(room, null)).contains(Role.WEREWOLF);
    }

    @Test
    void nextRole_includesGhostAndAngelOnceSomeoneHasDied() {
        Room room = afterlifeRoom(1, 1, 0, 0);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player deadVillager = player(room, "DEAD", Role.VILLAGER, false);
        mockPlayers(room, List.of(wolf, deadVillager));

        NightEngine engine = nightEngine();
        assertThat(engine.nextRole(room, null)).contains(Role.GHOST);
        assertThat(engine.nextRole(room, Role.GHOST)).contains(Role.ANGEL);
    }

    // ---------- afterlife mode: ghost curse selection ----------

    @Test
    void recordSelection_ghostCurseFillsBothSlotsAcrossTwoClicks() {
        Room room = afterlifeRoom(1, 1, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1, v2));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());
        engine.recordSelection(room, Role.GHOST, v2.getId());

        NightAction action = engine.findAction(room, Role.GHOST).orElseThrow();
        assertThat(action.getTargetPlayerId()).isEqualTo(v1.getId());
        assertThat(action.getSecondTargetPlayerId()).isEqualTo(v2.getId());
    }

    @Test
    void recordSelection_ghostCurseClearsSlotOnReclickAndShiftsSecondIntoFirst() {
        Room room = afterlifeRoom(1, 1, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1, v2));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());
        engine.recordSelection(room, Role.GHOST, v2.getId());
        engine.recordSelection(room, Role.GHOST, v1.getId());

        NightAction action = engine.findAction(room, Role.GHOST).orElseThrow();
        assertThat(action.getTargetPlayerId()).isEqualTo(v2.getId());
        assertThat(action.getSecondTargetPlayerId()).isNull();
    }

    @Test
    void recordSelection_ghostCurseIgnoresThirdDistinctPlayerWhenBothSlotsFull() {
        Room room = afterlifeRoom(1, 1, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        Player v3 = player(room, "V3", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1, v2, v3));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());
        engine.recordSelection(room, Role.GHOST, v2.getId());
        engine.recordSelection(room, Role.GHOST, v3.getId());

        NightAction action = engine.findAction(room, Role.GHOST).orElseThrow();
        assertThat(action.getTargetPlayerId()).isEqualTo(v1.getId());
        assertThat(action.getSecondTargetPlayerId()).isEqualTo(v2.getId());
    }

    @Test
    void requireSelectionIfNeeded_throwsWhenOnlyOneGhostCurseTargetSelected() {
        Room room = afterlifeRoom(1, 0, 0, 0);
        Player deadGhost = player(room, "GHOST1", Role.GHOST, false);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(deadGhost, v1, v2));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());

        assertThatThrownBy(() -> engine.requireSelectionIfNeeded(room, Role.GHOST))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void requireSelectionIfNeeded_doesNotThrowWhenBothGhostCurseTargetsSelected() {
        Room room = afterlifeRoom(1, 0, 0, 0);
        Player deadGhost = player(room, "GHOST1", Role.GHOST, false);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(deadGhost, v1, v2));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());
        engine.recordSelection(room, Role.GHOST, v2.getId());

        engine.requireSelectionIfNeeded(room, Role.GHOST);
    }

    @Test
    void requireSelectionIfNeeded_ghostCurseSatisfiedWithOneTargetWhenOnlyOneAlivePlayerExists() {
        Room room = afterlifeRoom(1, 0, 0, 0);
        Player deadGhost = player(room, "GHOST1", Role.GHOST, false);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(deadGhost, v1));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());

        engine.requireSelectionIfNeeded(room, Role.GHOST);
    }

    // ---------- afterlife mode: curse flip / expiry ----------

    @Test
    void isCursedThisRound_trueForBothGhostCurseTargetsOnly() {
        Room room = afterlifeRoom(0, 0, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        Player v3 = player(room, "V3", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1, v2, v3));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());
        engine.recordSelection(room, Role.GHOST, v2.getId());

        assertThat(engine.isCursedThisRound(room, v1.getId())).isTrue();
        assertThat(engine.isCursedThisRound(room, v2.getId())).isTrue();
        assertThat(engine.isCursedThisRound(room, v3.getId())).isFalse();
    }

    @Test
    void isCursedThisRound_falseTheFollowingRound() {
        Room room = afterlifeRoom(0, 0, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());
        room.setRoundNumber(room.getRoundNumber() + 1);

        assertThat(engine.isCursedThisRound(room, v1.getId())).isFalse();
    }

    // ---------- afterlife mode: angel protection ----------

    @Test
    void resolveDeferredKillsAndClearState_protectedTargetSurvivesWerewolfKill() {
        Room room = afterlifeRoom(1, 0, 0, 0);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(victim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.ANGEL, victim.getId());
        engine.recordSelection(room, Role.WEREWOLF, victim.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(victim.isAlive()).isTrue();
        assertThat(victimIds).isEmpty();
    }

    @Test
    void resolveDeferredKillsAndClearState_corruptedJudgeStillKillsAProtectedTarget() {
        Room room = afterlifeRoom(0, 0, 0, 1);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(victim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.ANGEL, victim.getId());
        engine.recordSelection(room, Role.CORRUPTED_JUDGE, victim.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(victim.isAlive()).isFalse();
        assertThat(victimIds).containsExactly(victim.getId());
    }

    @Test
    void recordSelection_angelProtectionCanBeReusedOnTheSamePlayerAcrossRounds() {
        Room room = afterlifeRoom(0, 0, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.ANGEL, v1.getId());
        room.setRoundNumber(room.getRoundNumber() + 1);
        engine.recordSelection(room, Role.ANGEL, v1.getId());

        assertThat(engine.findAction(room, Role.ANGEL))
                .get().extracting(NightAction::getTargetPlayerId).isEqualTo(v1.getId());
        assertThat(v1.isProtectionBlocked()).isFalse();
    }

    @Test
    void recordSelection_protectingACursedPlayerBurnsTheirProtectionEligibilityForever() {
        Room room = afterlifeRoom(0, 0, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1, v2));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GHOST, v1.getId());
        engine.recordSelection(room, Role.GHOST, v2.getId());
        engine.recordSelection(room, Role.ANGEL, v1.getId());

        assertThat(v1.isProtectionBlocked()).isTrue();
    }

    @Test
    void recordSelection_rejectsProtectingAPlayerWhoseProtectionWasPreviouslyBurned() {
        Room room = afterlifeRoom(0, 0, 0, 0);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        v1.setProtectionBlocked(true);
        mockPlayers(room, List.of(v1));

        assertThatThrownBy(() -> nightEngine().recordSelection(room, Role.ANGEL, v1.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    // ---------- findAction ----------

    @Test
    void findAction_returnsEmptyWhenNoneRecorded() {
        Room room = room(1, 0, 0);

        assertThat(nightEngine().findAction(room, Role.WEREWOLF)).isEmpty();
    }

    // ---------- guardian protection ----------

    @Test
    void nextRole_classicOrderPlacesGuardianAfterWerewolfBeforePriest() {
        Room room = roomWithGuardian(1, 1, 1);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        mockPlayers(room, List.of(wolf));

        NightEngine engine = nightEngine();
        assertThat(engine.nextRole(room, null)).contains(Role.WEREWOLF);
        assertThat(engine.nextRole(room, Role.WEREWOLF)).contains(Role.GUARDIAN);
        assertThat(engine.nextRole(room, Role.GUARDIAN)).contains(Role.PRIEST);
    }

    @Test
    void nextRole_afterlifeOrderPlacesGuardianAfterWerewolfBeforePriest() {
        Room room = afterlifeRoomWithGuardian(1, 1, 1);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        mockPlayers(room, List.of(wolf));

        NightEngine engine = nightEngine();
        assertThat(engine.nextRole(room, null)).contains(Role.WEREWOLF);
        assertThat(engine.nextRole(room, Role.WEREWOLF)).contains(Role.GUARDIAN);
        assertThat(engine.nextRole(room, Role.GUARDIAN)).contains(Role.PRIEST);
    }

    @Test
    void nextRole_includesGuardianEvenWhenTheGuardianHasDied() {
        Room room = roomWithGuardian(1, 0, 1);
        Player wolf = player(room, "WOLF", Role.WEREWOLF, true);
        Player deadGuardian = player(room, "GUARD", Role.GUARDIAN, false);
        mockPlayers(room, List.of(wolf, deadGuardian));

        assertThat(nightEngine().nextRole(room, Role.WEREWOLF)).contains(Role.GUARDIAN);
    }

    @Test
    void resolveDeferredKillsAndClearState_guardianProtectionBlocksWerewolfKill() {
        Room room = roomWithGuardian(1, 0, 1);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(victim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GUARDIAN, victim.getId());
        engine.recordSelection(room, Role.WEREWOLF, victim.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(victim.isAlive()).isTrue();
        assertThat(victimIds).isEmpty();
    }

    @Test
    void resolveDeferredKillsAndClearState_corruptedJudgeStillKillsAGuardianProtectedTarget() {
        Room room = room(0, 0, 0, 1);
        Player victim = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(victim));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GUARDIAN, victim.getId());
        engine.recordSelection(room, Role.CORRUPTED_JUDGE, victim.getId());
        List<Long> victimIds = engine.resolveDeferredKillsAndClearState(room);

        assertThat(victim.isAlive()).isFalse();
        assertThat(victimIds).containsExactly(victim.getId());
    }

    @Test
    void recordSelection_guardianCanProtectHimself() {
        Room room = roomWithGuardian(1, 0, 1);
        Player guardian = player(room, "GUARD", Role.GUARDIAN, true);
        mockPlayers(room, List.of(guardian));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GUARDIAN, guardian.getId());

        assertThat(engine.findAction(room, Role.GUARDIAN))
                .get().extracting(NightAction::getTargetPlayerId).isEqualTo(guardian.getId());
    }

    @Test
    void recordSelection_rejectsProtectingTheSamePlayerTwoNightsInARow() {
        Room room = roomWithGuardian(1, 0, 1);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GUARDIAN, v1.getId());
        room.setRoundNumber(room.getRoundNumber() + 1);

        assertThatThrownBy(() -> engine.recordSelection(room, Role.GUARDIAN, v1.getId()))
                .isInstanceOf(InvalidGamePhaseException.class);
    }

    @Test
    void recordSelection_allowsProtectingADifferentPlayerTheFollowingRound() {
        Room room = roomWithGuardian(1, 0, 1);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1, v2));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GUARDIAN, v1.getId());
        room.setRoundNumber(room.getRoundNumber() + 1);
        engine.recordSelection(room, Role.GUARDIAN, v2.getId());

        assertThat(engine.findAction(room, Role.GUARDIAN))
                .get().extracting(NightAction::getTargetPlayerId).isEqualTo(v2.getId());
    }

    @Test
    void recordSelection_allowsProtectingTheSamePlayerAgainTwoRoundsLater() {
        Room room = roomWithGuardian(1, 0, 1);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        Player v2 = player(room, "V2", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1, v2));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GUARDIAN, v1.getId());
        room.setRoundNumber(room.getRoundNumber() + 1);
        engine.recordSelection(room, Role.GUARDIAN, v2.getId());
        room.setRoundNumber(room.getRoundNumber() + 1);
        engine.recordSelection(room, Role.GUARDIAN, v1.getId());

        assertThat(engine.findAction(room, Role.GUARDIAN))
                .get().extracting(NightAction::getTargetPlayerId).isEqualTo(v1.getId());
    }

    @Test
    void recordSelection_allowsFirstEverGuardianSelectionOnRoundOne() {
        Room room = roomWithGuardian(1, 0, 1);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GUARDIAN, v1.getId());

        assertThat(engine.findAction(room, Role.GUARDIAN))
                .get().extracting(NightAction::getTargetPlayerId).isEqualTo(v1.getId());
    }

    @Test
    void previousRoundGuardianTarget_returnsLastRoundsSelection() {
        Room room = roomWithGuardian(1, 0, 1);
        Player v1 = player(room, "V1", Role.VILLAGER, true);
        mockPlayers(room, List.of(v1));

        NightEngine engine = nightEngine();
        engine.recordSelection(room, Role.GUARDIAN, v1.getId());
        room.setRoundNumber(room.getRoundNumber() + 1);

        assertThat(engine.previousRoundGuardianTarget(room)).isEqualTo(v1.getId());
    }
}
