package com.delivery.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.domain.entity.DeliveryHistory;
import com.delivery.domain.repository.DeliveryHistoryRepository;
import com.delivery.domain.vo.DeliveryOperationType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DeliveryHistoryRepositoryImpl implements DeliveryHistoryRepository {

    private static final String OPERATION_TYPE_IDEMPOTENCY_KEY_UNIQUE =
            "uk_delivery_histories_operation_type_idempotency_key";

    private final DeliveryHistoryJpaRepository jpaRepository;


    @Override
    public DeliveryHistory save(DeliveryHistory deliveryHistory) {
        try {
            return jpaRepository.saveAndFlush(deliveryHistory);
        } catch (DataIntegrityViolationException e){
            if(e.getCause() instanceof ConstraintViolationException cve
                    && OPERATION_TYPE_IDEMPOTENCY_KEY_UNIQUE.equalsIgnoreCase(cve.getConstraintName())) {
                throw new BusinessException(DeliveryErrorCode.DUPLICATE_IDEMPOTENT_REQUEST);
            }
            throw e;
        }
    }

    @Override
    public Optional<DeliveryHistory> findByOperationTypeAndIdempotencyKey(DeliveryOperationType operationType, String idempotencyKey) {
        return jpaRepository.findByOperationTypeAndIdempotencyKey(operationType, idempotencyKey);
    }


}
