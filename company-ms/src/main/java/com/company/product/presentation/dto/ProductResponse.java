package com.company.product.presentation.dto;

import com.company.product.application.dto.ProductResult;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID companyId,
        String name,
        String description,
        BigDecimal unitPrice
) {
    public static ProductResponse from(ProductResult result) {
        return new ProductResponse(
                result.id(),
                result.companyId(),
                result.name(),
                result.description(),
                result.unitPrice()
        );
    }
}
