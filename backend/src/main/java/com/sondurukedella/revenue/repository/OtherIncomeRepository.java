package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.OtherIncome;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OtherIncomeRepository extends JpaRepository<OtherIncome, Long>, JpaSpecificationExecutor<OtherIncome> {

    // locked by receipts so two at once can't go over the amount
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OtherIncome o where o.id = :id")
    Optional<OtherIncome> findByIdForUpdate(long id);
}
