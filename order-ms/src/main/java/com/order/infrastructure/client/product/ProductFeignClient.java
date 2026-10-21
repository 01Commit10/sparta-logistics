package com.order.infrastructure.client.product;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "company-ms")
public interface ProductFeignClient {

    @GetMapping("/internal/v1/products/{productId}")
    ProductFeignResponse getProduct(@PathVariable("productId") UUID productId);

}
