package com.delivery.application.client;

import com.delivery.domain.vo.DeliveryDestination;

import java.util.UUID;

public interface CompanyClient {

    DeliveryDestination getDeliveryDestination(UUID companyId);
}
