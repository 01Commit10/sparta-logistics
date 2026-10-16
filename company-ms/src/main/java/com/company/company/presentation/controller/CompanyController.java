package com.company.company.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.company.company.application.service.CompanyService;
import com.company.company.presentation.dto.CompanyResponse;
import com.company.company.presentation.dto.CreateCompanyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping

    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(@CurrentUser AuthUser authUser,
                                         @Valid @RequestBody CreateCompanyRequest request) {
        authUser.requireMaster();
        return CompanyResponse.from(companyService.createCompany(request.toCommand()));
    }
}
