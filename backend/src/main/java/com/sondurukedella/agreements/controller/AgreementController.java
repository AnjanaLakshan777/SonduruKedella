package com.sondurukedella.agreements.controller;

import com.sondurukedella.agreements.dto.AgreementResponse;
import com.sondurukedella.agreements.service.AgreementDownload;
import com.sondurukedella.agreements.service.AgreementService;
import com.sondurukedella.agreements.service.AgreementUpload;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Lives under the project URL but in its own module. PUT because there is only ever one current
// agreement: the same call uploads the first one or replaces it. No DELETE yet.
@RestController
@RequestMapping("/api/v1/projects/{projectId}/agreement")
@RequiredArgsConstructor
public class AgreementController {

    private final AgreementService service;

    @GetMapping
    public AgreementResponse get(@PathVariable long projectId) {
        return service.getCurrent(projectId);
    }

    // multipart/form-data with the PDF in a part called "file"
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AgreementResponse> upload(@PathVariable long projectId, @RequestPart("file") MultipartFile file) {
        AgreementUpload upload = service.upload(projectId, file);
        return ResponseEntity.status(upload.replaced() ? HttpStatus.OK : HttpStatus.CREATED).body(upload.agreement());
    }

    // retires the current agreement, the PDF is kept
    @DeleteMapping
    public ResponseEntity<Void> remove(@PathVariable long projectId) {
        service.remove(projectId);
        return ResponseEntity.noContent().build();
    }

    // Always an attachment, never inline, so a browser doesn't render it inside our origin.
    // ContentDisposition encodes the stored name (RFC 6266/5987), which was already cleaned on upload.
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long projectId) {
        AgreementDownload download = service.download(projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(download.content()));
    }
}
