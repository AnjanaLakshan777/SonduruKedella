package com.sondurukedella.expenses.mapper;

import com.sondurukedella.expenses.dto.ExpensePaymentResponse;
import com.sondurukedella.expenses.dto.ExpenseResponse;
import com.sondurukedella.expenses.entity.Expense;
import com.sondurukedella.expenses.entity.ExpensePayment;
import com.sondurukedella.expenses.service.ExpenseCalculator.Balance;
import org.springframework.stereotype.Component;

@Component
public class ExpenseMapper {

    public ExpenseResponse toResponse(Expense expense, String categoryName, String supplierName, String unitName,
            Balance balance) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getExpenseDate(),
                expense.getScope(),
                expense.getProjectId(),
                expense.getExpenseGroup(),
                expense.getCategoryId(),
                categoryName,
                expense.getDescription(),
                expense.getPayee(),
                expense.getSupplierId(),
                supplierName,
                expense.getAmount(),
                expense.getDueDate(),
                expense.getInvoiceNo(),
                expense.getNotes(),
                expense.getQuantity(),
                expense.getUnitId(),
                unitName,
                expense.getUnitPrice(),
                expense.getMachineType(),
                expense.getUsageFrom(),
                expense.getUsageTo(),
                expense.getBillingPeriod(),
                expense.getAttachmentFilename(),
                expense.getAttachmentSize(),
                balance.paid(),
                balance.outstanding(),
                balance.status(),
                expense.getCreatedAt(),
                expense.getUpdatedAt(),
                expense.getVersion(),
                null);
    }

    public ExpensePaymentResponse toResponse(ExpensePayment payment, String methodName) {
        return new ExpensePaymentResponse(
                payment.getId(),
                payment.getExpenseId(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getPaymentMethodId(),
                methodName,
                payment.getReference(),
                payment.getNotes(),
                payment.getRequestId(),
                payment.isVoided(),
                payment.getVoidedAt(),
                payment.getVoidReason(),
                payment.getCreatedAt(),
                payment.getVersion());
    }
}
