package com.sondurukedella.commissions.dto;

import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.util.List;

// split (pool vs company share) is null for consultation projects; voided rows are included
public record ProjectCommissionsResponse(
        long projectId,
        String projectCode,
        String projectName,
        ProjectType projectType,
        BigDecimal contractValue,
        CommissionPolicyCheck policy,
        CommissionSplit split,
        CommissionTotals totals,
        List<CommissionResponse> arrangements) {
}
