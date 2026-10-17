package com.delivery.domain.repository;

import com.delivery.domain.entity.Delivery;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository {

    Delivery save(Delivery delivery);
    Optional<Delivery> findById(UUID deliveryId);
}
