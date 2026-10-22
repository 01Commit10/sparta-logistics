package com.hub.inventory.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_inventories",
        schema = "hub",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inventories_hub_product",
                        columnNames = {"hub_id", "product_id"}
                )
        }
)
@Check(
        name = "ck_inventories_quantity_non_negative",
        constraints = "quantity >= 0"
)
public class Inventory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "hub_id", nullable = false)
    private UUID hubId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private Inventory(UUID hubId, UUID productId, int quantity) {
        this.hubId = hubId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public static Inventory create(
            UUID hubId,
            UUID productId,
            int initialQuantity
    ){
        if (hubId == null || productId == null) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT,
                    "허브 ID와 상품 ID는 필수입니다."
            );
        }

        if (initialQuantity < 0) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT,
                    "초기 재고 수량은 0 이상이어야 합니다."
            );
        }
        return new Inventory(hubId, productId, initialQuantity);
    }

}
