package com.sondurukedella.agreements.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.PayloadTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AgreementFilesTest {

    private static final long MAX = 20L * 1024 * 1024;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "../../etc/passwd.pdf|passwd.pdf",
            "..\\..\\Windows\\win.ini.pdf|win.ini.pdf",
            "C:\\Users\\owner\\Desktop\\Agreement PRJ-2026-001.pdf|Agreement PRJ-2026-001.pdf",
            "/var/tmp/signed.pdf|signed.pdf",
            "  Signed agreement.pdf  |Signed agreement.pdf"})
    void directoriesAreStrippedFromTheClientName(String original, String expected) {
        assertThat(AgreementFiles.safeFileName(original)).isEqualTo(expected);
    }

    @Test
    void headerBreakingCharactersAreRemoved() {
        // CR/LF could inject a header into Content-Disposition; quotes could end the filename early
        assertThat(AgreementFiles.safeFileName("evil\r\nSet-Cookie: x=1\".pdf")).isEqualTo("evilSet-Cookie: x=1.pdf");
        assertThat(AgreementFiles.safeFileName("tab\there.pdf")).isEqualTo("tabhere.pdf");
    }

    @Test
    void emptyOrHiddenNamesFallBackToAPlainName() {
        assertThat(AgreementFiles.safeFileName(null)).isEqualTo("agreement.pdf");
        assertThat(AgreementFiles.safeFileName("   ")).isEqualTo("agreement.pdf");
        assertThat(AgreementFiles.safeFileName(".pdf")).isEqualTo("agreement.pdf");
        assertThat(AgreementFiles.safeFileName("../")).isEqualTo("agreement.pdf");
        assertThat(AgreementFiles.safeFileName(".hidden.pdf")).isEqualTo("agreement.pdf");
    }

    @Test
    void longNamesAreCutButKeepTheirExtension() {
        String name = AgreementFiles.safeFileName("a".repeat(400) + ".PDF");
        assertThat(name).hasSize(255).endsWith(".PDF");
    }

    @Test
    void unicodeNamesAreKeptAsTheyAre() {
        // Sinhala names are fine; the download header encodes them
        assertThat(AgreementFiles.safeFileName("ගිවිසුම.pdf")).isEqualTo("ගිවිසුම.pdf");
    }

    @Test
    void aValidUploadPasses() {
        assertThatCode(() -> AgreementFiles.validateUpload("a.pdf", "application/pdf", 10, MAX)).doesNotThrowAnyException();
        assertThatCode(() -> AgreementFiles.validateUpload("A.PDF", "Application/PDF; name=a.pdf", 10, MAX))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyAndOversizedFilesAreRejected() {
        assertThatThrownBy(() -> AgreementFiles.validateUpload("a.pdf", "application/pdf", 0, MAX))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> AgreementFiles.validateUpload("a.pdf", "application/pdf", MAX + 1, MAX))
                .isInstanceOf(PayloadTooLargeException.class).hasMessageContaining("20 MB");
    }

    @ParameterizedTest
    @ValueSource(strings = {"agreement.docx", "agreement.pdf.exe", "agreement", "agreement.pdfx"})
    void onlyPdfExtensionsAreAccepted(String name) {
        assertThatThrownBy(() -> AgreementFiles.validateUpload(name, "application/pdf", 10, MAX))
                .isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/octet-stream", "text/html", "application/x-pdf", "pdf", "", "application/"})
    void theDeclaredTypeMustBeApplicationPdf(String type) {
        assertThatThrownBy(() -> AgreementFiles.validateUpload("a.pdf", type, 10, MAX))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void aMissingContentTypeIsRejected() {
        assertThatThrownBy(() -> AgreementFiles.validateUpload("a.pdf", null, 10, MAX))
                .isInstanceOf(InvalidRequestException.class);
    }
}
