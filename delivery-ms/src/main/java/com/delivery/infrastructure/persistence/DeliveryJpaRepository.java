package com.delivery.infrastructure.persistence;

import com.delivery.domain.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryJpaRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {

    Optional<Delivery> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Delivery> findByOrderIdAndDeletedAtIsNull(UUID orderId);

    Optional<Delivery> findByRoutes_IdAndDeletedAtIsNull(UUID routeId);

}
