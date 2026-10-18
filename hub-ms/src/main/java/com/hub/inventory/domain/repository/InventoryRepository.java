package com.hub.inventory.domain.repository;

import com.hub.inventory.domain.entity.Inventory;

import java.util.List;
import java.util.UUID;

public interface InventoryRepository {
    // 논리 삭제되지 않은 재고를 허브 ID 또는 상품 ID로 검색한다.
    // 두 값이 있으면 모두 일치하는 재고를, 둘 다 null이면 전체 재고를 조회한다.
    List<Inventory> findAll(UUID hubId, UUID productId);

    //같은 허브&상품 조합이 이미 등록되어있는지 확인
    boolean existsByHubIdAndProductId(UUID hubId, UUID productId);

    // 새 재고 저장후 Inventory  entity 반환
    Inventory save(Inventory inventory);

}
