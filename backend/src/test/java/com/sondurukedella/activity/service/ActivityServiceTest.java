package com.sondurukedella.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sondurukedella.activity.entity.ActivityEntry;
import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.repository.ActivityEntryRepository;
import com.sondurukedella.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityEntryRepository repository;

    @Test
    void theActivityDateIsTheColomboDate() {
        // 20:00 UTC is 01:30 the next morning in Sri Lanka
        Clock clock = Clock.fixed(Instant.parse("2026-12-31T20:00:00Z"), ClockConfig.BUSINESS_ZONE);
        ArgumentCaptor<ActivityEntry> entry = ArgumentCaptor.forClass(ActivityEntry.class);

        new ActivityService(repository, clock).record(4L, ActivityKind.STATUS, "Status changed.");

        verify(repository).save(entry.capture());
        assertThat(entry.getValue().getActivityDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(entry.getValue().getProjectId()).isEqualTo(4L);
        assertThat(entry.getValue().getKind()).isEqualTo(ActivityKind.STATUS);
        assertThat(entry.getValue().getMessage()).isEqualTo("Status changed.");
        assertThat(entry.getValue().getCreatedAt().toInstant()).isEqualTo(Instant.parse("2026-12-31T20:00:00Z"));
    }
}
