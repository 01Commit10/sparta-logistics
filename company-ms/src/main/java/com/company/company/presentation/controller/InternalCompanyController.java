package com.company.company.presentation.controller;

import com.company.company.application.service.CompanyService;
import com.company.company.presentation.dto.InternalCompanyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 서비스 간 내부 API. Gateway에 라우트가 없고 /internal/**은 차단되므로 외부에서는 호출할 수 없다.
 * Eureka로 직접 호출되므로 X-User-* 헤더를 요구하지 않는다 (스케줄러·Saga 등 요청 밖 호출도 가능, C01 D8).
 */
@RestController
@RequiredArgsConstructor
public class InternalCompanyController {

    private final CompanyService companyService;

    // 업체 검증: 없거나 삭제된 업체는 404 COMPANY_NOT_FOUND
    @GetMapping("/internal/v1/companies/{companyId}")
    public InternalCompanyResponse getCompany(@PathVariable UUID companyId) {
        return InternalCompanyResponse.from(companyService.getCompany(companyId));
    }
}
