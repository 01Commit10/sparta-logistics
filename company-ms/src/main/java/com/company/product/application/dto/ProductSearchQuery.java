package com.company.product.application.dto;

import java.util.UUID;

/** 상품 목록 검색 조건. 보내지 않은(null) 조건은 걸지 않는다. */
public record ProductSearchQuery(
        UUID companyId,
        String name
) {}
