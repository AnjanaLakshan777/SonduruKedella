package com.sondurukedella.projects.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.clients.service.ClientService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.projects.dto.ProjectCreateRequest;
import com.sondurukedella.projects.dto.ProjectFilter;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.dto.ProjectStatusUpdateRequest;
import com.sondurukedella.projects.entity.Project;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.mapper.ProjectMapper;
import com.sondurukedella.projects.repository.ProjectRepository;
import com.sondurukedella.projects.repository.ProjectSpecifications;
import com.sondurukedella.projects.validation.ProjectRules;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No general edit or delete: once created only the status changes. Type and contract value
// are fixed, and commissions, delay terms and stock issues depend on them.
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    public static final int MAX_PAGE_SIZE = 100;
    // Received, costs and outstanding aren't here - they need Revenue and Expenses.
    public static final Set<String> SORTABLE_PROPERTIES =
            Set.of("code", "name", "startDate", "expectedCompletion", "contractValue", "createdAt", "id");
    static final int MAX_CODE_ATTEMPTS = 3;

    private final ProjectRepository repository;
    private final ProjectMapper mapper;
    private final ProjectCreator creator;
    private final ClientService clientService;
    private final ActivityService activityService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> search(ProjectFilter filter, Pageable pageable) {
        ProjectRules.validateFilter(filter);
        List<Long> clientIds = clientService.findIdsByName(filter.q());
        Page<Project> page = repository.findAll(ProjectSpecifications.matching(filter, clientIds), stablePage(pageable));
        Map<Long, String> names = clientService.findNames(
                page.getContent().stream().map(Project::getClientId).collect(Collectors.toSet()));
        return PageResponse.of(page, project -> mapper.toResponse(project, names.get(project.getClientId())));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(long id) {
        return toResponse(load(id));
    }

    // Deliberately not @Transactional: each attempt needs a fresh transaction. The advisory lock
    // in ProjectCreator makes a clash unlikely; this covers what it can't (e.g. a code added by
    // hand). Must be called outside a transaction for the retry to work.
    public ProjectResponse create(ProjectCreateRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return creator.create(request);
            } catch (DataIntegrityViolationException ex) {
                if (!ProjectCodes.isCodeCollision(ex)) {
                    throw ex;
                }
                if (attempt >= MAX_CODE_ATTEMPTS) {
                    throw new ConflictException("Could not assign a project code. Please try again.");
                }
                log.warn("Project code clash on attempt {}, retrying", attempt);
            }
        }
    }

    // Any status can move to any other, including reopening a finished or cancelled project.
    @Transactional
    public ProjectResponse changeStatus(long id, ProjectStatusUpdateRequest request) {
        Project project = load(id);
        ConflictException.requireCurrentVersion(request.version(), project.getVersion());
        ProjectStatus from = project.getStatus();
        ProjectStatus to = request.status();
        if (from == to) {
            return toResponse(project);
        }
        project.setStatus(to);
        // Only fill an empty date. Reopening keeps it, and completing again keeps the first
        // date, so a quick reopen-and-close doesn't rewrite when the work actually finished.
        if (to == ProjectStatus.COMPLETED && project.getActualCompletion() == null) {
            project.setActualCompletion(LocalDate.now(clock));
        }
        project = repository.saveAndFlush(project);
        activityService.record(project.getId(), ActivityKind.STATUS,
                "Status changed from " + from.label() + " to " + to.label() + ".");
        return toResponse(project);
    }

    private Project load(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Project not found."));
    }

    private ProjectResponse toResponse(Project project) {
        return mapper.toResponse(project, clientService.findNames(List.of(project.getClientId())).get(project.getClientId()));
    }

    // Newest code first by default, like the projects page. id breaks ties so paging is stable.
    private static Pageable stablePage(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Projects cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: code, name, startDate, expectedCompletion, contractValue, createdAt, id.");
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Order.desc("code"));
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
