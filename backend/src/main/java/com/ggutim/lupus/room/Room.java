package com.ggutim.lupus.room;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * A single game session, identified by a short access {@code code}.
 * The ruleset (game mode, player count and role counts) is fixed at
 * creation time by the master.
 */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 4)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameMode gameMode;

    @Column(nullable = false)
    private int playerCount;

    @ElementCollection
    @CollectionTable(name = "room_role_count", joinColumns = @JoinColumn(name = "room_id"))
    @MapKeyColumn(name = "role")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "count", nullable = false)
    private Map<Role, Integer> roleCounts = new EnumMap<>(Role.class);

    @Column(nullable = false)
    private Instant createdAt;

    protected Room() {
        // required by JPA
    }

    public Room(String code, GameMode gameMode, int playerCount, Map<Role, Integer> roleCounts) {
        this.code = code;
        this.gameMode = gameMode;
        this.playerCount = playerCount;
        this.roleCounts = new EnumMap<>(roleCounts);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public Map<Role, Integer> getRoleCounts() {
        return roleCounts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
