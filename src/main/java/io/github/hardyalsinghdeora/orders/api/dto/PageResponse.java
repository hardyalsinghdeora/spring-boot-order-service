package io.github.hardyalsinghdeora.orders.api.dto;

import java.util.List;

import io.github.hardyalsinghdeora.orders.application.PageSlice;

/** A deliberately small, stable page shape instead of exposing Spring Data's Page. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> PageResponse<T> from(PageSlice<T> slice) {
        return new PageResponse<>(slice.content(), slice.page(), slice.size(), slice.totalElements());
    }
}
