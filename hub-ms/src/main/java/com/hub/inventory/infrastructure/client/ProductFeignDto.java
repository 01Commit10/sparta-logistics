package com.hub.inventory.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductFeignDto(
        UUID id,
        UUID companyId
) {
}
