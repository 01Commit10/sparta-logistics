package com.hub.inventory.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanyFeignDto(
        UUID id,
        UUID hubId,
        UUID managerUserId
) {
}