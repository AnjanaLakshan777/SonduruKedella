package com.sondurukedella.suppliers.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// only voidable, never edited or deleted (V1 trigger)
@Getter
@Entity
@Table(name = "supplier_payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierPayment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private Long paymentMethodId;

    @Column(length = 100, updatable = false)
    private String reference;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @Version
    private Long version;

    public SupplierPayment(long invoiceId, LocalDate paymentDate, BigDecimal amount, long paymentMethodId, String reference,
            String notes, UUID requestId) {
        this.invoiceId = invoiceId;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.paymentMethodId = paymentMethodId;
        this.reference = reference;
        this.notes = notes;
        this.requestId = requestId;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    public void markVoided(OffsetDateTime when, String reason) {
        this.voidedAt = when;
        this.voidReason = reason;
    }
}
