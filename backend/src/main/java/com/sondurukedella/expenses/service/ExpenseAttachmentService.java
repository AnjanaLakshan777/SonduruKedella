package com.sondurukedella.expenses.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.agreements.storage.AgreementFileStore;
import com.sondurukedella.agreements.storage.AgreementStorageProperties;
import com.sondurukedella.agreements.storage.DocumentType;
import com.sondurukedella.agreements.storage.StoredFile;
import com.sondurukedella.agreements.validation.AgreementFiles;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.exception.StorageException;
import com.sondurukedella.expenses.entity.Expense;
import com.sondurukedella.expenses.repository.ExpenseRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

// The receipt or bill of a manual expense: a PDF, JPG or PNG. Same private store as agreements and
// supplier invoices; the bytes have to match the type the name says.
// File first, then the row; the old file goes once the new one is saved.
@Service
@RequiredArgsConstructor
public class ExpenseAttachmentService {

    private final ExpenseRepository expenses;
    private final AgreementFileStore fileStore;
    private final AgreementStorageProperties storageProperties;
    private final TransactionTemplate transactions;
    private final ActivityService activityService;

    public void attach(long id, MultipartFile file) {
        if (!expenses.existsById(id)) {
            throw ExpensePaymentService.expenseNotFound();
        }
        long maxBytes = storageProperties.maxSize().toBytes();
        String fileName = AgreementFiles.safeFileName(file.getOriginalFilename());
        DocumentType type = AgreementFiles.validateReceiptUpload(fileName, file.getContentType(), file.getSize(), maxBytes);
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStore.store(in, maxBytes, Set.of(type));
        } catch (IOException ex) {
            throw new StorageException("Could not read the uploaded file", ex);
        }
        String replaced;
        try {
            replaced = transactions.execute(status -> {
                Expense expense = expenses.findByIdForUpdate(id).orElseThrow(ExpensePaymentService::expenseNotFound);
                String old = expense.getAttachmentStorageKey();
                expense.attach(stored.key(), fileName, stored.size());
                expenses.saveAndFlush(expense);
                activityService.record(expense.getProjectId(), ActivityKind.DOCUMENT,
                        "Receipt attached to expense \"" + expense.getDescription() + "\": " + fileName);
                return old;
            });
        } catch (RuntimeException ex) {
            fileStore.deleteQuietly(stored.key());
            throw ex;
        }
        if (replaced != null) {
            fileStore.deleteQuietly(replaced);
        }
    }

    @Transactional(readOnly = true)
    public AttachmentDownload attachment(long id) {
        Expense expense = expenses.findById(id).orElseThrow(ExpensePaymentService::expenseNotFound);
        if (expense.getAttachmentStorageKey() == null) {
            throw new NotFoundException("No file is attached to this expense.");
        }
        if (!fileStore.exists(expense.getAttachmentStorageKey())) {
            throw new StorageException("Expense " + id + " has attachment metadata but no stored file", null);
        }
        return new AttachmentDownload(expense.getAttachmentFilename(), expense.getAttachmentSize(),
                DocumentType.ofKey(expense.getAttachmentStorageKey()).mediaType(), fileStore.open(expense.getAttachmentStorageKey()));
    }

    // caller closes the stream (Spring does it when writing the response)
    public record AttachmentDownload(String fileName, long size, MediaType contentType, InputStream content) {
    }
}
