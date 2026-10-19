package com.company.product.infrastructure.client;

import com.common.remote.RemoteCallTemplate;
import com.company.product.domain.client.InventoryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryClientImpl implements InventoryClient {

    private static final String TARGET = "hub-ms";

    private final InventoryFeignClient inventoryFeignClient;
    private final RemoteCallTemplate remote;

    // hub-ms는 멱등(대상 없어도 204)이라 404를 따로 해석하지 않는다. 실패(4xx·5xx·타임아웃)는 그대로 던진다
    @Override
    public void deleteInventory(UUID productId) {
        remote.run(TARGET, () -> inventoryFeignClient.deleteProductInventories(productId));
    }
}
