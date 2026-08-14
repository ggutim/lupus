package com.ggutim.lupus.room;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByRoomIdOrderByJoinedAtAsc(Long roomId);

    List<Player> findByRoomIdAndAliveTrueOrderByJoinedAtAsc(Long roomId);

    long countByRoomId(Long roomId);

    boolean existsByRoomIdAndNicknameIgnoreCase(Long roomId, String nickname);
}
