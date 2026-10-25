package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.agreements.service.AgreementService;
import com.sondurukedella.agreements.storage.AgreementFileStore;
import com.sondurukedella.common.exception.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Real files and real commits: a test-managed transaction would hide whether the database and the
// disk stay in step. Storage goes to a temp directory, max size is 64 KB so "too large" is cheap,
// and every row this creates (codes AGTEST-*) is deleted afterwards. Throwaway DB only.
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class AgreementStorageIntegrationTest {

    private static final Path STORAGE = tempDir();
    private static final byte[] PDF_V1 = pdf("first agreement");
    private static final byte[] PDF_V2 = pdf("signed replacement");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("agreements.storage-dir", STORAGE::toString);
        registry.add("agreements.max-size", () -> "64KB");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AgreementService agreementService;

    @MockitoSpyBean
    private ActivityService activityService;

    @MockitoSpyBean
    private AgreementFileStore fileStore;

    private long project;
    private long otherProject;

    @BeforeEach
    void createProjects() {
        long client = jdbc.queryForObject(
                "INSERT INTO clients (name, phone) VALUES ('AgTest Client', '0710000000') RETURNING id", Long.class);
        project = project(client, "AGTEST-1");
        otherProject = project(client, "AGTEST-2");
    }

    @AfterEach
    void cleanUp() {
        String projects = "SELECT id FROM projects WHERE code LIKE 'AGTEST-%'";
        jdbc.update("DELETE FROM activity_log WHERE project_id IN (" + projects + ")");
        jdbc.update("DELETE FROM project_agreements WHERE project_id IN (" + projects + ")");
        jdbc.update("DELETE FROM projects WHERE code LIKE 'AGTEST-%'");
        jdbc.update("DELETE FROM clients WHERE name LIKE 'AgTest%'");
        jdbc.update("DELETE FROM supplier_invoices WHERE supplier_id IN (SELECT id FROM suppliers WHERE name LIKE 'AgTest%')");
        jdbc.update("DELETE FROM suppliers WHERE name LIKE 'AgTest%'");
        // the directory is shared by the whole class, so empty it for the next test's file checks
        try (Stream<Path> paths = Files.list(STORAGE)) {
            paths.forEach(p -> p.toFile().delete());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @AfterAll
    static void deleteStorage() throws IOException {
        try (Stream<Path> paths = Files.walk(STORAGE)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void anAgreementCanBeRemovedAndItsFileIsKept() throws Exception {
        upload(project, "contract.pdf", PDF_V1).andExpect(status().isCreated());
        String key = (String) currentRow(project).get("storage_key");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url(project)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url(project))).andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url(project)).with(csrf()))
                .andExpect(status().isNotFound());
        // retired, not deleted: the row (removed_at set) and its PDF stay
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_agreements WHERE project_id = ? AND removed_at IS NOT NULL",
                Long.class, project)).isOne();
        assertThat(STORAGE.resolve(key)).exists();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE project_id = ? AND message = 'Agreement removed.'",
                Long.class, project)).isOne();
        // and a new one can be uploaded afterwards
        upload(project, "signed.pdf", PDF_V2).andExpect(status().isCreated());
    }

    @Test
    void aSupplierInvoiceCanCarryAPrivatePdf() throws Exception {
        long supplier = jdbc.queryForObject("INSERT INTO suppliers (name, phone) VALUES ('AgTest Shop', '0710000000') RETURNING id", Long.class);
        long invoice = jdbc.queryForObject("""
                INSERT INTO supplier_invoices (supplier_id, invoice_no, invoice_date, due_date, allocation, total_amount)
                VALUES (?, 'ATT-1', DATE '2026-10-01', DATE '2026-10-31', 'STOCK', 1000) RETURNING id""", Long.class, supplier);
        String attachment = "/api/v1/supplier-invoices/" + invoice + "/attachment";
        mockMvc.perform(get(attachment + "/download")).andExpect(status().isNotFound());
        mockMvc.perform(multipart(HttpMethod.PUT, attachment).file(new MockMultipartFile("file", "bill.txt", "text/plain", "hello".getBytes()))
                .with(csrf())).andExpect(status().isBadRequest());
        mockMvc.perform(multipart(HttpMethod.PUT, attachment).file(new MockMultipartFile("file", "bill.pdf", "application/pdf", PDF_V1)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoice.attachmentFilename").value("bill.pdf"));
        String firstKey = jdbc.queryForObject("SELECT attachment_storage_key FROM supplier_invoices WHERE id = ?", String.class, invoice);
        mockMvc.perform(get(attachment + "/download")).andExpect(status().isOk()).andExpect(content().bytes(PDF_V1))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        // replacing it removes the old file
        mockMvc.perform(multipart(HttpMethod.PUT, attachment).file(new MockMultipartFile("file", "bill2.pdf", "application/pdf", PDF_V2)).with(csrf()))
                .andExpect(status().isOk());
        assertThat(STORAGE.resolve(firstKey)).doesNotExist();
        mockMvc.perform(get(attachment + "/download")).andExpect(content().bytes(PDF_V2));
    }

    @Test
    void anUploadedAgreementCanBeReadAndDownloaded() throws Exception {
        upload(project, "Agreement PRJ-2026-001.pdf", PDF_V1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(project))
                .andExpect(jsonPath("$.fileName").value("Agreement PRJ-2026-001.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.size").value(PDF_V1.length))
                .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(content().string(not(containsString(STORAGE.getFileName().toString()))))
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        mockMvc.perform(get(url(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Agreement PRJ-2026-001.pdf"));
        mockMvc.perform(get(url(project) + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''Agreement%20PRJ-2026-001.pdf")))
                .andExpect(content().bytes(PDF_V1));

        Map<String, Object> row = currentRow(project);
        assertThat(Files.readAllBytes(STORAGE.resolve((String) row.get("storage_key")))).isEqualTo(PDF_V1);
        assertThat(activity(project)).containsExactly("Agreement uploaded: Agreement PRJ-2026-001.pdf");
    }

    @Test
    void replacingKeepsTheOldRowAndFileButServesTheNewOne() throws Exception {
        upload(project, "draft.pdf", PDF_V1).andExpect(status().isCreated());
        String oldKey = (String) currentRow(project).get("storage_key");

        upload(project, "signed.pdf", PDF_V2).andExpect(status().isOk()).andExpect(jsonPath("$.fileName").value("signed.pdf"));

        mockMvc.perform(get(url(project) + "/download")).andExpect(content().bytes(PDF_V2));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT original_filename, removed_at, version FROM project_agreements WHERE project_id = ? ORDER BY id", project);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("original_filename")).isEqualTo("draft.pdf");
        assertThat(rows.get(0).get("removed_at")).isNotNull();
        assertThat(rows.get(0).get("version")).isEqualTo(1L);
        assertThat(rows.get(1).get("removed_at")).isNull();
        // the superseded file is kept; its row still points at it
        assertThat(STORAGE.resolve(oldKey)).exists();
        assertThat(activity(project)).containsExactly("Agreement uploaded: draft.pdf", "Agreement replaced: signed.pdf");
    }

    @Test
    void invalidFilesAreRejectedAndLeaveNothingBehind() throws Exception {
        upload(project, "agreement.docx", PDF_V1).andExpect(status().isBadRequest());
        uploadAs(project, "agreement.pdf", "application/octet-stream", PDF_V1).andExpect(status().isBadRequest());
        upload(project, "agreement.pdf", "<html>not a pdf</html>".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is not a valid PDF."));
        upload(project, "agreement.pdf", new byte[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is empty."));
        byte[] big = Arrays.copyOf(PDF_V1, 65 * 1024);
        upload(project, "agreement.pdf", big).andExpect(status().is(413)).andExpect(jsonPath("$.status").value(413));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_agreements WHERE project_id = ?", Long.class, project))
                .isZero();
        assertThat(storedFiles()).isEmpty(); // not even temp files
    }

    @Test
    void missingProjectsAndMissingAgreementsAre404() throws Exception {
        long missing = Long.MAX_VALUE;
        mockMvc.perform(get(url(missing))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Project not found."));
        mockMvc.perform(get(url(missing) + "/download")).andExpect(status().isNotFound());
        upload(missing, "a.pdf", PDF_V1).andExpect(status().isNotFound());

        mockMvc.perform(get(url(project))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No agreement has been uploaded for this project."));
        mockMvc.perform(get(url(project) + "/download")).andExpect(status().isNotFound());
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void oneProjectsAgreementIsNeverServedForAnother() throws Exception {
        upload(project, "mine.pdf", PDF_V1).andExpect(status().isCreated());

        mockMvc.perform(get(url(otherProject) + "/download")).andExpect(status().isNotFound());
        mockMvc.perform(get(url(otherProject))).andExpect(status().isNotFound());
    }

    @Test
    void traversalNamesNeverReachThePathAndAreCleanedForDownload() throws Exception {
        upload(project, "../../../etc/cron.d/evil.pdf", PDF_V1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("evil.pdf"));
        upload(otherProject, "x\r\nSet-Cookie: a=b\".pdf", PDF_V2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("xSet-Cookie: a=b.pdf"));

        mockMvc.perform(get(url(otherProject) + "/download"))
                .andExpect(header().string("Content-Disposition", not(containsString("\r"))))
                .andExpect(header().string("Content-Disposition", not(containsString("\n"))))
                .andExpect(header().doesNotExist("Set-Cookie"));
        // only server-generated keys, all directly inside the storage directory
        assertThat(storedFiles()).hasSize(2).allMatch(name -> name.matches("[0-9a-f-]{36}\\.pdf"));
        try (Stream<Path> all = Files.walk(STORAGE)) {
            assertThat(all.filter(Files::isRegularFile)).allMatch(p -> p.getParent().equals(STORAGE));
        }
    }

    @Test
    void aDatabaseFailureDuringReplacementKeepsTheOldAgreementIntact() throws Exception {
        upload(project, "original.pdf", PDF_V1).andExpect(status().isCreated());
        Map<String, Object> before = currentRow(project);
        // the activity entry is the last write in the transaction, so everything before it is rolled back
        ActivityService spy = AopTestUtils.getUltimateTargetObject(activityService);
        doThrow(new IllegalStateException("database down")).when(spy).record(any(), any(), any());

        assertThatThrownBy(() -> agreementService.upload(project,
                new MockMultipartFile("file", "replacement.pdf", "application/pdf", PDF_V2)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(currentRow(project)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_agreements WHERE project_id = ?", Long.class, project))
                .isEqualTo(1L);
        assertThat(storedFiles()).containsExactly((String) before.get("storage_key")); // new file cleaned up
        mockMvc.perform(get(url(project) + "/download")).andExpect(content().bytes(PDF_V1));
    }

    @Test
    void aStorageFailureChangesNoMetadata() throws Exception {
        upload(project, "original.pdf", PDF_V1).andExpect(status().isCreated());
        Map<String, Object> before = currentRow(project);
        doThrow(new StorageException("disk full", new IOException("No space left on device")))
                .when(fileStore).store(any(InputStream.class), anyLong());

        upload(project, "replacement.pdf", PDF_V2)
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("No space"))));

        assertThat(currentRow(project)).isEqualTo(before);
        assertThat(storedFiles()).containsExactly((String) before.get("storage_key"));
        mockMvc.perform(get(url(project) + "/download")).andExpect(content().bytes(PDF_V1));
    }

    @Test
    void storedFilesAreNotServedAsStaticContent() throws Exception {
        upload(project, "private.pdf", PDF_V1).andExpect(status().isCreated());
        String key = (String) currentRow(project).get("storage_key");

        // nothing maps the storage directory, and nothing outside /api is allowed anyway: even a
        // signed-in user gets a refusal, never the bytes
        for (String path : List.of("/" + key, "/data/agreements/" + key, "/agreements/" + key, "/static/" + key)) {
            mockMvc.perform(get(path))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(not(containsString("%PDF"))));
        }
        assertThat(STORAGE.toString()).doesNotContain("static", "public", "classes");
    }

    @Test
    @WithAnonymousUser
    void anonymousUsersCannotDownload() throws Exception {
        mockMvc.perform(get(url(project) + "/download")).andExpect(status().isUnauthorized());
    }

    private ResultActions upload(long projectId, String name, byte[] bytes) throws Exception {
        return uploadAs(projectId, name, "application/pdf", bytes);
    }

    private ResultActions uploadAs(long projectId, String name, String type, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PUT, url(projectId))
                .file(new MockMultipartFile("file", name, type, bytes)).with(csrf()));
    }

    private Map<String, Object> currentRow(long projectId) {
        return jdbc.queryForMap("SELECT id, storage_key, sha256, size_bytes, version, removed_at "
                + "FROM project_agreements WHERE project_id = ? AND removed_at IS NULL", projectId);
    }

    private List<String> activity(long projectId) {
        return jdbc.queryForList("SELECT message FROM activity_log WHERE project_id = ? ORDER BY id", String.class, projectId);
    }

    private List<String> storedFiles() throws IOException {
        if (!Files.exists(STORAGE)) {
            return List.of();
        }
        // the files of the projects this test made; other tests' leftovers can't be here (own temp dir)
        try (Stream<Path> paths = Files.list(STORAGE)) {
            return paths.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private long project(long clientId, String code) {
        return jdbc.queryForObject("""
                INSERT INTO projects (code, name, client_id, project_type, contract_value, start_date, expected_completion)
                VALUES (?, 'AgTest project', ?, 'CONSTRUCTION', 1000000, DATE '2026-01-01', DATE '2026-12-31')
                RETURNING id""", Long.class, code, clientId);
    }

    private static String url(long projectId) {
        return "/api/v1/projects/" + projectId + "/agreement";
    }

    private static byte[] pdf(String body) {
        return ("%PDF-1.7\n% " + body + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("sk-agreements-test").toRealPath();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
