package com.sondurukedella.agreements.validation;

import com.sondurukedella.agreements.entity.ProjectAgreement;
import com.sondurukedella.agreements.storage.DocumentType;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.PayloadTooLargeException;
import java.util.Locale;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

// Checks on what the client says about the upload. The file's actual bytes (the %PDF- signature
// and the real size) are checked by AgreementFileStore while it streams them.
public final class AgreementFiles {

    public static final String PDF_CONTENT_TYPE = MediaType.APPLICATION_PDF_VALUE;
    private static final String FALLBACK_NAME = "agreement.pdf";

    private AgreementFiles() {
    }

    public static void validateUpload(String fileName, String declaredContentType, long declaredSize, long maxBytes) {
        if (declaredSize <= 0) {
            throw new InvalidRequestException("The file is empty.");
        }
        // cheap early reject; the store re-checks the real byte count in case the client lied
        if (declaredSize > maxBytes) {
            throw tooLarge(maxBytes);
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidRequestException("Only PDF files can be uploaded.");
        }
        if (!isContentType(declaredContentType, MediaType.APPLICATION_PDF)) {
            throw new InvalidRequestException("Only PDF files can be uploaded (content type must be application/pdf).");
        }
    }

    // Receipts and bills (expenses, supplier invoices): PDF, JPG or PNG. Returns the type the name
    // says, which the store then holds the bytes to - a photo renamed to .pdf is refused.
    public static DocumentType validateReceiptUpload(String fileName, String declaredContentType, long declaredSize, long maxBytes) {
        if (declaredSize <= 0) {
            throw new InvalidRequestException("The file is empty.");
        }
        if (declaredSize > maxBytes) {
            throw tooLarge(maxBytes);
        }
        DocumentType type = DocumentType.ofFileName(fileName).filter(DocumentType.RECEIPTS::contains)
                .orElseThrow(() -> new InvalidRequestException("Only PDF, JPG or PNG files can be uploaded."));
        if (!isContentType(declaredContentType, type.mediaType())) {
            throw new InvalidRequestException("The file's content type doesn't match a " + type.keyExtension().toUpperCase(Locale.ROOT) + " file.");
        }
        return type;
    }

    public static PayloadTooLargeException tooLarge(long maxBytes) {
        return new PayloadTooLargeException("The file is too large. The limit is " + (maxBytes / (1024 * 1024)) + " MB.");
    }

    // The client's file name is only ever metadata and the download name - it is never used to
    // build a path. Still, strip anything that could confuse a browser or a header: directories
    // (C:\x\y.pdf from old IE, ../ tricks), control characters including CR/LF, and quotes.
    public static String safeFileName(String original) {
        if (original == null) {
            return FALLBACK_NAME;
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty() || name.equals(".pdf") || name.startsWith(".")) {
            return FALLBACK_NAME;
        }
        if (name.length() > ProjectAgreement.FILENAME_MAX_LENGTH) {
            // keep the extension so the extension check and the download still make sense
            int dot = name.lastIndexOf('.');
            String extension = dot >= 0 && name.length() - dot <= 5 ? name.substring(dot) : "";
            name = name.substring(0, ProjectAgreement.FILENAME_MAX_LENGTH - extension.length()) + extension;
        }
        return name;
    }

    private static boolean isContentType(String declared, MediaType expected) {
        if (declared == null) {
            return false;
        }
        try {
            // ignores parameters like ";charset=..." but not the type itself
            return expected.equalsTypeAndSubtype(MediaType.parseMediaType(declared));
        } catch (InvalidMediaTypeException ex) {
            return false;
        }
    }
}
