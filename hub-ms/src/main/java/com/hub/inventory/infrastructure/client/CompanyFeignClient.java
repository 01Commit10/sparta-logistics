package com.hub.inventory.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "company-ms")
public interface CompanyFeignClient {

    @GetMapping("/internal/v1/products/{productId}")
    ProductFeignDto getProduct(
            @PathVariable("productId") UUID productId
    );

    @GetMapping("/internal/v1/companies/{companyId}")
    CompanyFeignDto getCompany(
            @PathVariable("companyId") UUID companyId
    );
}