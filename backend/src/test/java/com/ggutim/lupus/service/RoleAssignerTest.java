package com.ggutim.lupus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.model.GameMode;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleAssignerTest {

    @Mock
    private PlayerRepository playerRepository;

    private RoleAssigner roleAssigner() {
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new RoleAssigner(playerRepository);
    }

    private Room room(int playerCount, Map<Role, Integer> roleCounts) {
        return new Room("ABCD", "token", GameMode.CLASSIC, playerCount, roleCounts, true, false);
    }

    private Room afterlifeRoom(int playerCount, Map<Role, Integer> roleCounts) {
        return new Room("ABCD", "token", GameMode.AFTERLIFE, playerCount, roleCounts, true, false);
    }

    private List<Player> players(Room room, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new Player(room, "P" + i, "token" + i))
                .toList();
    }

    @Test
    void assign_assignsConfiguredRoleCountsAndRemainderAsVillager() {
        Room room = room(6, Map.of(Role.WEREWOLF, 2, Role.PRIEST, 1, Role.GRAVEDIGGER, 1));
        List<Player> players = players(room, 6);

        roleAssigner().assign(room, players);

        Map<Role, Long> counts = players.stream()
                .collect(Collectors.groupingBy(Player::getRole, Collectors.counting()));
        assertThat(counts.getOrDefault(Role.WEREWOLF, 0L)).isEqualTo(2);
        assertThat(counts.getOrDefault(Role.PRIEST, 0L)).isEqualTo(1);
        assertThat(counts.getOrDefault(Role.GRAVEDIGGER, 0L)).isEqualTo(1);
        assertThat(counts.getOrDefault(Role.VILLAGER, 0L)).isEqualTo(2);
    }

    @Test
    void assign_rejectsRoleCountsExceedingJoinedPlayers() {
        Room room = room(4, Map.of(Role.WEREWOLF, 3, Role.PRIEST, 2));
        List<Player> players = players(room, 4);

        assertThatThrownBy(() -> roleAssigner().assign(room, new ArrayList<>(players)))
                .isInstanceOf(InvalidRulesetException.class);
    }

    @Test
    void assign_flagsTheMayorAsMayorAndEveryoneElseNot() {
        Room room = room(4, Map.of(Role.MAYOR, 1));
        List<Player> players = players(room, 4);

        roleAssigner().assign(room, players);

        Player mayor = players.stream().filter(p -> p.getRole() == Role.MAYOR).findFirst().orElseThrow();
        assertThat(mayor.isMayor()).isTrue();
        players.stream().filter(p -> p.getRole() != Role.MAYOR)
                .forEach(p -> assertThat(p.isMayor()).isFalse());
    }

    @Test
    void assign_givesSurvivorsOneExtraLifeAndEveryoneElseNone() {
        Room room = room(4, Map.of(Role.SURVIVOR, 1));
        List<Player> players = players(room, 4);

        roleAssigner().assign(room, players);

        Player survivor = players.stream().filter(p -> p.getRole() == Role.SURVIVOR).findFirst().orElseThrow();
        assertThat(survivor.getExtraLives()).isEqualTo(1);
        players.stream().filter(p -> p.getRole() != Role.SURVIVOR)
                .forEach(p -> assertThat(p.getExtraLives()).isZero());
    }

    @Test
    void applyAfterlifeDeathTransition_evilRoleBecomesGhost() {
        Room room = afterlifeRoom(4, Map.of(Role.VILLAGER, 4));
        Player player = new Player(room, "P1", "token1");
        player.setRole(Role.WEREWOLF);

        roleAssigner().applyAfterlifeDeathTransition(room, player);

        assertThat(player.getRole()).isEqualTo(Role.GHOST);
        assertThat(player.getOriginalRole()).isEqualTo(Role.WEREWOLF);
        assertThat(player.getExtraLives()).isZero();
    }

    @Test
    void applyAfterlifeDeathTransition_goodRoleBecomesAngel() {
        Room room = afterlifeRoom(4, Map.of(Role.VILLAGER, 4));
        Player player = new Player(room, "P1", "token1");
        player.setRole(Role.PRIEST);

        roleAssigner().applyAfterlifeDeathTransition(room, player);

        assertThat(player.getRole()).isEqualTo(Role.ANGEL);
        assertThat(player.getOriginalRole()).isEqualTo(Role.PRIEST);
    }

    @Test
    void applyAfterlifeDeathTransition_excludesTheIdiot() {
        Room room = afterlifeRoom(4, Map.of(Role.VILLAGER, 4));
        Player player = new Player(room, "P1", "token1");
        player.setRole(Role.IDIOT);

        roleAssigner().applyAfterlifeDeathTransition(room, player);

        assertThat(player.getRole()).isEqualTo(Role.IDIOT);
        assertThat(player.getOriginalRole()).isNull();
    }

    @Test
    void applyAfterlifeDeathTransition_noOpInClassicMode() {
        Room room = room(4, Map.of(Role.VILLAGER, 4));
        Player player = new Player(room, "P1", "token1");
        player.setRole(Role.WEREWOLF);

        roleAssigner().applyAfterlifeDeathTransition(room, player);

        assertThat(player.getRole()).isEqualTo(Role.WEREWOLF);
        assertThat(player.getOriginalRole()).isNull();
    }

    @Test
    void applyAfterlifeDeathTransition_resetsExtraLivesToZero() {
        Room room = afterlifeRoom(4, Map.of(Role.VILLAGER, 4));
        Player survivor = new Player(room, "P1", "token1");
        survivor.setRole(Role.SURVIVOR);
        survivor.setExtraLives(1);

        roleAssigner().applyAfterlifeDeathTransition(room, survivor);

        assertThat(survivor.getExtraLives()).isZero();
    }
}
