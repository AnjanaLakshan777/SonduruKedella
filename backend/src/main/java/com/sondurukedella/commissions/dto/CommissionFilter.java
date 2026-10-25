package com.sondurukedella.commissions.dto;

import com.sondurukedella.projects.entity.ProjectType;
import java.time.LocalDate;

// All optional. recipient matches the name exactly (ignoring case and extra spaces); q matches part
// of the recipient or role. from/to limit confirmedOn, so drafts drop out when either is given.
public record CommissionFilter(
        String recipient,
        String q,
        Long projectId,
        ProjectType type,
        CommissionState status,
        LocalDate from,
        LocalDate to) {
}
