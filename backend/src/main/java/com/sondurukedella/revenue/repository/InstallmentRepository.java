package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.Installment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    boolean existsByProjectId(Long projectId);

    // id breaks ties so two installments due the same day always come back in the same order
    List<Installment> findByProjectIdOrderByDueDateAscIdAsc(Long projectId);

    List<Installment> findByBilledOnIsNotNull();

    // SELECT ... FOR UPDATE. Every money change on an installment (bill, receipt, write-off,
    // deduction) takes this lock first, so two of them can't both see the same balance.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Installment i where i.id = :id")
    Optional<Installment> findByIdForUpdate(Long id);
}
