package com.sondurukedella.audit;

import com.sondurukedella.audit.dto.AuditChangeResponse;
import com.sondurukedella.audit.dto.SecurityEventResponse;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.web.PageResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// Read side of the audit history, newest first. Super Admin only (SecurityConfig).
@Service
@RequiredArgsConstructor
public class AuditQueries {

    public static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<Map<String, List<String>>> CHANGES = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;
    private final ZoneId zone = ZoneId.systemDefault();

    @Transactional(readOnly = true)
    public PageResponse<AuditChangeResponse> changes(Long userId, String entityType, String entityId, LocalDate from, LocalDate to,
            int page, int size) {
        Filter f = new Filter(page, size, from, to);
        f.add(userId != null, "user_id = :userId", "userId", userId);
        f.add(entityType != null && !entityType.isBlank(), "entity_type = :entityType", "entityType", entityType);
        f.add(entityId != null && !entityId.isBlank(), "entity_id = :entityId", "entityId", entityId);
        long total = f.count("audit_events");
        List<AuditChangeResponse> rows = jdbc.query("SELECT id, occurred_at, user_id, username, http_method, path, request_id, "
                        + "entity_type, entity_id, change_type, changes::text AS changes FROM audit_events" + f.where()
                        + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset", f.params,
                (rs, i) -> new AuditChangeResponse(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class),
                        (Long) rs.getObject("user_id"), rs.getString("username"), rs.getString("http_method"), rs.getString("path"),
                        rs.getObject("request_id", UUID.class), rs.getString("entity_type"), rs.getString("entity_id"),
                        rs.getString("change_type"), json.readValue(rs.getString("changes"), CHANGES)));
        return f.page(rows, total);
    }

    @Transactional(readOnly = true)
    public List<String> entityTypes() {
        return jdbc.queryForList("SELECT DISTINCT entity_type FROM audit_events ORDER BY entity_type", Map.of(), String.class);
    }

    // the user is either side: who did it, or whose account it was
    @Transactional(readOnly = true)
    public PageResponse<SecurityEventResponse> security(Long userId, String eventType, LocalDate from, LocalDate to, int page, int size) {
        Filter f = new Filter(page, size, from, to);
        f.add(userId != null, "(actor_user_id = :userId OR target_user_id = :userId)", "userId", userId);
        f.add(eventType != null && !eventType.isBlank(), "event_type = :eventType", "eventType", eventType);
        long total = f.count("security_events");
        List<SecurityEventResponse> rows = jdbc.query("SELECT id, occurred_at, event_type, actor_user_id, target_user_id, username, "
                        + "remote_address, details FROM security_events" + f.where()
                        + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset", f.params,
                (rs, i) -> new SecurityEventResponse(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("event_type"), (Long) rs.getObject("actor_user_id"), (Long) rs.getObject("target_user_id"),
                        rs.getString("username"), rs.getString("remote_address"), rs.getString("details")));
        return f.page(rows, total);
    }

    // where clause and paging shared by both lists; dates are whole days in the server's time zone
    private final class Filter {

        private final List<String> conditions = new ArrayList<>();
        private final MapSqlParameterSource params = new MapSqlParameterSource();
        private final int page;
        private final int size;

        Filter(int page, int size, LocalDate from, LocalDate to) {
            if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
                throw new InvalidRequestException("page starts at 0 and size is 1 to " + MAX_PAGE_SIZE + ".");
            }
            if (from != null && to != null && to.isBefore(from)) {
                throw new InvalidRequestException("\"to\" can't be before \"from\".");
            }
            this.page = page;
            this.size = size;
            add(from != null, "occurred_at >= :from", "from", from == null ? null : from.atStartOfDay(zone).toOffsetDateTime());
            add(to != null, "occurred_at < :to", "to", to == null ? null : to.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
            params.addValue("limit", size).addValue("offset", (long) page * size);
        }

        void add(boolean when, String condition, String name, Object value) {
            if (when) {
                conditions.add(condition);
                params.addValue(name, value);
            }
        }

        String where() {
            return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        }

        long count(String table) {
            Long n = jdbc.queryForObject("SELECT count(*) FROM " + table + where(), params, Long.class);
            return n == null ? 0 : n;
        }

        <T> PageResponse<T> page(List<T> rows, long total) {
            return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
        }
    }
}
