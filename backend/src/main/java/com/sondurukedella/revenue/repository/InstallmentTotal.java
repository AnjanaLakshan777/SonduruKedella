package com.sondurukedella.revenue.repository;

import java.math.BigDecimal;

// one row of a "sum per installment" query
public interface InstallmentTotal {

    Long getInstallmentId();

    BigDecimal getTotal();
}
