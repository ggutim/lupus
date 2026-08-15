package com.ggutim.lupus.repository;

import com.ggutim.lupus.model.NightAction;
import com.ggutim.lupus.model.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NightActionRepository extends JpaRepository<NightAction, Long> {

    Optional<NightAction> findByRoomIdAndRoundNumberAndRole(Long roomId, int roundNumber, Role role);
}
