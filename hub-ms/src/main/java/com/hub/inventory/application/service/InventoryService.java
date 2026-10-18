package com.hub.inventory.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.hub.domain.repository.HubRepository;
import com.hub.inventory.application.dto.CreateInventoryCommand;
import com.hub.inventory.application.dto.InventoryResult;
import com.hub.inventory.domain.client.ProductClient;
import com.hub.inventory.domain.client.ProductInfo;
import com.hub.inventory.domain.entity.Inventory;
import com.hub.inventory.domain.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final HubRepository hubRepository;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;

    public InventoryService(InventoryRepository inventoryRepository, HubRepository hubRepository, ProductClient productClient, TransactionTemplate transactionTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.hubRepository = hubRepository;
        this.productClient = productClient;
        this.transactionTemplate = transactionTemplate;
    }
    @Transactional(readOnly = true)
    public List<InventoryResult> getInventories(UUID hubId, UUID productId){
        return inventoryRepository.findAll(hubId, productId)
                .stream()
                .map(InventoryResult:: fromEntity)
                .toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InventoryResult createInventory(CreateInventoryCommand command){
        validateCommand(command);


        ProductInfo product = productClient
                .findActiveProduct(command.productId())
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "등록 가능한 상품 또는 소유 업체를 찾을 수 없습니다."
                ));

        if (!command.actorIsMaster()
                && !command.actorId().equals(product.managerUserId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }

        if (!command.hubId().equals(product.hubId())) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT,
                    "상품 소유 업체의 관리 허브에만 재고를 등록할 수 있습니다."
            );
        }

        return transactionTemplate.execute(status -> {
            hubRepository.findById(command.hubId())
                    .orElseThrow(() -> new BusinessException(
                            CommonErrorCode.RESOURCE_NOT_FOUND,
                            "해당하는 허브를 찾을 수 없습니다."
                    ));

            if (inventoryRepository.existsByHubIdAndProductId(
                    command.hubId(),
                    command.productId()
            )) {
                throw new BusinessException(
                        CommonErrorCode.INVALID_INPUT,
                        "이미 등록된 허브·상품의 재고입니다."
                );
            }

            Inventory inventory = Inventory.create(
                    command.hubId(),
                    command.productId(),
                    command.initialQuantity()
            );

            Inventory savedInventory = inventoryRepository.save(inventory);

            return InventoryResult.fromEntity(savedInventory);
        });
    }

    //재고 등록 명령의 필수 값 누락과 초기 수량의 유효성 검증
    private void validateCommand(CreateInventoryCommand command) {
        if (command == null
                || command.hubId() == null
                || command.productId() == null
                || command.actorId() == null
                || command.initialQuantity() < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
