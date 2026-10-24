package com.sondurukedella.payroll.mapper;

import com.sondurukedella.payroll.dto.PayrollCorrectionResponse;
import com.sondurukedella.payroll.dto.PayrollPaymentResponse;
import com.sondurukedella.payroll.dto.SalaryAdvanceResponse;
import com.sondurukedella.payroll.entity.PayrollCorrection;
import com.sondurukedella.payroll.entity.PayrollPayment;
import com.sondurukedella.payroll.entity.SalaryAdvance;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class PayrollMapper {

    public PayrollPaymentResponse toResponse(PayrollPayment p, long employeeId, LocalDate weekStart, String method) {
        return new PayrollPaymentResponse(p.getId(), employeeId, weekStart, p.getPaymentDate(), p.getAmount(), p.getPaymentMethodId(),
                method, p.getReference(), p.getNotes(), p.isVoided(), p.getVoidedAt(), p.getVoidReason(), p.getRequestId(),
                p.getCreatedAt(), p.getVersion());
    }

    public PayrollCorrectionResponse toResponse(PayrollCorrection c, long employeeId, LocalDate weekStart) {
        return new PayrollCorrectionResponse(c.getId(), employeeId, weekStart, c.getCorrectionDate(), c.getWageDelta(),
                c.getProjectId(), c.getReason(), c.getCreatedAt());
    }

    public SalaryAdvanceResponse toResponse(SalaryAdvance a, String employee, String method, BigDecimal recovered) {
        BigDecimal outstanding = a.isVoided() ? BigDecimal.ZERO.setScale(2) : a.getAmount().subtract(recovered);
        return new SalaryAdvanceResponse(a.getId(), a.getEmployeeId(), employee, a.getPaymentDate(), a.getAmount(),
                a.getPaymentMethodId(), method, a.getReference(), a.getNotes(), recovered, outstanding, a.isVoided(), a.getVoidedAt(),
                a.getVoidReason(), a.getRequestId(), a.getVersion());
    }
}
