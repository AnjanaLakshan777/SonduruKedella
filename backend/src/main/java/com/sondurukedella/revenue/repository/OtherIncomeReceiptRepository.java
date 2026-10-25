package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.OtherIncomeReceipt;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OtherIncomeReceiptRepository extends JpaRepository<OtherIncomeReceipt, Long> {

    Optional<OtherIncomeReceipt> findByRequestId(UUID requestId);

    List<OtherIncomeReceipt> findByOtherIncomeIdOrderByPaymentDateAscIdAsc(long otherIncomeId);

    boolean existsByOtherIncomeId(long otherIncomeId);

    // voided receipts never count
    @Query("select coalesce(sum(r.amount), 0) from OtherIncomeReceipt r where r.otherIncomeId = :id and r.voidedAt is null")
    BigDecimal sumActive(long id);

    @Query("select r.otherIncomeId as id, sum(r.amount) as total from OtherIncomeReceipt r "
            + "where r.otherIncomeId in :ids and r.voidedAt is null group by r.otherIncomeId")
    List<IncomeTotal> sumActiveByIncome(Collection<Long> ids);

    interface IncomeTotal {
        Long getId();

        BigDecimal getTotal();
    }
}
