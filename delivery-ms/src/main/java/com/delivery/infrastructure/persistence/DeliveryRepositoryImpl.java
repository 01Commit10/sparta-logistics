package com.delivery.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
            throw translate(e);
        }
    }

    @Override
    public Optional<Delivery> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }


    private RuntimeException translate(DataIntegrityViolationException e) {
        if(e.getCause() instanceof ConstraintViolationException cve
            && ORDER_ID_UNIQUE.equalsIgnoreCase(cve.getConstraintName())) {
            return new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_EXISTS);
        }
        return e;
    }
}
