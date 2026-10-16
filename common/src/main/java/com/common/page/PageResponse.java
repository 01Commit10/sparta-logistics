package com.common.page;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 조회 공통 응답. page는 0부터 시작한다.
 * Spring의 PageImpl을 그대로 반환하면 pageable 등 내부 구조가 노출되고 Feign 역직렬화도 어려워 이 record로 변환한다.
 * presentation 계층에서만 만든다 (application은 Page까지만 반환).
 *
 * <pre>
 * return PageResponse.from(companyService.searchCompanies(cond, pageable), CompanyResponse::from);
 * </pre>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        List<SortOrder> sort
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.getSort().stream().map(SortOrder::from).toList());
    }

    public static <S, T> PageResponse<T> from(Page<S> page, Function<? super S, ? extends T> mapper) {
        return from(page.map(mapper));
    }
}
