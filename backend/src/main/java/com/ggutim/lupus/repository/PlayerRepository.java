package com.ggutim.lupus.repository;

import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByRoomIdOrderByJoinedAtAsc(Long roomId);

    List<Player> findByRoomIdAndAliveTrueOrderByJoinedAtAsc(Long roomId);

    List<Player> findByRoomIdAndAliveFalseOrderByJoinedAtAsc(Long roomId);

    long countByRoomId(Long roomId);

    boolean existsByRoomIdAndNicknameIgnoreCase(Long roomId, String nickname);

    /**
     * Scopes a player lookup to a room in one query, instead of
     * fetching by id and separately checking {@code player.getRoom()}
     * — the pattern every "look up this target, but only within my
     * room" call site needs (a stranger's id from another room must
     * never resolve here).
     */
    Optional<Player> findByIdAndRoomId(Long id, Long roomId);

    /**
     * The sole holder of a given starting {@link Role} in a room, if
     * any — for roles capped at one per room (e.g. the killer). Doesn't
     * apply to the mayor, whose current holder isn't tracked via
     * {@code role} (see {@link #findFirstByRoomIdAndMayorTrue}).
     */
    Optional<Player> findFirstByRoomIdAndRole(Long roomId, Role role);

    /** The player who currently holds the mayor status, if any — see {@link Player#isMayor()}. */
    Optional<Player> findFirstByRoomIdAndMayorTrue(Long roomId);
}
