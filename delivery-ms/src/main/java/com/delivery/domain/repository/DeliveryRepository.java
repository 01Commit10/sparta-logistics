package com.delivery.domain.repository;

import com.delivery.domain.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository {

    Delivery save(Delivery delivery);
    Optional<Delivery> findById(UUID deliveryId);
    Optional<Delivery> findByOrderId(UUID orderId);
    Optional<Delivery> findByRouteId(UUID routeId);
    Page<Delivery> findAll(Pageable pageable);
    Page<Delivery> findAllByDeliveryManagerId(UUID deliveryManagerId, Pageable pageable);
}
