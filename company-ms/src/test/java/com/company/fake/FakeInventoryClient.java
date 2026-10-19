package com.company.fake;

import com.company.product.domain.client.InventoryClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 재고 삭제를 요청받은 상품을 기록한다. failWith()를 지정하면 원격 장애처럼 그 예외를 던진다. */
public class FakeInventoryClient implements InventoryClient {

    private final List<UUID> deletedProductIds = new ArrayList<>();
    private RuntimeException failure;

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public List<UUID> deletedProductIds() {
        return deletedProductIds;
    }

    @Override
    public void deleteInventory(UUID productId) {
        if (failure != null) {
            throw failure;
        }
        deletedProductIds.add(productId);
    }
}
