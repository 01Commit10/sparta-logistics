package com.company.company.presentation.dto;

import com.company.company.application.dto.CreateCompanyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 06 테이블 명세서 p_companies: name VARCHAR(150), address VARCHAR(255)
 */
public record CreateCompanyRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 255) String address,
        @NotNull UUID hubId,
        @NotNull UUID managerUserId
) {
    public CreateCompanyCommand toCommand() {
        return new CreateCompanyCommand(name, address, hubId, managerUserId);
    }
}
