package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryJpaRepository extends JpaRepository<Inventory, UUID> {
    // 논리 삭제되지 않은 재고를 허브 ID 또는 상품 ID로 검색한다.
    // 두 값이 있으면 모두 일치하는 재고를, 둘 다 null이면 전체 재고를 조회
    @Query("""
                    SELECT i
                    FROM Inventory i
                    WHERE i.deletedAt IS NULL                             
                        AND (:hubId IS NULL OR i.hubId = :hubId)
                        AND (:productId IS NULL OR i.productId = :productId)
                    ORDER BY i.createdAt DESC, i.id ASC                                
                   """)
    List<Inventory> findAllByConditions(
            @Param("hubId") UUID hubId,
            @Param("productId") UUID productId
            );

    boolean existsByHubIdAndProductId(
            UUID hubId,
            UUID productId
    );

}
