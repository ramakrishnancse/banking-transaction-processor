package com.example.bank.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** A stable JSON shape, independent of Spring Data's Page implementation. */
record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
