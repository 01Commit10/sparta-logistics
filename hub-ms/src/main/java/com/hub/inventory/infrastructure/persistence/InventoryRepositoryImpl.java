package com.hub.inventory.infrastructure.persistence;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.inventory.domain.entity.Inventory;
import com.hub.inventory.domain.repository.InventoryRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class InventoryRepositoryImpl implements InventoryRepository {
    private static final String HUB_PRODUCT_UNIQUE_CONSTRAINT =
            "uk_inventories_hub_product";

    private final InventoryJpaRepository inventoryJpaRepository;

    public InventoryRepositoryImpl(InventoryJpaRepository inventoryJpaRepository) {
        this.inventoryJpaRepository = inventoryJpaRepository;
    }

    @Override
    public List<Inventory> findAll(UUID hubId, UUID productId){
        return inventoryJpaRepository.findAllByConditions(hubId, productId);
    }

    @Override
    public boolean existsByHubIdAndProductId(
            UUID hubId,
            UUID productId
    ){
        return inventoryJpaRepository.existsByHubIdAndProductId(
                hubId,
                productId
        );
    }


    @Override
    public Inventory save(Inventory inventory) {
        try {
            return inventoryJpaRepository.saveAndFlush(inventory);
        } catch (DataIntegrityViolationException e) {
            if (isHubProductUniqueViolation(e)) {
                throw new BusinessException(
                        CommonErrorCode.INVALID_INPUT,
                        "이미 등록된 허브·상품의 재고입니다."
                );
            }

            throw e;
        }
    }

    private static boolean isHubProductUniqueViolation(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && HUB_PRODUCT_UNIQUE_CONSTRAINT.equals(
                    violation.getConstraintName()
            )) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

}
