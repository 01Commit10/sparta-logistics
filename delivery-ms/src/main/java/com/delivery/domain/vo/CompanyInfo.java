package com.delivery.domain.vo;

import java.util.UUID;

public record CompanyInfo(
    UUID hubId,
    String address,
    UUID managerUserId
) {
}
