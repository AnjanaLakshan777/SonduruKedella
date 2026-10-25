package com.sondurukedella.commissions.dto;

import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.util.List;

// The project's Commissions tab. designSplit is null for construction projects. arrangements
// includes voided ones (with their reason), oldest first.
public record ProjectCommissionsResponse(
        long projectId,
        String projectCode,
        String projectName,
        ProjectType projectType,
        BigDecimal contractValue,
        CommissionPolicyCheck policy,
        DesignSplit designSplit,
        CommissionTotals totals,
        List<CommissionResponse> arrangements) {
}
