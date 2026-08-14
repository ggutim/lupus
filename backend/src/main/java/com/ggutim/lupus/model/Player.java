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

    @Column(nullable = false)
    private boolean alive = true;

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

    public boolean isAlive() {
        return alive;
    }

    public void kill() {
        this.alive = false;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
