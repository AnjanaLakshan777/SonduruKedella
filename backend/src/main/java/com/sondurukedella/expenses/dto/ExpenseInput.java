package com.sondurukedella.expenses.dto;

import com.sondurukedella.expenses.entity.ExpenseScope;
import java.math.BigDecimal;
import java.time.LocalDate;

// The fields create and update share, so the service validates and applies them once.
public interface ExpenseInput {

    LocalDate date();

    ExpenseScope scope();

    Long projectId();

    Long categoryId();

    String description();

    String payee();

    Long supplierId();

    BigDecimal amount();

    LocalDate dueDate();

    String invoiceNo();

    String notes();

    BigDecimal quantity();

    Long unitId();

    BigDecimal unitPrice();

    String machineType();

    LocalDate usageFrom();

    LocalDate usageTo();

    String billingPeriod();
}
