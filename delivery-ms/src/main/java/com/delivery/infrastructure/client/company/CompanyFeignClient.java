package com.delivery.infrastructure.client.company;

import com.delivery.infrastructure.client.company.dto.CompanyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "company-ms")
public interface CompanyFeignClient {

    @GetMapping("/internal/v1/companies/{companyId}")
    CompanyResponse getCompany(@PathVariable UUID companyId);
}
