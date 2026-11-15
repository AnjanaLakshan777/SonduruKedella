package com.sondurukedella.audit;

import com.sondurukedella.common.audit.AuditUser;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

// Writes audit_events for every entity Hibernate inserts, updates or deletes: who, when, which request,
// and old -> new per field. Hooked into Hibernate's own events so no service has to remember to call it.
// The row goes in on the same connection and transaction, so a rolled-back change leaves no audit row.
@Component
@RequiredArgsConstructor
public class AuditRecorder implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    // these are logs already (security_events, activity_log)
    private static final Set<String> SKIPPED_ENTITIES = Set.of("SecurityEvent", "ActivityEntry");
    // bookkeeping columns that change on every save and say nothing
    private static final Set<String> SKIPPED_FIELDS = Set.of("createdAt", "createdBy", "updatedAt", "version", "authVersion");
    // never stored, only that it changed
    private static final Set<String> MASKED_FIELDS = Set.of("passwordHash");
    private static final String REQUEST_ID = AuditRecorder.class.getName() + ".requestId";
    private static final int MAX_VALUE_LENGTH = 500;

    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    @PostConstruct
    void register() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class).getEventListenerRegistry();
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_DELETE, this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        Map<String, Object[]> changes = new LinkedHashMap<>();
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            String value = show(names[i], event.getState()[i], event.getSession());
            if (value != null && tracked(names[i])) {
                changes.put(names[i], new Object[] {null, MASKED_FIELDS.contains(names[i]) ? "(set)" : value});
            }
        }
        write(event.getPersister(), event.getId(), "CREATED", changes);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Object[] old = event.getOldState();
        Object[] now = event.getState();
        String[] names = event.getPersister().getPropertyNames();
        Map<String, Object[]> changes = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (!tracked(names[i])) {
                continue;
            }
            String before = old == null ? null : show(names[i], old[i], event.getSession());
            String after = show(names[i], now[i], event.getSession());
            if (!Objects.equals(before, after)) {
                changes.put(names[i], MASKED_FIELDS.contains(names[i]) ? new Object[] {"(old)", "(changed)"} : new Object[] {before, after});
            }
        }
        // only bookkeeping moved (version, updated_at): nothing worth a row
        if (!changes.isEmpty()) {
            write(event.getPersister(), event.getId(), "UPDATED", changes);
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        Map<String, Object[]> changes = new LinkedHashMap<>();
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            String value = show(names[i], event.getDeletedState()[i], event.getSession());
            if (value != null && tracked(names[i]) && !MASKED_FIELDS.contains(names[i])) {
                changes.put(names[i], new Object[] {value, null});
            }
        }
        write(event.getPersister(), event.getId(), "DELETED", changes);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private static boolean tracked(String field) {
        return !SKIPPED_FIELDS.contains(field);
    }

    private void write(EntityPersister persister, Object id, String changeType, Map<String, Object[]> changes) {
        String type = simpleName(persister.getEntityName());
        if (SKIPPED_ENTITIES.contains(type)) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = auth != null && auth.getPrincipal() instanceof AuditUser user ? user.userId() : null;
        String username = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) ? cut(auth.getName(), 100) : null;
        HttpServletRequest request = currentRequest();
        jdbc.update("""
                INSERT INTO audit_events (user_id, username, http_method, path, request_id, entity_type, entity_id, change_type, changes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""",
                userId, username,
                request == null ? null : request.getMethod(),
                request == null ? null : cut(request.getRequestURI(), 300),
                request == null ? null : requestId(),
                type, id == null ? null : cut(String.valueOf(id), 40), changeType, json.writeValueAsString(changes));
    }

    // a value as text; null for things that aren't worth showing (collections, empty)
    private static String show(String field, Object value, SharedSessionContractImplementor session) {
        if (value == null || value instanceof Collection<?> || value instanceof Map<?, ?>) {
            return null;
        }
        String text;
        if (value instanceof BigDecimal d) {
            text = d.stripTrailingZeros().toPlainString();
        } else if (value instanceof Enum<?> e) {
            text = e.name();
        } else if (value instanceof byte[]) {
            text = "(file)";
        } else if (value instanceof Number || value instanceof Boolean || value instanceof Temporal || value instanceof CharSequence) {
            text = value.toString();
        } else if (value.getClass().isAnnotationPresent(Entity.class)) {
            // a link to another record: its id
            Object linked = session.getContextEntityIdentifier(value);
            text = linked == null ? null : "#" + linked;
        } else {
            text = value.toString();
        }
        return text == null ? null : cut(text, MAX_VALUE_LENGTH);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    // one id per request, so the page can show what a single click changed
    private static UUID requestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object id = attributes.getAttribute(REQUEST_ID, RequestAttributes.SCOPE_REQUEST);
        if (id == null) {
            id = UUID.randomUUID();
            attributes.setAttribute(REQUEST_ID, id, RequestAttributes.SCOPE_REQUEST);
        }
        return (UUID) id;
    }

    private static String simpleName(String entityName) {
        return entityName.substring(entityName.lastIndexOf('.') + 1);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
