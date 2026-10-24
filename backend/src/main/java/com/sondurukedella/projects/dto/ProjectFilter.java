package com.sondurukedella.projects.dto;

import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import java.time.LocalDate;

// Date ranges replace the UI's hard-coded "Started in 2026" / "Due 2027" presets, e.g.
// "Due in 2027" becomes dueFrom=2027-01-01&dueTo=2027-12-31. All bounds are inclusive.
public record ProjectFilter(
        String q,
        ProjectType type,
        ProjectStatus status,
        Long clientId,
        LocalDate startFrom,
        LocalDate startTo,
        LocalDate dueFrom,
        LocalDate dueTo) {
}
