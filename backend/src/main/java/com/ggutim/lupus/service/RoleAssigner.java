package com.ggutim.lupus.service;

import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Assigns a room's configured role counts to its joined players at
 * game start, shuffled, with any remainder becoming villagers. Reads
 * {@link Room#getRoleCounts()} generically over every non-villager
 * {@link Role}, so a new role needs no change here.
 */
@Service
public class RoleAssigner {

    private final PlayerRepository playerRepository;

    RoleAssigner(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void assign(Room room, List<Player> players) {
        Map<Role, Integer> roleCounts = room.getRoleCounts();
        int specialRoleCount = 0;
        for (Role role : Role.values()) {
            if (role != Role.VILLAGER) {
                specialRoleCount += roleCounts.getOrDefault(role, 0);
            }
        }
        if (specialRoleCount > players.size()) {
            throw new InvalidRulesetException("Configured role counts cannot exceed the number of joined players");
        }

        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int index = 0;
        for (Role role : Role.values()) {
            if (role == Role.VILLAGER) {
                continue;
            }
            int count = roleCounts.getOrDefault(role, 0);
            for (int i = 0; i < count; i++) {
                assignRole(shuffled.get(index++), role);
            }
        }
        while (index < shuffled.size()) {
            assignRole(shuffled.get(index++), Role.VILLAGER);
        }

        playerRepository.saveAll(shuffled);
    }

    private void assignRole(Player player, Role role) {
        player.setRole(role);
        player.setExtraLives(role.getStartingExtraLives());
    }
}
