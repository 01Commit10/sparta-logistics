package com.company.product.presentation.dto;

import com.company.product.application.dto.UpdateProductCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 상품 수정(PATCH): 보낸 필드만 바꾸고, 빠진(null) 필드는 그대로 둔다.
 * 06 테이블 명세서 p_products: name VARCHAR(150), unit_price NUMERIC(15,2) >= 0
 * name은 보낼 경우 공백만으로 채울 수 없다 (@Pattern은 null을 통과시킨다).
 */
public record UpdateProductRequest(
        @Size(max = 150) @Pattern(regexp = "(?s).*\\S.*", message = "공백만으로 이루어질 수 없습니다.") String name,
        String description,
        @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice
) {
    // 빈 요청({})은 바꿀 내용이 없으므로 400으로 막는다
    @AssertTrue(message = "수정할 필드를 하나 이상 보내야 합니다.")
    public boolean isAnyFieldPresent() {
        return name != null || description != null || unitPrice != null;
    }

    public UpdateProductCommand toCommand(UUID productId, UUID actorId, boolean actorIsMaster) {
        return new UpdateProductCommand(productId, name, description, unitPrice, actorId, actorIsMaster);
    }
}
