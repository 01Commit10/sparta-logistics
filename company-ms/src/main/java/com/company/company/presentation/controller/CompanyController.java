package com.company.company.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.common.page.PagePolicy;
import com.common.page.PageQuery;
import com.common.page.PageResponse;
import com.company.company.application.service.CompanyService;
import com.company.company.presentation.dto.CompanyResponse;
import com.company.company.presentation.dto.CompanySearchRequest;
import com.company.company.presentation.dto.CreateCompanyRequest;
import com.company.company.presentation.dto.UpdateCompanyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;
    // 업체 목록 (정렬 허용: createdAt·name)
    private static final PagePolicy COMPANIES_PAGE_POLICY = PagePolicy.of("name");

    @PostMapping

    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(@CurrentUser AuthUser authUser,
                                         @Valid @RequestBody CreateCompanyRequest request) {
        authUser.requireMaster();
        return CompanyResponse.from(companyService.createCompany(request.toCommand()));
    }

    // 로그인 사용자 모두. hubId·name 검색은 선택 (보내지 않으면 전체)
    @GetMapping
    public PageResponse<CompanyResponse> searchCompanies(@CurrentUser AuthUser authUser,
                                                         @Valid CompanySearchRequest request,
                                                         Pageable pageable) {
        PageQuery query = COMPANIES_PAGE_POLICY.toQuery(pageable);
        return PageResponse.from(companyService.searchCompanies(request.toQuery(), query), query, CompanyResponse::from);
    }

    // 로그인 사용자 모두 (권한 확인 없이 X-User-* 헤더로 로그인 여부만 본다)
    @GetMapping("/{companyId}")
    public CompanyResponse getCompany(@CurrentUser AuthUser authUser, @PathVariable UUID companyId) {
        return CompanyResponse.from(companyService.getCompany(companyId));
    }

    // MASTER·업체 관리자 본인. 보낸 필드만 수정 (PATCH). hubId·managerUserId 변경은 MASTER만 (CompanyService가 판단)
    @PatchMapping("/{companyId}")
    public CompanyResponse updateCompany(@CurrentUser AuthUser authUser,
                                         @PathVariable UUID companyId,
                                         @Valid @RequestBody UpdateCompanyRequest request) {
        return CompanyResponse.from(companyService.updateCompany(
                request.toCommand(companyId, authUser.id(), authUser.isMaster())));
    }
}
