package com.company.product.application.dto;

import com.company.product.domain.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResult(
        UUID id,
        UUID companyId,
        String name,
        String description,
        BigDecimal unitPrice,
        Long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProductResult from(Product product) {
        return new ProductResult(
                product.getId(),
                product.getCompany().getId(),
                product.getName(),
                product.getDescription(),
                product.getUnitPrice(),
                product.getViewCount(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    // 조회수만 바꾼 값 (캐시한 상세에 방금 센 조회수를 담아 응답)
    public ProductResult withViewCount(long viewCount) {
        return new ProductResult(id, companyId, name, description, unitPrice, viewCount, createdAt, updatedAt);
    }
}
