package com.sondurukedella.expenses.validation;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.expenses.dto.ExpenseInput;
import com.sondurukedella.expenses.entity.ExpenseScope;
import java.math.BigDecimal;
import java.math.RoundingMode;

// Checks that need no other module. Category, project, unit and payment-method checks need Settings
// and Projects, so they live in the service.
public final class ExpenseRules {

    private ExpenseRules() {
    }

    public static void validateScope(ExpenseInput input) {
        if (input.scope() == ExpenseScope.PROJECT && input.projectId() == null) {
            throw new InvalidRequestException("Choose the project, or record it as a company-level expense.");
        }
        // reject rather than drop: a project id on a company expense means the form is confused
        if (input.scope() == ExpenseScope.COMPANY && input.projectId() != null) {
            throw new InvalidRequestException("A company-level expense is not assigned to a project; remove the project.");
        }
    }

    public static void validatePeriod(ExpenseInput input) {
        if (input.usageFrom() != null && input.usageTo() != null && input.usageTo().isBefore(input.usageFrom())) {
            throw new InvalidRequestException("End date must be on or after the start date.");
        }
    }

    public static boolean hasMaterialsLine(ExpenseInput input) {
        return input.quantity() != null || input.unitId() != null || input.unitPrice() != null;
    }

    // A materials line is quantity x unit price, rounded the way ck_expenses_materials does
    // (round(quantity * unit_price, 2), which is HALF_UP for these positive amounts). The amount is
    // worked out here; a client-sent amount must agree, because the check would refuse it otherwise.
    public static BigDecimal amount(ExpenseInput input) {
        if (hasMaterialsLine(input)) {
            if (input.quantity() == null || input.unitId() == null || input.unitPrice() == null) {
                throw new InvalidRequestException("Give the quantity, unit and unit price together.");
            }
            BigDecimal computed = input.quantity().multiply(input.unitPrice()).setScale(Money.SCALE, RoundingMode.HALF_UP);
            if (computed.signum() <= 0) {
                throw new InvalidRequestException("Quantity x unit price must come to more than zero.");
            }
            if (input.amount() != null && input.amount().compareTo(computed) != 0) {
                throw new InvalidRequestException("The amount must be quantity x unit price (" + Money.format(computed) + ").");
            }
            return computed;
        }
        if (input.amount() == null) {
            throw new InvalidRequestException("Enter the amount.");
        }
        return Money.amount(input.amount());
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
