package com.sondurukedella.security.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    List<SecurityEvent> findByTargetUserIdOrderByIdAsc(Long targetUserId);
}
