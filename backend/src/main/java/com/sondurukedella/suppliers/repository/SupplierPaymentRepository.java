package com.sondurukedella.suppliers.repository;

import com.sondurukedella.suppliers.entity.SupplierPayment;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    Optional<SupplierPayment> findByRequestId(UUID requestId);

    List<SupplierPayment> findByInvoiceIdOrderByPaymentDateAscIdAsc(long invoiceId);

    @Query("select coalesce(sum(p.amount), 0) from SupplierPayment p where p.invoiceId = :invoiceId and p.voidedAt is null")
    BigDecimal sumActive(long invoiceId);

    @Query("select p.invoiceId as invoiceId, sum(p.amount) as total from SupplierPayment p "
            + "where p.invoiceId in :invoiceIds and p.voidedAt is null group by p.invoiceId")
    List<InvoiceTotal> sumActiveByInvoice(Collection<Long> invoiceIds);

    interface InvoiceTotal {
        Long getInvoiceId();

        BigDecimal getTotal();
    }
}
