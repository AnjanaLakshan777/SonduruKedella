package com.sondurukedella.revenue.validation;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.InstallmentDraft;
import com.sondurukedella.revenue.entity.Installment;
import java.math.BigDecimal;
import java.util.List;

// Bean validation already covers each row when the request comes through the API. These checks
// are repeated here because the service can be called from code too, and the total rule needs
// the contract value anyway.
public final class ScheduleRules {

    private ScheduleRules() {
    }

    public static void validateInitialSchedule(BigDecimal contractValue, List<InstallmentDraft> rows) {
        BigDecimal contract = Money.amount(contractValue);
        List<InstallmentDraft> schedule = rows == null ? List.of() : rows;

        // A consultation with no agreed fee yet is billed later, so there is nothing to schedule.
        if (contract.signum() == 0) {
            if (!schedule.isEmpty()) {
                throw new InvalidRequestException("A project with no agreed fee cannot have a payment schedule.");
            }
            return;
        }
        if (schedule.isEmpty()) {
            throw new InvalidRequestException("Add the client payment schedule.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < schedule.size(); i++) {
            total = total.add(validRowAmount(schedule.get(i), i + 1));
        }
        // compareTo, not equals: 1500.0 and 1500.00 are the same amount
        if (total.compareTo(contract) != 0) {
            throw new InvalidRequestException("The schedule adds up to " + total.toPlainString()
                    + " but the contract value is " + contract.toPlainString() + ". They must match exactly.");
        }
    }

    private static BigDecimal validRowAmount(InstallmentDraft row, int number) {
        if (row == null) {
            throw new InvalidRequestException("Installment " + number + " is empty.");
        }
        if (row.description() == null || row.description().isBlank()) {
            throw new InvalidRequestException("Describe installment " + number + ".");
        }
        if (row.description().strip().length() > Installment.DESCRIPTION_MAX_LENGTH) {
            throw new InvalidRequestException("The description of installment " + number + " is too long.");
        }
        if (row.dueDate() == null) {
            throw new InvalidRequestException("Choose a due date for installment " + number + ".");
        }
        BigDecimal amount = row.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException("Installment " + number + " needs an amount greater than zero.");
        }
        // reject rather than round: rounding here would quietly change what the agreement says
        if (amount.stripTrailingZeros().scale() > Money.SCALE) {
            throw new InvalidRequestException("Installment " + number + " can have at most 2 decimal places.");
        }
        return Money.amount(amount);
    }
}
