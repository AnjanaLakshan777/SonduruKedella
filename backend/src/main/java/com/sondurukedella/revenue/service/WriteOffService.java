package com.sondurukedella.revenue.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.WriteOffCreateRequest;
import com.sondurukedella.revenue.dto.WriteOffResponse;
import com.sondurukedella.revenue.dto.WriteOffReversalRequest;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.entity.ReceivableWriteOff;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import com.sondurukedella.revenue.repository.ReceivableWriteOffRepository;
import com.sondurukedella.revenue.service.ReceivableCalculator.Receivable;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bad-debt write-offs. Create only: V1's receivable_write_offs has no void or reversal columns, and
// deleting one would erase financial history, so reversing waits for a migration that adds them.
@Service
@RequiredArgsConstructor
public class WriteOffService {

    // the write-off dialog's rule: a real sentence, not "bad"
    static final int MIN_REASON_LENGTH = 10;

    private final InstallmentLedger ledger;
    private final ReceivableWriteOffRepository repository;
    private final RevenueMapper mapper;
    private final ActivityService activityService;
    private final Clock clock;

    @Transactional
    public WriteOffResponse create(long projectId, long installmentId, WriteOffCreateRequest request) {
        Installment installment = ledger.lockInProject(projectId, installmentId);
        if (!installment.isBilled()) {
            throw new InvalidRequestException("Only a billed installment can be written off.");
        }
        String reason = request.reason().strip();
        if (reason.length() < MIN_REASON_LENGTH) {
            throw new InvalidRequestException("Give the reason the amount cannot be collected (at least a sentence).");
        }
        // same locked balance as receipts, so a write-off and a receipt can't together go past what is owed
        Receivable before = ledger.receivable(installment, LocalDate.now(clock));
        if (request.amount().compareTo(before.outstanding()) > 0) {
            throw new InvalidRequestException("Cannot exceed the remaining " + Money.format(before.outstanding()) + ".");
        }

        ReceivableWriteOff saved = repository.saveAndFlush(
                new ReceivableWriteOff(installment.getId(), request.date(), Money.amount(request.amount()), reason));
        // the UI's wording, plus the amount and installment
        activityService.record(projectId, ActivityKind.WRITEOFF, "Bad debt written off: " + reason + " ("
                + Money.format(saved.getAmount()) + ", " + installment.getDescription() + ")");
        return mapper.toResponse(saved, projectId);
    }

    // Under the same installment lock as receipts and write-offs. Reversing only raises what is owed,
    // so no balance check is needed.
    @Transactional
    public WriteOffResponse reverse(long projectId, long writeOffId, WriteOffReversalRequest request) {
        ReceivableWriteOff writeOff = repository.findById(writeOffId).orElseThrow(() -> new NotFoundException("Write-off not found."));
        Installment installment = ledger.lockInProject(projectId, writeOff.getInstallmentId());
        if (writeOff.isVoided()) {
            throw new ConflictException("This write-off was already reversed.");
        }
        ConflictException.requireCurrentVersion(request.version(), writeOff.getVersion());
        writeOff.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        writeOff = repository.saveAndFlush(writeOff);
        activityService.record(projectId, ActivityKind.WRITEOFF, "Write-off reversed: " + writeOff.getVoidReason() + " ("
                + Money.format(writeOff.getAmount()) + ", " + installment.getDescription() + ")");
        return mapper.toResponse(writeOff, projectId);
    }
}
