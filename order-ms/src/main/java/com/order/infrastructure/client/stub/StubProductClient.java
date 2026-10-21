package com.order.infrastructure.client.stub;

import com.order.domain.client.ProductClient;
import com.order.domain.client.ProductInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Profile("stub")
public class StubProductClient implements ProductClient {
    // Postman 요청의 supplierCompanyId, unitPrice와 맞춰야 통과한다
    private static final UUID STUB_SUPPLIER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final BigDecimal STUB_PRICE = new BigDecimal("1000.00");

    @Override
    public ProductInfo getProduct(UUID productId) {
        return new ProductInfo(productId, STUB_SUPPLIER_ID, "테스트 상품", STUB_PRICE);
    }
}
