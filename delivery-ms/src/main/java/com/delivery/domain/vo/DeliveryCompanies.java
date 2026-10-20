package com.delivery.domain.vo;

import java.util.UUID;

public record DeliveryCompanies(
        UUID supplierCompanyId,
        UUID supplierCompanyManagerId,
        UUID requesterCompanyId,
        UUID requesterCompanyManagerId
) {}
