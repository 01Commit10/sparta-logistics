package com.hub.inventory.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.inventory.domain.dto.DecreaseResult;
import com.hub.inventory.domain.repository.InventoryStockRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryStockRepositoryImpl implements InventoryStockRepository {
    private final InventoryJpaRepository inventoryJpaRepository;

    public InventoryStockRepositoryImpl(InventoryJpaRepository inventoryJpaRepository) {
        this.inventoryJpaRepository = inventoryJpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<DecreaseResult> decrease(
            UUID hubId,
            UUID productId,
            int quantity,
            UUID actorId,
            LocalDateTime changedAt
    ){
        if (hubId == null
                || productId == null
                || quantity <= 0
                || changedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        int updatedCount = inventoryJpaRepository.decreaseIfEnough(
                hubId,
                productId,
                quantity,
                actorId,
                changedAt
        );

        if (updatedCount == 0) {
            return Optional.empty();
        }

        if (updatedCount != 1) {
            throw new IllegalStateException(
                    "재고 차감 대상은 정확히 한 건이어야 합니다."
            );
        }

        InventoryJpaRepository.StockSnapshot snapshot =
                inventoryJpaRepository.findActiveStockSnapshot(
                        hubId,
                        productId
                ).orElseThrow(() -> new IllegalStateException(
                        "차감된 재고의 결과를 조회할 수 없습니다."
                ));

        return Optional.of(new DecreaseResult(
                snapshot.getInventoryId(),
                snapshot.getRemainingQuantity()
        ));

    }

}
