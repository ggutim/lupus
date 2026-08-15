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

/**
 * A single role's resolved night turn for a given round: who it
 * targeted (if anyone — a role with no living holder that round never
 * gets one) and any result to show the master (e.g. the priest's
 * alignment reveal). One row per (room, round, role), created once
 * that role's {@link NightStepKind#SELECT} beat resolves.
 *
 * <p>Replaces the old per-role nullable columns on {@link Room}
 * ({@code pendingWerewolfVictimId}, {@code pendingPriestTargetId},
 * {@code priestCheckResult}) with a generic, queryable history so a
 * future role needing "what happened last round" doesn't need another
 * schema change.
 */
@Entity
@Table(name = "night_action", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "round_number", "role"}))
public class NightAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private int roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private Long targetPlayerId;

    @Enumerated(EnumType.STRING)
    private Alignment resultAlignment;

    protected NightAction() {
        // required by JPA
    }

    public NightAction(Room room, int roundNumber, Role role) {
        this.room = room;
        this.roundNumber = roundNumber;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public Role getRole() {
        return role;
    }

    public Long getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(Long targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public Alignment getResultAlignment() {
        return resultAlignment;
    }

    public void setResultAlignment(Alignment resultAlignment) {
        this.resultAlignment = resultAlignment;
    }
}
