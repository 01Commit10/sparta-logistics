package com.order.infrastructure.persistence;

import com.order.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/* OrderRepository 와의 약속 지키는 곳 */
public interface OrderJpaRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdAndDeletedAtIsNull(UUID id);

    Page<Order> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Order> findAllByOrderedByAndDeletedAtIsNull(UUID orderedBy, Pageable pageable);
}