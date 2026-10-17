package com.user.domain.repository;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 조회 결과 한 페이지와 전체 건수. application이 Spring Data Page로 바꾼다.
 * (이름이 *Result면 application.dto 규칙(ArchitectureTest)에 걸려 PageData로 둔다)
 */
public record PageData<T>(List<T> content, long totalElements) {

    public PageData {
        content = List.copyOf(content);
    }

    public <R> PageData<R> map(Function<? super T, ? extends R> mapper) {
        return new PageData<>(content.stream().<R>map(mapper).toList(), totalElements);
    }
}
