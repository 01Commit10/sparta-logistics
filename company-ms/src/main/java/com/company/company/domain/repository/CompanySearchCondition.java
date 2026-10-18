package com.company.company.domain.repository;

import java.util.UUID;

/**
 * 업체 목록 검색 조건 (04 API 명세서 Company). null인 조건은 걸지 않는다.
 * hubId는 일치, name은 부분 일치(대소문자 무시). 삭제된 업체는 항상 제외한다.
 */
public record CompanySearchCondition(
        UUID hubId,
        String name
) {

    /** 빈 문자열(name=)은 조건이 없는 것으로 본다 */
    public CompanySearchCondition {
        name = name == null || name.isBlank() ? null : name.strip();
    }
}
