package com.order.domain.repository;

import com.order.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/* 도메인은 JPA 모른다 */
public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(UUID id);

    // 삭제되지 않은 주문만
    Optional<Order> findActiveById(UUID uuid);

    Page<Order> findAllActive(Pageable pageable);

    Page<Order> findAllActiveByOrderedBy(UUID orderedBy, Pageable pageable);

}