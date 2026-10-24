package com.sondurukedella.agreements.service;

import com.sondurukedella.agreements.dto.AgreementResponse;
import com.sondurukedella.agreements.entity.ProjectAgreement;
import com.sondurukedella.agreements.mapper.AgreementMapper;
import com.sondurukedella.agreements.repository.ProjectAgreementRepository;
import com.sondurukedella.agreements.storage.AgreementFileStore;
import com.sondurukedella.agreements.storage.AgreementStorageProperties;
import com.sondurukedella.agreements.storage.StoredFile;
import com.sondurukedella.agreements.validation.AgreementFiles;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.exception.StorageException;
import com.sondurukedella.projects.service.ProjectService;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// One current agreement PDF per project. Projects are checked through ProjectService, never the
// projects repository. Superseded PDFs are kept on disk: their rows still exist (removed_at set),
// and deleting the file would leave a row pointing at nothing.
@Slf4j
@Service
public class AgreementService {

    private final ProjectAgreementRepository repository;
    private final AgreementMapper mapper;
    private final AgreementRecorder recorder;
    private final AgreementFileStore store;
    private final ProjectService projectService;
    private final long maxBytes;

    AgreementService(ProjectAgreementRepository repository, AgreementMapper mapper, AgreementRecorder recorder,
            AgreementFileStore store, ProjectService projectService, AgreementStorageProperties properties) {
        this.repository = repository;
        this.mapper = mapper;
        this.recorder = recorder;
        this.store = store;
        this.projectService = projectService;
        this.maxBytes = properties.maxSize().toBytes();
    }

    public AgreementResponse getCurrent(long projectId) {
        return mapper.toResponse(current(projectId));
    }

    // Not @Transactional on purpose: the file is written first, outside any transaction, then the
    // metadata in AgreementRecorder's own transaction. If that fails the new file is deleted again,
    // and the old agreement - row and file - is exactly as it was.
    public AgreementUpload upload(long projectId, MultipartFile file) {
        projectService.requireExists(projectId);
        String fileName = AgreementFiles.safeFileName(file.getOriginalFilename());
        AgreementFiles.validateUpload(fileName, file.getContentType(), file.getSize(), maxBytes);

        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = store.store(in, maxBytes);
        } catch (IOException ex) {
            throw new StorageException("Could not read the uploaded file", ex);
        }

        try {
            AgreementUpload upload = recorder.record(projectId, fileName, stored);
            log.info("Agreement {} stored for project {}{}", upload.agreement().id(), projectId,
                    upload.replaced() ? " (replaced the previous one)" : "");
            return upload;
        } catch (RuntimeException ex) {
            store.deleteQuietly(stored.key());
            throw ex;
        }
    }

    public AgreementDownload download(long projectId) {
        ProjectAgreement agreement = current(projectId);
        // metadata without its file means storage was lost or tampered with - a server problem,
        // not "no agreement", so it's a 500 rather than a 404
        if (!store.exists(agreement.getStorageKey())) {
            throw new StorageException("Agreement " + agreement.getId() + " has metadata but no stored file", null);
        }
        return new AgreementDownload(agreement.getOriginalFilename(), agreement.getSizeBytes(),
                store.open(agreement.getStorageKey()));
    }

    private ProjectAgreement current(long projectId) {
        projectService.requireExists(projectId);
        return repository.findByProjectIdAndRemovedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException("No agreement has been uploaded for this project."));
    }
}
