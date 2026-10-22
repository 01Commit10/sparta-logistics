package com.company.product.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// 같은 hub-ms를 부르는 HubFeignClient와 Feign 설정 빈 이름이 겹치지 않도록 contextId를 따로 둔다
@FeignClient(name = "hub-ms", contextId = "inventoryFeignClient", path = "/internal/v1/inventories")
public interface InventoryFeignClient {

    @DeleteMapping("/products/{productId}")
    void deleteProductInventories(@PathVariable("productId") UUID productId);
}
