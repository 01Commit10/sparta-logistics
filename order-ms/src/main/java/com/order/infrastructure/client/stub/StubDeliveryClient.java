package com.order.infrastructure.client.stub;

import com.order.domain.client.CreateDeliveryCommand;
import com.order.domain.client.DeliveryClient;
import com.order.domain.client.DeliveryResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("stub")
public class StubDeliveryClient implements DeliveryClient {

    @Override
    public DeliveryResult createDelivery(CreateDeliveryCommand command) {
        return new DeliveryResult(UUID.randomUUID());
    }
}