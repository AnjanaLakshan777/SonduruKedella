package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.util.List;

// A worker's week with everything recorded against it. recoveries are the advances its deduction
// recovered (frozen weeks only).
public record PayrollLineDetail(
        PayrollLineResponse line,
        List<PayrollPaymentResponse> payments,
        List<PayrollCorrectionResponse> corrections,
        List<Recovery> recoveries) {

    public record Recovery(long advanceId, BigDecimal amount) {
    }
}
