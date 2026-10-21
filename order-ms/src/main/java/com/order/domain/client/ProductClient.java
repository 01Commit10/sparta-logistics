package com.order.domain.client;

import java.util.UUID;

public interface ProductClient {
    // 없거나 삭제된 상품/업체면 company-ms의 404 예외가 그대로 전달된다.
    ProductInfo getProduct(UUID productId);
}
