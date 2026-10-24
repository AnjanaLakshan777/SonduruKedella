package com.sondurukedella.projects.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.clients.dto.ClientCreateRequest;
import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.projects.dto.ProjectCreateRequest;
import com.sondurukedella.projects.dto.ProjectFilter;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProjectRulesTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);

    @Test
    void aClientMustBeChosenOrEntered() {
        assertThatThrownBy(() -> ProjectRules.validateCreate(request(ProjectType.DESIGN, "1000", null, null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("Choose a client");
    }

    @Test
    void notBothAnExistingAndANewClient() {
        ClientCreateRequest newClient = new ClientCreateRequest("New", ClientKind.INDIVIDUAL, "0710000000",
                null, null, null, null);
        assertThatThrownBy(() -> ProjectRules.validateCreate(request(ProjectType.DESIGN, "1000", 1L, newClient)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("not both");
    }

    @ParameterizedTest
    @EnumSource(value = ProjectType.class, names = {"DESIGN", "CONSTRUCTION"})
    void designAndConstructionNeedAContractValue(ProjectType type) {
        assertThatThrownBy(() -> ProjectRules.validateCreate(request(type, "0", 1L, null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatCode(() -> ProjectRules.validateCreate(request(type, "0.01", 1L, null))).doesNotThrowAnyException();
    }

    @Test
    void aConsultationMayHaveNoFeeYetButNotANegativeOne() {
        assertThatCode(() -> ProjectRules.validateCreate(request(ProjectType.CONSULTATION, "0", 1L, null)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ProjectRules.validateCreate(request(ProjectType.CONSULTATION, "-1", 1L, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void aContractValueWithMoreThanTwoDecimalsIsRejectedNotRounded() {
        assertThatThrownBy(() -> ProjectRules.validateCreate(request(ProjectType.DESIGN, "1000.005", 1L, null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("2 decimal places");
        assertThatCode(() -> ProjectRules.validateCreate(request(ProjectType.DESIGN, "1000.500", 1L, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void expectedCompletionMustBeStrictlyAfterTheStart() {
        assertThatThrownBy(() -> ProjectRules.validateCreate(withDates(START, START)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> ProjectRules.validateCreate(withDates(START, START.minusDays(1))))
                .isInstanceOf(InvalidRequestException.class);
        assertThatCode(() -> ProjectRules.validateCreate(withDates(START, START.plusDays(1))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(ProjectStatus.class)
    void onlyOpenStatusesAreAllowedForANewProject(ProjectStatus status) {
        ProjectCreateRequest request = withStatus(status);
        if (status == ProjectStatus.COMPLETED || status == ProjectStatus.CANCELLED) {
            assertThatThrownBy(() -> ProjectRules.validateCreate(request)).isInstanceOf(InvalidRequestException.class);
        } else {
            assertThatCode(() -> ProjectRules.validateCreate(request)).doesNotThrowAnyException();
        }
    }

    @Test
    void theStatusDefaultsToPlanning() {
        assertThat(ProjectRules.initialStatus(withStatus(null))).isEqualTo(ProjectStatus.PLANNING);
        assertThat(ProjectRules.initialStatus(withStatus(ProjectStatus.ON_HOLD))).isEqualTo(ProjectStatus.ON_HOLD);
    }

    @Test
    void dateRangesMustNotBeBackwards() {
        assertThatThrownBy(() -> ProjectRules.validateFilter(new ProjectFilter(null, null, null, null,
                START, START.minusDays(1), null, null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> ProjectRules.validateFilter(new ProjectFilter(null, null, null, null,
                null, null, START, START.minusDays(1)))).isInstanceOf(InvalidRequestException.class);
        assertThatCode(() -> ProjectRules.validateFilter(new ProjectFilter(null, null, null, null,
                START, START, START, null))).doesNotThrowAnyException();
    }

    private static ProjectCreateRequest request(ProjectType type, String value, Long clientId,
            ClientCreateRequest newClient) {
        return new ProjectCreateRequest("Project", type, clientId, newClient, null, null, new BigDecimal(value),
                START, START.plusMonths(6), null, null, null, null);
    }

    private static ProjectCreateRequest withDates(LocalDate start, LocalDate end) {
        return new ProjectCreateRequest("Project", ProjectType.DESIGN, 1L, null, null, null, new BigDecimal("1000"),
                start, end, null, null, null, null);
    }

    private static ProjectCreateRequest withStatus(ProjectStatus status) {
        return new ProjectCreateRequest("Project", ProjectType.DESIGN, 1L, null, null, null, new BigDecimal("1000"),
                START, START.plusMonths(6), status, null, null, null);
    }
}
