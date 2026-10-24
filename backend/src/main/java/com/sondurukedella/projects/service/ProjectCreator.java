package com.sondurukedella.projects.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.clients.dto.ClientResponse;
import com.sondurukedella.clients.service.ClientService;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.projects.dto.ProjectCreateRequest;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.Project;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.mapper.ProjectMapper;
import com.sondurukedella.projects.repository.ProjectRepository;
import com.sondurukedella.projects.validation.ProjectRules;
import com.sondurukedella.revenue.service.DelayTermsService;
import com.sondurukedella.revenue.service.InstallmentScheduleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// One attempt at creating a project. Lives in its own bean so ProjectService can retry the whole
// transaction on a code clash - you can't retry inside it, Postgres aborts the transaction on
// the first constraint error.
@Component
@RequiredArgsConstructor
class ProjectCreator {

    private final ProjectRepository repository;
    private final ProjectMapper mapper;
    private final ClientService clientService;
    private final InstallmentScheduleService scheduleService;
    private final DelayTermsService delayTermsService;
    private final ActivityService activityService;

    // Client, project, schedule, delay terms and the activity entry all commit together.
    // Any exception rolls the lot back, including a client created inline a moment earlier.
    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        ProjectRules.validateCreate(request);

        long clientId;
        String clientName;
        if (request.newClient() != null) {
            ClientResponse client = clientService.create(request.newClient());
            clientId = client.id();
            clientName = client.name();
        } else {
            clientId = request.clientId();
            if (!clientService.exists(clientId)) {
                throw new InvalidRequestException("The selected client does not exist.");
            }
            clientName = clientService.findNames(List.of(clientId)).get(clientId);
        }

        int year = request.startDate().getYear();
        repository.lockCodeYear(ProjectCodes.LOCK_NAMESPACE, year);
        String code = ProjectCodes.next(year, repository.findCodesStartingWith(ProjectCodes.prefix(year) + "%"));

        ProjectStatus status = ProjectRules.initialStatus(request);
        Project project = new Project(code, request.name().strip(), clientId, request.type(),
                Money.amount(request.contractValue()), request.startDate(), request.expectedCompletion(), status);
        project.setLocation(trimToNull(request.location()));
        project.setDescription(trimToNull(request.description()));
        project.setNotes(trimToNull(request.notes()));
        // flush now so a code clash surfaces here, before the schedule is written
        project = repository.saveAndFlush(project);

        scheduleService.createInitialSchedule(project.getId(), project.getContractValue(), request.schedule());
        if (request.delayTerms() != null) {
            delayTermsService.createTerms(project.getId(), project.getType().name(), request.delayTerms());
        }
        // same wording the page used for its local feed
        activityService.record(project.getId(), ActivityKind.STATUS, "Project created in " + status.label() + ".");

        return mapper.toResponse(project, clientName);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
