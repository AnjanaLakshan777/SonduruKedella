package com.sondurukedella.agreements.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.PayloadTooLargeException;
import com.sondurukedella.common.exception.StorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class AgreementFileStoreTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path dir;

    private AgreementFileStore store;

    @BeforeEach
    void setUp() {
        store = new AgreementFileStore(new AgreementStorageProperties(dir.resolve("agreements"), DataSize.ofMegabytes(20)));
    }

    @Test
    void aPdfIsStoredUnderAServerGeneratedKeyWithItsRealSizeAndHash() throws Exception {
        StoredFile stored = store.store(new ByteArrayInputStream(PDF), 1024);

        assertThat(stored.key()).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(stored.size()).isEqualTo(PDF.length);
        assertThat(stored.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF)));
        assertThat(Files.readAllBytes(store.root().resolve(stored.key()))).isEqualTo(PDF);
        try (InputStream in = store.open(stored.key())) {
            assertThat(in.readAllBytes()).isEqualTo(PDF);
        }
        assertThat(files()).containsExactly(stored.key()); // no temp file left behind
    }

    @Test
    void theStorageDirectoryIsCreatedOnFirstUseNotAtStartup() {
        assertThat(store.root()).doesNotExist();
        store.store(new ByteArrayInputStream(PDF), 1024);
        assertThat(store.root()).isDirectory();
    }

    @Test
    void contentThatIsNotAPdfIsRejectedWhateverItIsCalled() {
        assertThatThrownBy(() -> store.store(stream("<html><script>alert(1)</script></html>"), 1024))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("not a valid PDF");
        assertThatThrownBy(() -> store.store(stream("%PD"), 1024))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("not a valid PDF");
        assertThatThrownBy(() -> store.store(stream(" %PDF-1.7 leading space"), 1024))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(files()).isEmpty();
    }

    @Test
    void anEmptyStreamIsRejected() {
        assertThatThrownBy(() -> store.store(stream(""), 1024))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("empty");
        assertThat(files()).isEmpty();
    }

    @Test
    void anOversizedStreamIsCutOffWithoutReadingItAll() {
        // pretends to be endless; the store must stop soon after the limit instead of draining it
        CountingPdfStream endless = new CountingPdfStream();

        assertThatThrownBy(() -> store.store(endless, 100_000)).isInstanceOf(PayloadTooLargeException.class);

        assertThat(endless.served).isLessThan(100_000 + 64 * 1024 + 1);
        assertThat(files()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.pdf", "..\\outside.pdf", "/etc/passwd", "C:\\Windows\\win.ini", "agreement.pdf",
            "00000000-0000-0000-0000-000000000000.pdf/../../x.pdf", "00000000-0000-0000-0000-000000000000.PDF",
            "00000000-0000-0000-0000-000000000000.exe", ""})
    void keysThatCouldEscapeTheDirectoryAreRefused(String key) {
        assertThatThrownBy(() -> store.open(key)).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> store.exists(key)).isInstanceOf(StorageException.class);
        assertThatCode(() -> store.deleteQuietly(key)).doesNotThrowAnyException();
    }

    @Test
    void aStorageDirectoryThatCannotBeCreatedIsAStorageError() throws IOException {
        Path blocker = Files.writeString(dir.resolve("not-a-directory"), "x");
        AgreementFileStore broken = new AgreementFileStore(
                new AgreementStorageProperties(blocker.resolve("agreements"), DataSize.ofMegabytes(20)));

        assertThatThrownBy(() -> broken.store(new ByteArrayInputStream(PDF), 1024)).isInstanceOf(StorageException.class);
    }

    @Test
    void deletingRemovesTheFileAndToleratesOneThatIsAlreadyGone() {
        StoredFile stored = store.store(new ByteArrayInputStream(PDF), 1024);

        store.deleteQuietly(stored.key());
        assertThat(store.exists(stored.key())).isFalse();
        assertThatCode(() -> store.deleteQuietly(stored.key())).doesNotThrowAnyException();
    }

    private List<String> files() {
        if (!Files.exists(store.root())) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(store.root())) {
            return paths.map(p -> p.getFileName().toString()).toList();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
    }

    // a valid PDF header followed by an infinite stream of filler bytes
    private static final class CountingPdfStream extends InputStream {
        long served;

        @Override
        public int read() {
            return served < PDF.length ? PDF[(int) served++] : (int) (served++ & 0x7f);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            for (int i = 0; i < len; i++) {
                b[off + i] = (byte) read();
            }
            return len;
        }
    }
}
