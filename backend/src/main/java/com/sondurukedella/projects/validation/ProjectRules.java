package com.sondurukedella.projects.validation;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.projects.dto.ProjectCreateRequest;
import com.sondurukedella.projects.dto.ProjectFilter;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;

// Cross-field checks that bean validation can't express. The schedule and delay terms are
// checked by Revenue, which owns those rules.
public final class ProjectRules {

    private ProjectRules() {
    }

    public static void validateCreate(ProjectCreateRequest request) {
        if (request.clientId() == null && request.newClient() == null) {
            throw new InvalidRequestException("Choose a client or create a new one.");
        }
        if (request.clientId() != null && request.newClient() != null) {
            throw new InvalidRequestException("Choose an existing client or enter a new one, not both.");
        }
        // Mirrors ck_projects_contract_value: only a consultation may have no fee agreed yet.
        if (request.type() != ProjectType.CONSULTATION && request.contractValue().signum() <= 0) {
            throw new InvalidRequestException("Enter the agreed contract value.");
        }
        if (request.contractValue().signum() < 0) {
            throw new InvalidRequestException("The agreed fee cannot be negative.");
        }
        // The DTO already checks this, but the service can be called from code too, and the
        // contract value must never be rounded into something the agreement doesn't say.
        if (request.contractValue().stripTrailingZeros().scale() > Money.SCALE) {
            throw new InvalidRequestException("The contract value can have at most 2 decimal places.");
        }
        if (!request.expectedCompletion().isAfter(request.startDate())) {
            throw new InvalidRequestException("The expected completion must be after the start date.");
        }
        if (request.status() != null && !request.status().allowedAtCreation()) {
            throw new InvalidRequestException("A new project can only start as Planning, In Progress or On Hold.");
        }
    }

    public static ProjectStatus initialStatus(ProjectCreateRequest request) {
        return request.status() == null ? ProjectStatus.PLANNING : request.status();
    }

    public static void validateFilter(ProjectFilter filter) {
        if (filter.startFrom() != null && filter.startTo() != null && filter.startFrom().isAfter(filter.startTo())) {
            throw new InvalidRequestException("startFrom must be on or before startTo.");
        }
        if (filter.dueFrom() != null && filter.dueTo() != null && filter.dueFrom().isAfter(filter.dueTo())) {
            throw new InvalidRequestException("dueFrom must be on or before dueTo.");
        }
    }
}
