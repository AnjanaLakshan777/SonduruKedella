package com.sondurukedella.payroll.repository;

import com.sondurukedella.payroll.entity.AdvanceRecovery;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdvanceRecoveryRepository extends JpaRepository<AdvanceRecovery, Long> {

    List<AdvanceRecovery> findByPayrollLineIdIn(Collection<Long> lineIds);

    List<AdvanceRecovery> findByAdvanceId(Long advanceId);

    @Query("select r.advanceId as advanceId, sum(r.amount) as total from AdvanceRecovery r "
            + "where r.advanceId in :advanceIds group by r.advanceId")
    List<AdvanceTotal> recoveredByAdvance(Collection<Long> advanceIds);

    interface AdvanceTotal {
        Long getAdvanceId();

        BigDecimal getTotal();
    }
}
