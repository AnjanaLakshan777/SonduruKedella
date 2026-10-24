package com.sondurukedella.activity.service;

import com.sondurukedella.activity.entity.ActivityEntry;
import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.repository.ActivityEntryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Write side only for now. The project feed endpoint comes with the Activity module proper.
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityEntryRepository repository;
    private final Clock clock;

    // MANDATORY: an entry only makes sense as part of the change it describes, so it must
    // commit or roll back with the caller's transaction rather than on its own.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long projectId, ActivityKind kind, String message) {
        repository.save(new ActivityEntry(projectId, LocalDate.now(clock), kind, message, OffsetDateTime.now(clock)));
    }
}
