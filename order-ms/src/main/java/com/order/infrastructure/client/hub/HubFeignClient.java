package com.order.infrastructure.client.hub;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "hub-ms")
public interface HubFeignClient {
    @GetMapping("/internal/v1/inventories")
    List<InventoryFeignResponse> getInventories(@RequestParam("productId") UUID productId);

    @PostMapping("/internal/v1/inventories/decrease")
    DecreaseFeignResponse decrease(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @RequestBody DecreaseFeignRequest request);

    //TODO: 재고 복원 (상상하며 미리 작성)
    @PostMapping("/internal/v1/inventories/restore")
    RestoreFeignResponse restore(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                 @RequestBody RestoreFeignRequest request);

}
