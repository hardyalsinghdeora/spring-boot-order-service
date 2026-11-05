package io.github.hardyalsinghdeora.orders.application;

import java.util.List;
import java.util.function.Function;

/** One page of results, independent of the persistence framework's pagination types. */
public record PageSlice<T>(List<T> content, int page, int size, long totalElements) {

    public <R> PageSlice<R> map(Function<T, R> mapper) {
        return new PageSlice<>(content.stream().map(mapper).toList(), page, size, totalElements);
    }
}
