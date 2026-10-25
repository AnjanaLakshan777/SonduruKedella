package com.sondurukedella.revenue.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Bad debt written off an installment. It reduces what is owed but is not money received.
// A wrong write-off is reversed (V7: voided with a reason); the row stays and stops counting.
// trg_receivable_write_offs_guard refuses deletes and any other change.
@Getter
@Entity
@Table(name = "receivable_write_offs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivableWriteOff extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installment_id", nullable = false, updatable = false)
    private Long installmentId;

    @Column(name = "write_off_date", nullable = false, updatable = false)
    private LocalDate writeOffDate;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @Version
    private Long version;

    public boolean isVoided() {
        return voidedAt != null;
    }

    public void markVoided(OffsetDateTime when, String reason) {
        this.voidedAt = when;
        this.voidReason = reason;
    }

    public ReceivableWriteOff(long installmentId, LocalDate writeOffDate, BigDecimal amount, String reason) {
        this.installmentId = installmentId;
        this.writeOffDate = writeOffDate;
        this.amount = amount;
        this.reason = reason;
    }
}
