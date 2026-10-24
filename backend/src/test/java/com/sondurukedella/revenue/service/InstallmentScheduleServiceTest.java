package com.sondurukedella.revenue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sondurukedella.revenue.dto.InstallmentResponse;
import com.sondurukedella.revenue.entity.Installment;
import com.sondurukedella.revenue.entity.InstallmentKind;
import com.sondurukedella.revenue.mapper.RevenueMapper;
import com.sondurukedella.revenue.repository.InstallmentRepository;
import com.sondurukedella.support.TestEntities;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstallmentScheduleServiceTest {

    @Mock
    private InstallmentRepository repository;

    @Test
    void billedIsDerivedFromBilledOn() {
        when(repository.findByProjectIdOrderByDueDateAscIdAsc(4L)).thenReturn(List.of(
                installment(1L, "Advance", "2026-02-01", "400000.00", LocalDate.of(2026, 2, 3)),
                installment(2L, "Handover", "2026-09-01", "600000.00", null)));

        List<InstallmentResponse> schedule =
                new InstallmentScheduleService(repository, new RevenueMapper()).findSchedule(4L);

        assertThat(schedule).extracting(InstallmentResponse::billed).containsExactly(true, false);
        assertThat(schedule.getFirst().billedOn()).isEqualTo(LocalDate.of(2026, 2, 3));
        assertThat(schedule.get(1).billedOn()).isNull();
        assertThat(schedule.get(1).amount()).isEqualByComparingTo("600000.00");
        assertThat(schedule).extracting(InstallmentResponse::kind).containsOnly(InstallmentKind.SCHEDULED);
    }

    private static Installment installment(long id, String description, String due, String amount, LocalDate billedOn) {
        Map<String, Object> fields = new HashMap<>(Map.of("id", id, "projectId", 4L, "description", description,
                "dueDate", LocalDate.parse(due), "amount", new BigDecimal(amount), "kind", InstallmentKind.SCHEDULED,
                "version", 0L));
        fields.put("billedOn", billedOn);
        return TestEntities.create(Installment.class, fields);
    }
}
