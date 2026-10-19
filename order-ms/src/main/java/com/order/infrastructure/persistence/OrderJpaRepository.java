package com.order.infrastructure.persistence;

import com.order.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/* OrderRepository 와의 약속 지키는 곳 */
public interface OrderJpaRepository extends JpaRepository<Order, UUID> {
}
