package com.ggutim.lupus.model;

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

    @Column(nullable = false, unique = true, length = 43)
    private String masterToken;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    private GamePhase phase;

    @Column(nullable = false)
    private int roundNumber = 1;

    /**
     * Which role's night turn is active, and which beat of it, while
     * {@link #phase} is {@link GamePhase#NIGHT_ACTIONS}. Both null
     * outside that phase.
     */
    @Enumerated(EnumType.STRING)
    private Role currentNightRole;

    @Enumerated(EnumType.STRING)
    private NightStepKind currentNightStepKind;

    private Long pendingVoteVictimId;

    @Enumerated(EnumType.STRING)
    private Alignment winner;

    /**
     * Set instead of {@link #winner} when a solo role (e.g. the idiot)
     * wins on its own rather than as part of the good/evil factions.
     * At most one of the two is ever set.
     */
    @Enumerated(EnumType.STRING)
    private Role winningRole;

    @Column(nullable = false)
    private Instant createdAt;

    protected Room() {
        // required by JPA
    }

    public Room(String code, String masterToken, GameMode gameMode, int playerCount, Map<Role, Integer> roleCounts) {
        this.code = code;
        this.masterToken = masterToken;
        this.gameMode = gameMode;
        this.playerCount = playerCount;
        this.roleCounts = new EnumMap<>(roleCounts);
        this.status = RoomStatus.WAITING_FOR_PLAYERS;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    /**
     * There is deliberately no getter for the raw token: it must only
     * ever be known by the caller who generated it (see
     * {@code RoomService.createRoom}, which keeps its own local copy to
     * return exactly once) and verified here, never re-read afterwards.
     */
    public boolean hasMasterToken(String candidate) {
        return masterToken.equals(candidate);
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

    public RoomStatus getStatus() {
        return status;
    }

    public void start() {
        this.status = RoomStatus.STARTED;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public Role getCurrentNightRole() {
        return currentNightRole;
    }

    public void setCurrentNightRole(Role currentNightRole) {
        this.currentNightRole = currentNightRole;
    }

    public NightStepKind getCurrentNightStepKind() {
        return currentNightStepKind;
    }

    public void setCurrentNightStepKind(NightStepKind currentNightStepKind) {
        this.currentNightStepKind = currentNightStepKind;
    }

    public Long getPendingVoteVictimId() {
        return pendingVoteVictimId;
    }

    public void setPendingVoteVictimId(Long pendingVoteVictimId) {
        this.pendingVoteVictimId = pendingVoteVictimId;
    }

    public Alignment getWinner() {
        return winner;
    }

    public void setWinner(Alignment winner) {
        this.winner = winner;
    }

    public Role getWinningRole() {
        return winningRole;
    }

    public void setWinningRole(Role winningRole) {
        this.winningRole = winningRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
