package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// What Revenue needs to know about a project, handed over by the projects module. Revenue can't
// look these up itself: projects already depends on revenue, and the reverse would be a cycle.
// projectType is the plain code (CONSTRUCTION, ...), as stored in delay_deduction_terms.
public record ProjectFacts(long projectId, String projectType, BigDecimal contractValue, LocalDate actualCompletion) {
}
