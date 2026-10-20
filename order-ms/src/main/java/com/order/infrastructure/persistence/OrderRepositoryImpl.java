package com.order.infrastructure.persistence;


import com.order.domain.entity.Order;
import com.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public Optional<Order> findActiveById(UUID uuid) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(uuid);
    }

    @Override
    public Page<Order> findAllActive(Pageable pageable) {
        return orderJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public Page<Order> findALLByOrderedByAndDeletedAtIsNull(UUID orderedBy, Pageable pageable) {
        return orderJpaRepository.findAllByOrderedByAndDeletedAtIsNull(orderedBy, pageable);
    }
}
