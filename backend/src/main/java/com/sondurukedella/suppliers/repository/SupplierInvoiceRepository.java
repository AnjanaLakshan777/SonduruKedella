package com.sondurukedella.suppliers.repository;

import com.sondurukedella.suppliers.entity.SupplierInvoice;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long>, JpaSpecificationExecutor<SupplierInvoice> {

    // cancelled invoices free up their number
    boolean existsBySupplierIdAndInvoiceNoAndCancelledAtIsNull(long supplierId, String invoiceNo);

    boolean existsBySupplierIdAndInvoiceNoAndCancelledAtIsNullAndIdNot(long supplierId, String invoiceNo, long id);

    // locked for payments so two at once can't overpay
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SupplierInvoice i where i.id = :id")
    Optional<SupplierInvoice> findByIdForUpdate(long id);
}
