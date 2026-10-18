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
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
