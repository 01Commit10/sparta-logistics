package com.company.product.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * actorId·actorIsMaster: 요청한 사용자. 업체 관리자 본인인지는 업체 데이터를 봐야 알 수 있어 유스케이스에서 판단한다.
 */
public record CreateProductCommand(
        UUID companyId,
        String name,
        String description,
        BigDecimal unitPrice,
        UUID actorId,
        boolean actorIsMaster
) {}
