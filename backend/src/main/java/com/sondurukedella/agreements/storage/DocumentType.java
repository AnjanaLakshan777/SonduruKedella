package com.sondurukedella.agreements.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;

// What the store accepts. The type is read from the file's first bytes, never from its name or the
// content type the browser sent, and the stored key carries the matching extension.
public enum DocumentType {
    PDF("pdf", MediaType.APPLICATION_PDF, List.of("pdf"), new byte[] {'%', 'P', 'D', 'F', '-'}),
    JPEG("jpg", MediaType.IMAGE_JPEG, List.of("jpg", "jpeg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("png", MediaType.IMAGE_PNG, List.of("png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

    // agreements are signed contracts: PDF only. Receipts and bills are often phone photos.
    public static final Set<DocumentType> PDF_ONLY = Set.of(PDF);
    public static final Set<DocumentType> RECEIPTS = Set.of(PDF, JPEG, PNG);

    // the longest signature, so the store knows how many bytes to look at
    static final int HEADER_LENGTH = 8;

    private final String keyExtension;
    private final MediaType mediaType;
    private final List<String> nameExtensions;
    private final byte[] signature;

    DocumentType(String keyExtension, MediaType mediaType, List<String> nameExtensions, byte[] signature) {
        this.keyExtension = keyExtension;
        this.mediaType = mediaType;
        this.nameExtensions = nameExtensions;
        this.signature = signature;
    }

    public String keyExtension() {
        return keyExtension;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public List<String> nameExtensions() {
        return nameExtensions;
    }

    static Optional<DocumentType> detect(byte[] header, long size) {
        return Arrays.stream(values())
                .filter(t -> size >= t.signature.length && Arrays.equals(header, 0, t.signature.length, t.signature, 0, t.signature.length))
                .findFirst();
    }

    // for a download: the key was written by the store, so its extension is the real type
    public static DocumentType ofKey(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.keyExtension.equals(extension)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stored file type: " + key));
    }

    public static Optional<DocumentType> ofFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.nameExtensions.stream().anyMatch(e -> lower.endsWith("." + e))).findFirst();
    }
}
