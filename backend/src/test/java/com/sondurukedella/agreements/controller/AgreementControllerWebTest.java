package com.sondurukedella.agreements.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.agreements.dto.AgreementResponse;
import com.sondurukedella.agreements.service.AgreementDownload;
import com.sondurukedella.agreements.service.AgreementService;
import com.sondurukedella.agreements.service.AgreementUpload;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.exception.PayloadTooLargeException;
import com.sondurukedella.common.exception.StorageException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// Controller slice only - the service is mocked. Covers security, multipart binding, status codes and headers.
@WebMvcTest(controllers = AgreementController.class)
class AgreementControllerWebTest {

    private static final String URL = "/api/v1/projects/4/agreement";
    private static final byte[] PDF = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgreementService service;

    @Test
    void anonymousUsersCannotReadUploadOrDownload() throws Exception {
        mockMvc.perform(get(URL).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/download").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(upload().with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void uploadsWithoutACsrfTokenAreForbidden() throws Exception {
        mockMvc.perform(upload()).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void theFirstUploadIs201AndAReplacementIs200() throws Exception {
        when(service.upload(eq(4L), any())).thenReturn(new AgreementUpload(metadata(), false))
                .thenReturn(new AgreementUpload(metadata(), true));

        mockMvc.perform(upload().with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Agreement PRJ-2026-004.pdf"))
                .andExpect(jsonPath("$.size").value(PDF.length))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(content().string(not(containsString("storage"))));
        mockMvc.perform(upload().with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void aMissingFilePartIsABadRequest() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, URL).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void tooLargeUploadsAre413ProblemDetailsWhicheverLayerCatchesThem() throws Exception {
        // the servlet multipart limit (before our code runs) and our own streaming check
        when(service.upload(eq(4L), any())).thenThrow(new MaxUploadSizeExceededException(20L * 1024 * 1024))
                .thenThrow(new PayloadTooLargeException("The file is too large. The limit is 20 MB."));

        mockMvc.perform(upload().with(csrf())).andExpect(status().is(413)).andExpect(jsonPath("$.status").value(413));
        mockMvc.perform(upload().with(csrf())).andExpect(status().is(413))
                .andExpect(jsonPath("$.detail").value("The file is too large. The limit is 20 MB."));
    }

    @Test
    @WithMockUser
    void storageFailuresDoNotLeakPaths() throws Exception {
        when(service.upload(eq(4L), any())).thenThrow(new StorageException("Could not write /app/data/agreements/x.pdf",
                new java.io.IOException("D:\\secret\\path denied")));

        mockMvc.perform(upload().with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("/app/data"))))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    @WithMockUser
    void missingProjectsAndMissingAgreementsAre404() throws Exception {
        when(service.getCurrent(4L)).thenThrow(new NotFoundException("No agreement has been uploaded for this project."));
        when(service.download(4L)).thenThrow(new NotFoundException("Project not found."));

        mockMvc.perform(get(URL)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No agreement has been uploaded for this project."));
        mockMvc.perform(get(URL + "/download")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Project not found."));
    }

    @Test
    void theDownloadIsAnAttachmentWithAnEncodedName() throws Exception {
        when(service.download(4L)).thenReturn(new AgreementDownload("ගිවිසුම PRJ-2026-004.pdf", PDF.length,
                new ByteArrayInputStream(PDF)));

        mockMvc.perform(get(URL + "/download").with(user("owner")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().longValue("Content-Length", PDF.length))
                .andExpect(header().string("Content-Disposition", containsString("attachment;")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void thereIsNoPost() throws Exception {
        mockMvc.perform(post(URL).with(user("owner")).with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Test
    void deleteRetiresTheCurrentAgreementAndNeedsCsrf() throws Exception {
        mockMvc.perform(delete(URL).with(user("owner"))).andExpect(status().isForbidden());
        mockMvc.perform(delete(URL).with(user("owner")).with(csrf())).andExpect(status().isNoContent());
        org.mockito.Mockito.verify(service).remove(org.mockito.ArgumentMatchers.anyLong());
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload() {
        return multipart(HttpMethod.PUT, URL).file(new MockMultipartFile("file", "Agreement PRJ-2026-004.pdf",
                "application/pdf", PDF));
    }

    private static AgreementResponse metadata() {
        return new AgreementResponse(1L, 4L, "Agreement PRJ-2026-004.pdf", "application/pdf", PDF.length,
                "a".repeat(64), OffsetDateTime.parse("2026-10-24T10:00:00+05:30"), 0L);
    }
}
