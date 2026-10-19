package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    // 벌크 UPDATE에는 Auditing이 적용되지 않으므로 삭제·수정 감사 정보를 함께 기록한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.deletedAt = :deletedAt,
                i.deletedBy = :actorId,
                i.updatedAt = :deletedAt,
                i.updatedBy = :actorId
            WHERE i.productId = :productId
              AND i.deletedAt IS NULL
            """)
    int softDeleteActiveByProductId(
            @Param("productId") UUID productId,
            @Param("actorId") UUID actorId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    // 재고가 충분하면 차감
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.quantity = i.quantity - :quantity,
                i.updatedAt = :changedAt,
                i.updatedBy = COALESCE(:actorId, i.updatedBy)
            WHERE i.hubId = :hubId
              AND i.productId = :productId
              AND i.deletedAt IS NULL
              AND i.quantity >= :quantity
            """)
    int decreaseIfEnough(
            @Param("hubId") UUID hubId,
            @Param("productId") UUID productId,
            @Param("quantity") int quantity,
            @Param("actorId") UUID actorId,
            @Param("changedAt") LocalDateTime changedAt
    );

    // 차감된 재고의 ID와 남은 수량 조회
    @Query("""
            SELECT i.id AS inventoryId,
                   i.quantity AS remainingQuantity
            FROM Inventory i
            WHERE i.hubId = :hubId
              AND i.productId = :productId
              AND i.deletedAt IS NULL
            """)
    Optional<StockSnapshot> findActiveStockSnapshot(
            @Param("hubId") UUID hubId,
            @Param("productId") UUID productId
    );

    interface StockSnapshot {
        UUID getInventoryId();
        int getRemainingQuantity();
    }
}
