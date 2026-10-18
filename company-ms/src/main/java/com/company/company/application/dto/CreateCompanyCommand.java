package com.company.company.application.dto;

import java.util.UUID;

public record CreateCompanyCommand(
        String name,
        String address,
        UUID hubId,
        UUID managerUserId
) {}
