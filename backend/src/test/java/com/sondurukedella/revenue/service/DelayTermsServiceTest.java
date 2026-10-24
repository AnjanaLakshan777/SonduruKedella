package com.sondurukedella.revenue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.revenue.dto.DelayTermsDraft;
import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import com.sondurukedella.revenue.entity.DelayRule;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import com.sondurukedella.revenue.repository.DelayDeductionTermsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DelayTermsServiceTest {

    @Mock
    private DelayDeductionTermsRepository repository;

    private DelayTermsService service;

    @BeforeEach
    void setUp() {
        service = new DelayTermsService(repository, new RevenueMapper());
    }

    @Test
    void onlyTheValueForTheChosenRuleIsKept() {
        when(repository.saveAndFlush(any(DelayDeductionTerms.class))).thenAnswer(call -> call.getArgument(0));
        // the form still holds a per-day rate from before the user switched to a fixed amount
        DelayTermsDraft draft = new DelayTermsDraft(LocalDate.of(2027, 9, 1), DelayRule.FIXED_AMOUNT,
                new BigDecimal("15000"), new BigDecimal("0.5"), new BigDecimal("250000"), null, null, "  Clause 9 ");

        DelayDeductionTerms terms = service.createTerms(3L, "CONSTRUCTION", draft);

        assertThat(terms.getFixedAmount()).isEqualByComparingTo("250000.00");
        assertThat(terms.getRatePerDay()).isNull();
        assertThat(terms.getPercentPerWeek()).isNull();
        assertThat(terms.getGraceDays()).isZero();
        assertThat(terms.isEnabled()).isTrue();
        assertThat(terms.getProjectType()).isEqualTo("CONSTRUCTION");
        assertThat(terms.getNotes()).isEqualTo("Clause 9");
        assertThat(terms.getAppliedAmount()).isNull();
    }

    @Test
    void termsForANonConstructionProjectAreRejectedNotDropped() {
        DelayTermsDraft draft = new DelayTermsDraft(LocalDate.of(2027, 9, 1), DelayRule.PER_DAY,
                BigDecimal.TEN, null, null, null, null, null);

        assertThatThrownBy(() -> service.createTerms(3L, "DESIGN", draft)).isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aProjectWithoutTermsReadsAsEmptyNotAsDefaults() {
        when(repository.findByProjectId(8L)).thenReturn(java.util.Optional.empty());

        assertThat(service.findTerms(8L)).isEmpty();
    }

    @Test
    void aProjectGetsOneSetOfTerms() {
        when(repository.existsByProjectId(3L)).thenReturn(true);
        DelayTermsDraft draft = new DelayTermsDraft(LocalDate.of(2027, 9, 1), DelayRule.PER_DAY,
                BigDecimal.TEN, null, null, null, null, null);

        assertThatThrownBy(() -> service.createTerms(3L, "CONSTRUCTION", draft))
                .isInstanceOf(BusinessRuleException.class);
    }
}
