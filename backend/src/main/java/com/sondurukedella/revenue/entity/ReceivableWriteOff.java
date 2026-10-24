package com.sondurukedella.revenue.entity;

import com.sondurukedella.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Bad debt written off an installment. It reduces what is owed but is not money received.
// V1 gives this table no void/reversal columns and no version, so a write-off is treated as
// permanent: no setters, and no code path updates or deletes it. Reversal needs a schema change first.
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

    public ReceivableWriteOff(long installmentId, LocalDate writeOffDate, BigDecimal amount, String reason) {
        this.installmentId = installmentId;
        this.writeOffDate = writeOffDate;
        this.amount = amount;
        this.reason = reason;
    }
}
