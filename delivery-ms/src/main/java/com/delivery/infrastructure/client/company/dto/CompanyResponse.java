package com.delivery.infrastructure.client.company.dto;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
//        String name,
        String address,
        UUID hubId,
        UUID managerUserId
) {
}
