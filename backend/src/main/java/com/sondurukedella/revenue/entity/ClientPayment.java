package com.sondurukedella.revenue.entity;

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

// A client receipt against one billed installment. Receipts settle revenue, they never create it.
// The V1 trigger trg_client_payments_guard refuses deletes and any change except voiding, so the
// only mutator here is markVoided. paymentMethodId points at Settings' payment_methods.
@Getter
@Entity
@Table(name = "client_payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientPayment extends AuditableEntity {

    public static final int REFERENCE_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installment_id", nullable = false, updatable = false)
    private Long installmentId;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private Long paymentMethodId;

    @Column(length = REFERENCE_MAX_LENGTH, updatable = false)
    private String reference;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    // client-generated key: resending the same submission returns the first receipt instead of a second one
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @Version
    private Long version;

    public ClientPayment(long installmentId, LocalDate paymentDate, BigDecimal amount, long paymentMethodId,
            String reference, String notes, UUID requestId) {
        this.installmentId = installmentId;
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
