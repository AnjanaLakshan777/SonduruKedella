package com.sondurukedella.activity.service;

import com.sondurukedella.activity.dto.ActivityResponse;
import com.sondurukedella.activity.entity.ActivityEntry;
import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.mapper.ActivityMapper;
import com.sondurukedella.activity.repository.ActivityEntryRepository;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// The activity log: other modules write entries as part of their own changes; the feed reads them.
// It never checks that a project exists, because projects depends on this module (an unknown
// projectId just has no entries).
@Service
@RequiredArgsConstructor
public class ActivityService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ActivityEntryRepository repository;
    private final ActivityMapper mapper;
    private final Clock clock;

    // MANDATORY: an entry only makes sense as part of the change it describes, so it must
    // commit or roll back with the caller's transaction rather than on its own.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long projectId, ActivityKind kind, String message) {
        repository.save(new ActivityEntry(projectId, LocalDate.now(clock), kind, message, OffsetDateTime.now(clock)));
    }

    // Newest first. projectId narrows to one project's feed; from/to bound the activity date, inclusive.
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> feed(Long projectId, ActivityKind kind, LocalDate from, LocalDate to, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        if (pageable.getSort().isSorted()) {
            throw new InvalidRequestException("The activity feed is always newest first.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("The start date must be on or before the end date.");
        }
        Specification<ActivityEntry> filter = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (kind != null) {
                predicates.add(cb.equal(root.get("kind"), kind));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("activityDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("activityDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("activityDate"), Sort.Order.desc("id")));
        return PageResponse.of(repository.findAll(filter, page), mapper::toResponse);
    }
}
