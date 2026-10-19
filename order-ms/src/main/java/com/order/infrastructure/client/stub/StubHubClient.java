package com.order.infrastructure.client.stub;


import com.order.domain.client.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("stub")
public class StubHubClient implements HubClient {

    // Postman 테스트 때 응답에서 확인하기 쉽도록 고정값
    private static final UUID STUB_HUB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Override
    public List<InventoryInfo> findInventories(UUID productId) {
        return List.of(new InventoryInfo(UUID.randomUUID(), STUB_HUB_ID, productId, 1000));
    }

    @Override
    public StockOperationResult decreaseStock(StockDecreaseCommand command) {
        return new StockOperationResult(UUID.randomUUID(), true, null, 1000 - command.quantity());
    }

    @Override
    public StockOperationResult restoreStock(StockRestoreCommand command) {
        return new StockOperationResult(UUID.randomUUID(), true, null, 1000);
    }
}