package com.sondurukedella.inventory.dto;

import com.sondurukedella.inventory.entity.MovementType;
import java.time.LocalDate;

// all optional, combined with AND; from/to bound the movement date, inclusive
public record MovementFilter(
        Long materialId,
        MovementType type,
        Long projectId,
        Long supplierId,
        LocalDate from,
        LocalDate to) {
}
