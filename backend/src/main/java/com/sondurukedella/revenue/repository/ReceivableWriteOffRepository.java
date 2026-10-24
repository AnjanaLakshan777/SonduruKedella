package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.ReceivableWriteOff;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReceivableWriteOffRepository extends JpaRepository<ReceivableWriteOff, Long> {

    List<ReceivableWriteOff> findByInstallmentIdInOrderByWriteOffDateAscIdAsc(Collection<Long> installmentIds);

    @Query("select coalesce(sum(w.amount), 0) from ReceivableWriteOff w where w.installmentId = :installmentId")
    BigDecimal sumFor(Long installmentId);

    @Query("select w.installmentId as installmentId, sum(w.amount) as total from ReceivableWriteOff w "
            + "where w.installmentId in :installmentIds group by w.installmentId")
    List<InstallmentTotal> sumByInstallment(Collection<Long> installmentIds);
}
