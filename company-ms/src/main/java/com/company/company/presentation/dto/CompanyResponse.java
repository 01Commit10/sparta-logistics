package com.company.company.presentation.dto;

import com.company.company.application.dto.CompanyResult;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String address,
        UUID hubId,
        UUID managerUserId
) {
    public static CompanyResponse from(CompanyResult result) {
        return new CompanyResponse(
                result.id(),
                result.name(),
                result.address(),
                result.hubId(),
                result.managerUserId()
        );
    }
}
