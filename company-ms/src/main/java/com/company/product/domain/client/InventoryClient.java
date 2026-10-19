package com.company.product.domain.client;

import java.util.UUID;

public interface InventoryClient {

    /** 상품의 허브별 재고를 모두 지운다. hub-ms는 대상이 없거나 이미 지워졌어도 성공으로 답한다 */
    void deleteInventory(UUID productId);
}
