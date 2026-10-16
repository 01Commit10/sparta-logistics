package com.delivery.application.service;

import com.delivery.application.dto.command.DeliveryCreateCommand;
import com.delivery.application.dto.result.DeliveryCreateResult;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;


    public DeliveryCreateResult createDelivery(DeliveryCreateCommand command) {
        Delivery delivery = Delivery.create(
                command.orderId(),
                command.departureHubId(),
                command.arrivalHubId(),
                command.deliveryAddress(),
                command.recipientName(),
                command.recipientUserId()
        );
        return DeliveryCreateResult.from(deliveryRepository.save(delivery));
    }
}
