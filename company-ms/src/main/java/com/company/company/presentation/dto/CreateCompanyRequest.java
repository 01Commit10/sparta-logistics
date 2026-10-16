package com.company.company.presentation.dto;

import com.company.company.application.dto.CreateCompanyCommand;
import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.NotNull;import java.util.UUID;

public record CreateCompanyRequest(
    @NotBlank String name,
    @NotBlank String address,
    @NotNull UUID hubId,
    @NotNull UUID managerUserId
    ) {
    public CreateCompanyCommand toCommand() {
        return new CreateCompanyCommand(name, address, hubId, managerUserId);
    }
}
