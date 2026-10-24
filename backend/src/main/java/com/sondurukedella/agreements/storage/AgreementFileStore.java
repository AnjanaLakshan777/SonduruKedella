package com.sondurukedella.agreements.storage;

import com.sondurukedella.agreements.validation.AgreementFiles;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Local-disk store for agreement PDFs. Knows nothing about projects or the database: it writes,
// opens and deletes files by a key it generated itself. Keys are random UUIDs, so nothing the
// client sends ever ends up in a path.
@Slf4j
@Component
public class AgreementFileStore {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    // the only shape of key we ever create; anything else is refused before touching the disk
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path root;

    public AgreementFileStore(AgreementStorageProperties properties) {
        this.root = properties.storageDir().toAbsolutePath().normalize();
    }

    // Streams the upload to a temp file (never the whole thing in memory), hashing and counting as
    // it goes, and only then moves it into place under a new key. On any failure the temp file is
    // removed, so a rejected or broken upload leaves nothing behind.
    public StoredFile store(InputStream in, long maxBytes) {
        createRoot();
        String key = UUID.randomUUID() + ".pdf";
        Path temp = root.resolve(".upload-" + UUID.randomUUID() + ".tmp");
        try {
            long size;
            String sha256;
            try (OutputStream out = Files.newOutputStream(temp)) {
                MessageDigest digest = sha256();
                byte[] buffer = new byte[BUFFER_SIZE];
                byte[] header = new byte[PDF_SIGNATURE.length];
                size = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (size < header.length) {
                        System.arraycopy(buffer, 0, header, (int) size, (int) Math.min(read, header.length - size));
                    }
                    size += read;
                    if (size > maxBytes) {
                        // stop reading right away rather than writing gigabytes we'll throw out
                        throw AgreementFiles.tooLarge(maxBytes);
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
                if (size == 0) {
                    throw new InvalidRequestException("The file is empty.");
                }
                // the name and content type can say anything; the bytes have to start like a PDF
                if (size < header.length || !Arrays.equals(header, PDF_SIGNATURE)) {
                    throw new InvalidRequestException("The file is not a valid PDF.");
                }
                sha256 = HexFormat.of().formatHex(digest.digest());
            }
            moveIntoPlace(temp, resolve(key));
            return new StoredFile(key, size, sha256);
        } catch (IOException ex) {
            throw new StorageException("Could not write the uploaded file", ex);
        } finally {
            deleteQuietly(temp);
        }
    }

    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException ex) {
            throw new StorageException("Could not open stored agreement " + key, ex);
        }
    }

    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    // Used to undo a store whose metadata never made it into the database. Never throws: the
    // original failure is the one worth reporting, an orphaned file is only logged.
    public void deleteQuietly(String key) {
        try {
            deleteQuietly(resolve(key));
        } catch (StorageException ex) {
            log.warn("Refusing to delete agreement with invalid key {}", key);
        }
    }

    // Keys come from our own database, but check them anyway: a bad key must never reach outside
    // the storage directory (../, absolute paths, other files).
    Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new StorageException("Invalid agreement storage key", null);
        }
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root)) {
            throw new StorageException("Agreement storage key escapes the storage directory", null);
        }
        return path;
    }

    Path root() {
        return root;
    }

    private void createRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new StorageException("Could not create the agreement storage directory", ex);
        }
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        // same directory, so the move is a rename: the file appears complete or not at all
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete agreement file {}", path.getFileName(), ex);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", ex);
        }
    }
}
