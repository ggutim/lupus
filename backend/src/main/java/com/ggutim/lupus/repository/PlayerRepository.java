package com.ggutim.lupus.repository;

import com.ggutim.lupus.model.Player;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByRoomIdOrderByJoinedAtAsc(Long roomId);

    List<Player> findByRoomIdAndAliveTrueOrderByJoinedAtAsc(Long roomId);

    List<Player> findByRoomIdAndAliveFalseOrderByJoinedAtAsc(Long roomId);

    long countByRoomId(Long roomId);

    boolean existsByRoomIdAndNicknameIgnoreCase(Long roomId, String nickname);
}
