package com.common.page;

import java.util.List;
import java.util.function.Function;

public record PageData<T>(List<T> content, long totalElements) {
    public PageData {
        content =List.copyOf(content);
    }

    public <R> PageData<R> map(Function<? super T, ? extends R> mapper) {
        return new PageData<> (content.stream().<R>map(mapper).toList(), totalElements);
    }
}
