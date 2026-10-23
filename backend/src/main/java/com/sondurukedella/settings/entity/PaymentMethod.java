package com.sondurukedella.settings.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Payment method recorded with each payment as information only (no cash or bank balances). */
@Entity
@Table(name = "payment_methods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod extends SimpleLookup {

    public static final int NAME_MAX_LENGTH = 50;

    public PaymentMethod(String name, int sortOrder) {
        super(name, sortOrder);
    }
}
