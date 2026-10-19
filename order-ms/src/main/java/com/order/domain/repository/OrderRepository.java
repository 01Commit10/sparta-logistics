package com.order.domain.repository;

import com.order.domain.entity.Order;

import java.util.Optional;
import java.util.UUID;

/* 도메인은 JPA 모른다 */
public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(UUID id);
}