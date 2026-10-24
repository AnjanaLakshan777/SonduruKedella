package com.sondurukedella.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.clients.dto.ClientCreateRequest;
import com.sondurukedella.clients.dto.ClientResponse;
import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.clients.service.ClientService;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.projects.dto.ProjectCreateRequest;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.Project;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.mapper.ProjectMapper;
import com.sondurukedella.projects.repository.ProjectRepository;
import com.sondurukedella.revenue.dto.DelayTermsDraft;
import com.sondurukedella.revenue.dto.InstallmentDraft;
import com.sondurukedella.revenue.entity.DelayRule;
import com.sondurukedella.revenue.service.DelayTermsService;
import com.sondurukedella.revenue.service.InstallmentScheduleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectCreatorTest {

    private static final List<InstallmentDraft> SCHEDULE = List.of(
            new InstallmentDraft("Advance", LocalDate.of(2027, 2, 1), new BigDecimal("400000")),
            new InstallmentDraft("Handover", LocalDate.of(2027, 9, 1), new BigDecimal("600000")));

    @Mock
    private ProjectRepository repository;
    @Mock
    private ClientService clientService;
    @Mock
    private InstallmentScheduleService scheduleService;
    @Mock
    private DelayTermsService delayTermsService;
    @Mock
    private ActivityService activityService;

    private ProjectCreator creator;

    @BeforeEach
    void setUp() {
        creator = new ProjectCreator(repository, new ProjectMapper(), clientService, scheduleService,
                delayTermsService, activityService);
        when(clientService.exists(7L)).thenReturn(true);
        when(clientService.findNames(List.of(7L))).thenReturn(Map.of(7L, "K. M. Bandara"));
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(call -> {
            Project saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 50L);
            ReflectionTestUtils.setField(saved, "version", 0L);
            return saved;
        });
    }

    @Test
    void theCodeYearComesFromTheStartDateNotToday() {
        // created in 2026 but starting in 2027, so it's numbered in the 2027 series
        when(repository.findCodesStartingWith("PRJ-2027-%")).thenReturn(List.of("PRJ-2027-001", "PRJ-2027-002"));

        ProjectResponse created = creator.create(construction(7L, null, null));

        assertThat(created.code()).isEqualTo("PRJ-2027-003");
        InOrder order = inOrder(repository);
        order.verify(repository).lockCodeYear(ProjectCodes.LOCK_NAMESPACE, 2027);
        order.verify(repository).findCodesStartingWith("PRJ-2027-%");
        order.verify(repository).saveAndFlush(any(Project.class));
    }

    @Test
    void createsTheProjectThenItsScheduleDelayTermsAndActivity() {
        DelayTermsDraft delay = new DelayTermsDraft(LocalDate.of(2027, 9, 1), DelayRule.PER_DAY,
                new BigDecimal("15000"), null, null, 7, null, "Clause 14.2");

        ProjectResponse created = creator.create(construction(7L, null, delay));

        assertThat(created.id()).isEqualTo(50L);
        assertThat(created.clientName()).isEqualTo("K. M. Bandara");
        assertThat(created.status()).isEqualTo(ProjectStatus.PLANNING);
        assertThat(created.contractValue()).isEqualByComparingTo("1000000.00");
        verify(scheduleService).createInitialSchedule(eq(50L), eq(new BigDecimal("1000000.00")), eq(SCHEDULE));
        verify(delayTermsService).createTerms(50L, "CONSTRUCTION", delay);
        verify(activityService).record(50L, ActivityKind.STATUS, "Project created in Planning.");
    }

    @Test
    void anInlineClientIsCreatedThroughTheClientService() {
        ClientCreateRequest newClient = new ClientCreateRequest("Nirosha Herath", ClientKind.INDIVIDUAL,
                "076 904 1187", null, null, "Medawachchiya", null);
        when(clientService.create(newClient)).thenReturn(new ClientResponse(88L, "Nirosha Herath",
                ClientKind.INDIVIDUAL, "076 904 1187", null, null, "Medawachchiya", null,
                OffsetDateTime.now(), OffsetDateTime.now(), 0L));
        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);

        ProjectResponse created = creator.create(construction(null, newClient, null));

        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getClientId()).isEqualTo(88L);
        assertThat(created.clientName()).isEqualTo("Nirosha Herath");
        verify(clientService, never()).exists(anyLong());
    }

    @Test
    void anUnknownClientStopsBeforeAnythingIsWritten() {
        when(clientService.exists(99L)).thenReturn(false);

        assertThatThrownBy(() -> creator.create(construction(99L, null, null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(scheduleService, delayTermsService, activityService);
    }

    @Test
    void invalidRequestsAreRejectedBeforeTheClientIsCreated() {
        ClientCreateRequest newClient = new ClientCreateRequest("X", ClientKind.INDIVIDUAL, "0710000000",
                null, null, null, null);
        ProjectCreateRequest completed = new ProjectCreateRequest("Villa", ProjectType.DESIGN, null, newClient,
                null, null, new BigDecimal("1000"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1),
                ProjectStatus.COMPLETED, null, null, null);

        assertThatThrownBy(() -> creator.create(completed)).isInstanceOf(InvalidRequestException.class);
        verify(clientService, never()).create(any());
    }

    @Test
    void textIsTrimmedAndBlankOptionalFieldsAreStoredAsNull() {
        ProjectCreateRequest request = new ProjectCreateRequest("  Kekirawa Library ", ProjectType.CONSTRUCTION, 7L,
                null, "   ", " Two floors ", new BigDecimal("1000000"), LocalDate.of(2027, 1, 10),
                LocalDate.of(2027, 12, 1), ProjectStatus.IN_PROGRESS, "", SCHEDULE, null);

        ProjectResponse created = creator.create(request);

        assertThat(created.name()).isEqualTo("Kekirawa Library");
        assertThat(created.location()).isNull();
        assertThat(created.description()).isEqualTo("Two floors");
        assertThat(created.notes()).isNull();
        verify(activityService).record(50L, ActivityKind.STATUS, "Project created in In Progress.");
    }

    @Test
    void noDelayTermsMeansTheDelayServiceIsNotCalled() {
        creator.create(construction(7L, null, null));
        verifyNoInteractions(delayTermsService);
    }

    private static ProjectCreateRequest construction(Long clientId, ClientCreateRequest newClient, DelayTermsDraft delay) {
        return new ProjectCreateRequest("Nuwarawewa Lakeside Villa", ProjectType.CONSTRUCTION, clientId, newClient,
                "Nuwarawewa", null, new BigDecimal("1000000"), LocalDate.of(2027, 1, 10), LocalDate.of(2027, 9, 1),
                null, null, SCHEDULE, delay);
    }
}
