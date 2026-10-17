package com.delivery.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DeliveryRepositoryImpl implements DeliveryRepository {

    private static final String ORDER_ID_UNIQUE = "uk_p_delivery_order_id";

    private final DeliveryJpaRepository jpaRepository;


    @Override
    public Delivery save(Delivery delivery) {
        try {
            return jpaRepository.saveAndFlush(delivery);
        } catch (DataIntegrityViolationException e) {
            if(e.getCause() instanceof ConstraintViolationException cve
                    && ORDER_ID_UNIQUE.equalsIgnoreCase(cve.getConstraintName())) {
                throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    @Override
    public Optional<Delivery> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<Delivery> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderIdAndDeletedAtIsNull(orderId);
    }


    @Override
    public Page<Delivery> findAll(Pageable pageable) {
        return jpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public Page<Delivery> findAllByDeliveryManagerId(UUID deliveryManagerId, Pageable pageable) {
        return jpaRepository.findAllByDeliveryManagerIdAndDeletedAtIsNull(deliveryManagerId, pageable);
    }


}
