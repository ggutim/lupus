package com.ggutim.lupus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A participant who has joined a {@link Room}. Nicknames are unique within
 * a room, but not globally.
 */
@Entity
@Table(name = "player", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "nickname"}))
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false, unique = true, length = 43)
    private String playerToken;

    @Enumerated(EnumType.STRING)
    private Role role;

    /**
     * What {@link #role} was before an afterlife-mode death transition
     * (see {@code RoleAssigner#applyAfterlifeDeathTransition}) overwrote
     * it with {@link Role#GHOST}/{@link Role#ANGEL}. Display-only — no
     * game logic reads this, {@link #role} remains the single source of
     * truth for everything else. Null outside afterlife mode.
     */
    @Enumerated(EnumType.STRING)
    private Role originalRole;

    @Column(nullable = false)
    private boolean alive = true;

    /**
     * Afterlife mode only: permanently true once an angel has protected
     * this player while they were cursed — the one case where protection
     * eligibility is burned for good, rather than reusable night after
     * night. See {@code AngelProtectEffect}.
     */
    @Column(nullable = false)
    private boolean protectionBlocked = false;

    /**
     * Extra lives beyond the usual single life, e.g. the survivor's
     * (see {@link Role#getStartingExtraLives()}). Consulted only by
     * whichever kill mechanic cares — today, the werewolves' — not a
     * generic shield against every way a player can die.
     */
    @Column(nullable = false)
    private int extraLives;

    @Column(nullable = false)
    private Instant joinedAt;

    protected Player() {
        // required by JPA
    }

    public Player(Room room, String nickname, String playerToken) {
        this.room = room;
        this.nickname = nickname;
        this.playerToken = playerToken;
        this.joinedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getNickname() {
        return nickname;
    }

    /**
     * There is deliberately no getter for the raw token: it must only
     * ever be known by the caller who generated it (see
     * {@code PlayerService.joinRoom}, which keeps its own local copy to
     * return exactly once) and verified here, never re-read afterwards.
     */
    public boolean hasPlayerToken(String candidate) {
        return playerToken.equals(candidate);
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Role getOriginalRole() {
        return originalRole;
    }

    public void setOriginalRole(Role originalRole) {
        this.originalRole = originalRole;
    }

    public boolean isAlive() {
        return alive;
    }

    public void kill() {
        this.alive = false;
    }

    public boolean isProtectionBlocked() {
        return protectionBlocked;
    }

    public void setProtectionBlocked(boolean protectionBlocked) {
        this.protectionBlocked = protectionBlocked;
    }

    public int getExtraLives() {
        return extraLives;
    }

    public void setExtraLives(int extraLives) {
        this.extraLives = extraLives;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
