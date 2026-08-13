package com.ggutim.lupus.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false)
    private Instant joinedAt;

    protected Player() {
        // required by JPA
    }

    public Player(Room room, String nickname) {
        this.room = room;
        this.nickname = nickname;
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

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
