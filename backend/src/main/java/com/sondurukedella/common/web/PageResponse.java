package com.sondurukedella.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

// We return this instead of Spring's Page, whose JSON shape isn't guaranteed to stay the same.
// page starts at 0.
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
