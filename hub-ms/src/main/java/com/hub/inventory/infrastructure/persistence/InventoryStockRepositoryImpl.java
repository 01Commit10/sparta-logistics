package com.hub.inventory.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.inventory.domain.dto.DecreaseResult;
import com.hub.inventory.domain.repository.InventoryStockRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.common.exception.InventoryErrorCode;
import com.hub.inventory.domain.dto.RestoreResult;

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

        //차감 하지 못했으면 행유무로 수량부족인지 재고행 자체가 미존재인지 식별
        if (updatedCount == 0) {
            return inventoryJpaRepository.findActiveStockSnapshot(hubId, productId)
                    .map(snapshot -> DecreaseResult.insufficient(
                            snapshot.getInventoryId(),
                            snapshot.getRemainingQuantity()
                    ));
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

        return Optional.of( DecreaseResult.succeeded(
                snapshot.getInventoryId(),
                snapshot.getRemainingQuantity()
        ));

    }

    // 재고를 복원하고, 같은 트랜잭션에서 복원 직후의 수량을 조회해 반환한다.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RestoreResult> restore(
            UUID inventoryId,
            int quantity,
            UUID actorId,
            LocalDateTime changedAt
    ) {
        if (inventoryId == null
                || quantity <= 0
                || changedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        int updatedCount = inventoryJpaRepository.restoreStock(
                inventoryId,
                quantity,
                actorId,
                changedAt
        );

        if (updatedCount == 0) {
            // 변경하지 못한 대상이 없는 재고인지, 수량 한도에 걸린 재고인지 구분한다.
            if (inventoryJpaRepository.findStockById(inventoryId).isEmpty()) {
                return Optional.empty();
            }

            throw new BusinessException(
                    InventoryErrorCode.INVENTORY_QUANTITY_LIMIT_EXCEEDED
            );
        }

        if (updatedCount != 1) {
            throw new IllegalStateException(
                    "재고 복원 대상은 정확히 한 건이어야 합니다."
            );
        }

        InventoryJpaRepository.StockSnapshot snapshot =
                inventoryJpaRepository.findStockById(inventoryId)
                        .orElseThrow(() -> new IllegalStateException(
                                "복원된 재고의 결과를 조회할 수 없습니다."
                        ));

        return Optional.of(new RestoreResult(
                snapshot.getInventoryId(),
                snapshot.getRemainingQuantity()
        ));
    }

}
