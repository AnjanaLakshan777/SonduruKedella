package com.sondurukedella.agreements.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.agreements.entity.ProjectAgreement;
import com.sondurukedella.agreements.mapper.AgreementMapper;
import com.sondurukedella.agreements.repository.ProjectAgreementRepository;
import com.sondurukedella.agreements.storage.StoredFile;
import com.sondurukedella.agreements.validation.AgreementFiles;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// The database half of an upload, in its own bean so AgreementService can clean up the stored
// file if this transaction fails - including a failure at commit, which happens in the proxy.
@Component
@RequiredArgsConstructor
class AgreementRecorder {

    private final ProjectAgreementRepository repository;
    private final AgreementMapper mapper;
    private final ActivityService activityService;
    private final Clock clock;

    @Transactional
    public AgreementUpload record(long projectId, String fileName, StoredFile stored) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<ProjectAgreement> current = repository.findByProjectIdAndRemovedAtIsNull(projectId);
        // Retire the old row first and flush, so the one-current-agreement index sees it before the
        // insert. If someone else replaced it meanwhile, @Version makes this fail with a 409.
        current.ifPresent(old -> {
            old.markRemoved(now);
            repository.saveAndFlush(old);
        });
        ProjectAgreement saved = repository.saveAndFlush(new ProjectAgreement(projectId, fileName,
                AgreementFiles.PDF_CONTENT_TYPE, stored.size(), stored.sha256(), stored.key(), now));
        // same wording as the page's local feed
        activityService.record(projectId, ActivityKind.DOCUMENT,
                (current.isPresent() ? "Agreement replaced: " : "Agreement uploaded: ") + fileName);
        return new AgreementUpload(mapper.toResponse(saved), current.isPresent());
    }

    // Same as replace but with no new file - the row gets removed_at and the PDF stays since the row
    // still points to it.
    @Transactional
    public void remove(long projectId) {
        ProjectAgreement current = repository.findByProjectIdAndRemovedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException("No agreement has been uploaded for this project."));
        current.markRemoved(OffsetDateTime.now(clock));
        repository.saveAndFlush(current);
        activityService.record(projectId, ActivityKind.DOCUMENT, "Agreement removed.");
    }
}
