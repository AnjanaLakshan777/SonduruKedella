package com.sondurukedella.agreements.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sondurukedella.agreements.dto.AgreementResponse;
import com.sondurukedella.agreements.mapper.AgreementMapper;
import com.sondurukedella.agreements.repository.ProjectAgreementRepository;
import com.sondurukedella.agreements.storage.AgreementFileStore;
import com.sondurukedella.agreements.storage.AgreementStorageProperties;
import com.sondurukedella.agreements.storage.StoredFile;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.exception.StorageException;
import com.sondurukedella.projects.service.ProjectService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgreementServiceTest {

    private static final byte[] PDF = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
    private static final StoredFile STORED = new StoredFile("11111111-2222-3333-4444-555555555555.pdf", PDF.length, "a".repeat(64));

    @Mock
    private ProjectAgreementRepository repository;
    @Mock
    private AgreementRecorder recorder;
    @Mock
    private AgreementFileStore store;
    @Mock
    private ProjectService projectService;

    private AgreementService service;

    @BeforeEach
    void setUp() {
        service = new AgreementService(repository, new AgreementMapper(), recorder, store, projectService,
                new AgreementStorageProperties(Path.of("unused"), DataSize.ofMegabytes(20)));
        when(store.store(any(InputStream.class), anyLong())).thenReturn(STORED);
    }

    @Test
    void theFileIsStoredThenRecorded() {
        AgreementUpload done = new AgreementUpload(new AgreementResponse(1L, 4L, "a.pdf", "application/pdf",
                PDF.length, "a".repeat(64), OffsetDateTime.now(), 0L), false);
        when(recorder.record(4L, "a.pdf", STORED)).thenReturn(done);

        assertThat(service.upload(4L, pdf("a.pdf"))).isSameAs(done);
        verify(store, never()).deleteQuietly(anyString());
    }

    @Test
    void aDatabaseFailureDeletesTheNewFileAndKeepsTheError() {
        // e.g. someone replaced the agreement at the same moment (stale @Version on the old row)
        ObjectOptimisticLockingFailureException clash = new ObjectOptimisticLockingFailureException("ProjectAgreement", 1L);
        when(recorder.record(eq(4L), anyString(), eq(STORED))).thenThrow(clash);

        assertThatThrownBy(() -> service.upload(4L, pdf("a.pdf"))).isSameAs(clash);
        verify(store).deleteQuietly(STORED.key());
    }

    @Test
    void anyOtherFailureWhileRecordingAlsoCleansUp() {
        when(recorder.record(eq(4L), anyString(), eq(STORED)))
                .thenThrow(new DataIntegrityViolationException("uq_project_agreements_current"));

        assertThatThrownBy(() -> service.upload(4L, pdf("a.pdf"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(store).deleteQuietly(STORED.key());
    }

    @Test
    void aStorageFailureNeverReachesTheDatabase() {
        when(store.store(any(InputStream.class), anyLong())).thenThrow(new StorageException("disk full", null));

        assertThatThrownBy(() -> service.upload(4L, pdf("a.pdf"))).isInstanceOf(StorageException.class);
        verifyNoInteractions(recorder);
    }

    @Test
    void anUnknownProjectIsRejectedBeforeAnythingIsStored() {
        doThrow(new NotFoundException("Project not found.")).when(projectService).requireExists(9L);

        assertThatThrownBy(() -> service.upload(9L, pdf("a.pdf"))).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(store, recorder);
    }

    @Test
    void aWrongTypeIsRejectedBeforeAnythingIsStored() {
        MockMultipartFile word = new MockMultipartFile("file", "a.docx", "application/msword", PDF);

        assertThatThrownBy(() -> service.upload(4L, word)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(store, recorder);
    }

    @Test
    void theStoredNameIsTheCleanedNameNotThePathTheClientSent() {
        when(recorder.record(eq(4L), anyString(), eq(STORED))).thenReturn(new AgreementUpload(new AgreementResponse(1L,
                4L, "agreement.pdf", "application/pdf", PDF.length, "a".repeat(64), OffsetDateTime.now(), 0L), false));

        service.upload(4L, pdf("../../etc/agreement.pdf"));

        verify(recorder).record(4L, "agreement.pdf", STORED);
    }

    @Test
    void aProjectWithoutAnAgreementIs404() {
        when(repository.findByProjectIdAndRemovedAtIsNull(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(4L)).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No agreement");
        assertThatThrownBy(() -> service.download(4L)).isInstanceOf(NotFoundException.class);
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }
}
