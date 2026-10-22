package com.order.infrastructure.client.product;

import java.math.BigDecimal;
import java.util.UUID;

// company-ms InternalProductResponse와 같은 모양
public record ProductFeignResponse(UUID id, UUID companyId, String name, BigDecimal unitPrice) {
}
