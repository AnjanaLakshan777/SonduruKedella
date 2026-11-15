package com.sondurukedella.audit;

import com.sondurukedella.audit.dto.AuditChangeResponse;
import com.sondurukedella.audit.dto.SecurityEventResponse;
import com.sondurukedella.common.web.PageResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Audit history: read only, Super Admin only (SecurityConfig). Nothing here can change a row.
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueries queries;

    @GetMapping("/changes")
    public PageResponse<AuditChangeResponse> changes(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.changes(userId, entityType, entityId, from, to, page, size);
    }

    @GetMapping("/entity-types")
    public List<String> entityTypes() {
        return queries.entityTypes();
    }

    @GetMapping("/security")
    public PageResponse<SecurityEventResponse> security(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.security(userId, eventType, from, to, page, size);
    }
}
