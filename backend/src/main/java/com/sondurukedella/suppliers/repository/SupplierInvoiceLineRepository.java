package com.sondurukedella.suppliers.repository;

import com.sondurukedella.suppliers.entity.SupplierInvoiceLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierInvoiceLineRepository extends JpaRepository<SupplierInvoiceLine, Long> {

    List<SupplierInvoiceLine> findByInvoiceIdOrderByLineNo(long invoiceId);

    void deleteByInvoiceId(long invoiceId);
}
