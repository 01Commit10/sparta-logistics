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

/**
 * 재고 수량 변경을 @Query 조건부 UPDATE로 하는 이유:
 * 엔티티를 조회해 값을 바꾸면 동시 요청이 같은 수량을 읽고 각자 덮어써 차감 하나가 사라질 수 있다(Inventory에는 @Version이 없다).
 * 조건 검사와 변경을 UPDATE 한 문장으로 실행하면, READ COMMITTED에서 PostgreSQL은 같은 행의 뒤 UPDATE를 앞 트랜잭션이 끝날 때까지 기다리게 하고
 * 커밋된 값으로 조건을 다시 확인하므로 재고가 음수가 되지 않는다. 성공 여부는 변경된 행 수(0 또는 1)로 판단한다.
 * 벌크 UPDATE는 Auditing을 거치지 않으므로 updatedAt·updatedBy를 직접 넣고, 변경 후 수량은 별도 조회로 읽는다.
 */
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

    // 재고를 복원하는 메소드, 삭제된 재고와 수량 한도 초과 대상은 변경하지 않는다.
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.quantity = i.quantity + :quantity,
            i.updatedAt = :changedAt,
            i.updatedBy = COALESCE(:actorId, i.updatedBy)
        WHERE i.id = :inventoryId
          AND i.deletedAt IS NULL
          AND i.quantity <= 2147483647 - :quantity
        """)
    int restoreStock(
            @Param("inventoryId") UUID inventoryId,
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

    // 재고 ID로 삭제되지 않은 재고의 현재 수량을 조회한다.
    @Query("""
        SELECT i.id AS inventoryId,
               i.quantity AS remainingQuantity
        FROM Inventory i
        WHERE i.id = :inventoryId
          AND i.deletedAt IS NULL
        """)
    Optional<StockSnapshot> findStockById(
            @Param("inventoryId") UUID inventoryId
    );

    interface StockSnapshot {
        UUID getInventoryId();
        int getRemainingQuantity();
    }
}
