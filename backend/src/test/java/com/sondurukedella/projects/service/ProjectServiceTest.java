package com.sondurukedella.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.clients.service.ClientService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.config.ClockConfig;
import com.sondurukedella.projects.dto.ProjectCreateRequest;
import com.sondurukedella.projects.dto.ProjectFilter;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.dto.ProjectStatusUpdateRequest;
import com.sondurukedella.projects.entity.Project;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.mapper.ProjectMapper;
import com.sondurukedella.projects.repository.ProjectRepository;
import com.sondurukedella.support.TestEntities;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

    // 19:00 UTC on the 24th is already 00:30 on the 25th in Colombo - a UTC clock would get it wrong
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T19:00:00Z"), ClockConfig.BUSINESS_ZONE);
    private static final LocalDate COLOMBO_TODAY = LocalDate.of(2026, 10, 25);

    @Mock
    private ProjectRepository repository;
    @Mock
    private ProjectCreator creator;
    @Mock
    private ClientService clientService;
    @Mock
    private ActivityService activityService;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(repository, new ProjectMapper(), creator, clientService, activityService, CLOCK);
        when(clientService.findNames(anyCollection())).thenReturn(Map.of(7L, "Dilani Jayawardena"));
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(call -> call.getArgument(0));
    }

    static Stream<Arguments> everyStatusChange() {
        return Stream.of(ProjectStatus.values()).flatMap(from -> Stream.of(ProjectStatus.values())
                .filter(to -> to != from).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyStatusChange")
    void anyStatusCanChangeToAnyOther(ProjectStatus from, ProjectStatus to) {
        Project project = project(from, null, 2L);
        when(repository.findById(1L)).thenReturn(Optional.of(project));

        ProjectResponse result = service.changeStatus(1L, new ProjectStatusUpdateRequest(to, 2L));

        assertThat(result.status()).isEqualTo(to);
        verify(activityService).record(1L, ActivityKind.STATUS,
                "Status changed from " + from.label() + " to " + to.label() + ".");
    }

    @Test
    void completingSetsTodaysDateInColombo() {
        Project project = project(ProjectStatus.IN_PROGRESS, null, 0L);
        when(repository.findById(1L)).thenReturn(Optional.of(project));

        ProjectResponse result = service.changeStatus(1L, new ProjectStatusUpdateRequest(ProjectStatus.COMPLETED, 0L));

        assertThat(result.actualCompletion()).isEqualTo(COLOMBO_TODAY);
    }

    @Test
    void reopeningKeepsTheCompletionDate() {
        LocalDate finished = LocalDate.of(2026, 9, 30);
        for (ProjectStatus from : List.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED)) {
            Project project = project(from, finished, 0L);
            when(repository.findById(1L)).thenReturn(Optional.of(project));

            ProjectResponse result = service.changeStatus(1L,
                    new ProjectStatusUpdateRequest(ProjectStatus.IN_PROGRESS, 0L));

            assertThat(result.actualCompletion()).isEqualTo(finished);
        }
    }

    @Test
    void completingAgainKeepsTheFirstCompletionDate() {
        LocalDate finished = LocalDate.of(2026, 9, 30);
        Project project = project(ProjectStatus.IN_PROGRESS, finished, 4L);
        when(repository.findById(1L)).thenReturn(Optional.of(project));

        ProjectResponse result = service.changeStatus(1L, new ProjectStatusUpdateRequest(ProjectStatus.COMPLETED, 4L));

        assertThat(result.actualCompletion()).isEqualTo(finished);
    }

    @Test
    void cancellingDoesNotSetACompletionDate() {
        Project project = project(ProjectStatus.IN_PROGRESS, null, 0L);
        when(repository.findById(1L)).thenReturn(Optional.of(project));

        assertThat(service.changeStatus(1L, new ProjectStatusUpdateRequest(ProjectStatus.CANCELLED, 0L))
                .actualCompletion()).isNull();
    }

    @Test
    void settingTheSameStatusChangesNothing() {
        Project project = project(ProjectStatus.ON_HOLD, null, 3L);
        when(repository.findById(1L)).thenReturn(Optional.of(project));

        service.changeStatus(1L, new ProjectStatusUpdateRequest(ProjectStatus.ON_HOLD, 3L));

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(activityService);
    }

    @Test
    void aStaleVersionIsRejectedBeforeAnythingChanges() {
        Project project = project(ProjectStatus.PLANNING, null, 5L);
        when(repository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.changeStatus(1L,
                new ProjectStatusUpdateRequest(ProjectStatus.COMPLETED, 4L))).isInstanceOf(ConflictException.class);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.PLANNING);
        assertThat(project.getActualCompletion()).isNull();
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(activityService);
    }

    @Test
    void unknownProjectsAreNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.changeStatus(9L, new ProjectStatusUpdateRequest(ProjectStatus.ON_HOLD, 0L)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(activityService);
    }

    @Test
    void aCodeClashIsRetriedWithAFreshAttempt() {
        ProjectCreateRequest request = createRequest();
        ProjectResponse created = new ProjectMapper().toResponse(project(ProjectStatus.PLANNING, null, 0L), "x");
        when(creator.create(request)).thenThrow(codeClash()).thenReturn(created);

        assertThat(service.create(request)).isSameAs(created);
        verify(creator, times(2)).create(request);
    }

    @Test
    void retriesStopAfterTheLimitWithAConflict() {
        ProjectCreateRequest request = createRequest();
        when(creator.create(request)).thenThrow(codeClash());

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ConflictException.class);
        verify(creator, times(ProjectService.MAX_CODE_ATTEMPTS)).create(request);
    }

    @Test
    void otherIntegrityErrorsAreNotRetried() {
        ProjectCreateRequest request = createRequest();
        DataIntegrityViolationException other = new DataIntegrityViolationException("fk",
                new ConstraintViolationException("fk", new SQLException(), "projects_client_id_fkey"));
        when(creator.create(request)).thenThrow(other);

        assertThatThrownBy(() -> service.create(request)).isSameAs(other);
        verify(creator, times(1)).create(request);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listingDefaultsToNewestCodeFirstWithAnIdTieBreaker() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findAll(any(Specification.class), page.capture()))
                .thenReturn(new PageImpl<>(List.of(project(ProjectStatus.PLANNING, null, 0L))));

        ProjectResponse first = service.search(emptyFilter(), PageRequest.of(0, 10)).content().getFirst();

        assertThat(page.getValue().getSort()).containsExactly(Sort.Order.desc("code"), Sort.Order.asc("id"));
        assertThat(first.clientName()).isEqualTo("Dilani Jayawardena");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchLooksUpClientNamesThroughTheClientService() {
        when(clientService.findIdsByName("dilani")).thenReturn(List.of(7L));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(new ProjectFilter("dilani", null, null, null, null, null, null, null), PageRequest.of(0, 10));

        verify(clientService).findIdsByName("dilani");
    }

    @Test
    void unsupportedSortsAndHugePagesAreRejected() {
        // received/costs/outstanding need Revenue and Expenses, so they're not sortable yet
        for (String property : List.of("received", "costs", "receivable", "clientName")) {
            assertThatThrownBy(() -> service.search(emptyFilter(), PageRequest.of(0, 10, Sort.by(property))))
                    .isInstanceOf(InvalidRequestException.class);
        }
        assertThatThrownBy(() -> service.search(emptyFilter(), PageRequest.of(0, ProjectService.MAX_PAGE_SIZE + 1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static ProjectFilter emptyFilter() {
        return new ProjectFilter(null, null, null, null, null, null, null, null);
    }

    private static DataIntegrityViolationException codeClash() {
        return new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException(), "uq_projects_code"));
    }

    private static ProjectCreateRequest createRequest() {
        return new ProjectCreateRequest("Villa", ProjectType.DESIGN, 7L, null, null, null, new BigDecimal("1000"),
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 1), null, null, null, null);
    }

    private static Project project(ProjectStatus status, LocalDate actualCompletion, long version) {
        Map<String, Object> fields = new HashMap<>(Map.of(
                "id", 1L, "code", "PRJ-2026-001", "name", "Villa", "clientId", 7L, "type", ProjectType.CONSTRUCTION,
                "contractValue", new BigDecimal("1000.00"), "startDate", LocalDate.of(2026, 1, 1),
                "expectedCompletion", LocalDate.of(2026, 12, 31), "status", status, "version", version));
        fields.put("actualCompletion", actualCompletion);
        return TestEntities.create(Project.class, fields);
    }
}
