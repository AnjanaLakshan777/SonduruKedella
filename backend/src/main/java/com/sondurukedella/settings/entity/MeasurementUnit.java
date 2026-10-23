package com.sondurukedella.settings.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Unit of measurement used on materials, supplier invoices, stock movements and expenses. */
@Entity
@Table(name = "units")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementUnit extends SimpleLookup {

    public static final int NAME_MAX_LENGTH = 30;

    public MeasurementUnit(String name, int sortOrder) {
        super(name, sortOrder);
    }
}
