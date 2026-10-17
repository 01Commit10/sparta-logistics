package com.company.product.presentation.dto;

import com.company.product.application.dto.CreateProductCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 06 테이블 명세서 p_products: name VARCHAR(150), unit_price NUMERIC(15,2) >= 0
 */
public record CreateProductRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice
) {
    public CreateProductCommand toCommand(UUID companyId, UUID actorId, boolean actorIsMaster) {
        return new CreateProductCommand(companyId, name, description, unitPrice, actorId, actorIsMaster);
    }
}
