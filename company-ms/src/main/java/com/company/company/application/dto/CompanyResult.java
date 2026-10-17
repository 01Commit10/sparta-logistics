package com.company.company.application.dto;

import com.company.company.domain.entity.Company;

import java.time.LocalDateTime;
import java.util.UUID;

public record CompanyResult(
        UUID id,
        String name,
        String address,
        UUID hubId,
        UUID managerUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CompanyResult from(Company company) {
        return new CompanyResult(
                company.getId(),
                company.getName(),
                company.getAddress(),
                company.getHubId(),
                company.getManagerUserId(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }
}
