package com.company.product.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.ProductErrorCode;
import com.company.company.domain.entity.Company;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.util.UUID;


@Getter
@Entity
// 성능 측정 기준선(일반 조회): PK 외 인덱스 없음. 인덱스는 인덱스 조회 브랜치에서 추가한다
@Table(name = "p_products")
@Check(name = "ck_products_unit_price", constraints = "unit_price >= 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    // 상세 조회 수 (인기 상품용). 동시 조회에도 빠지지 않게 엔티티가 아니라 Repository의 조건부 UPDATE로만 올린다.
    // DEFAULT 0: 기존 행이 있는 테이블에 ddl-auto(update)로 NOT NULL 컬럼을 추가할 수 있도록
    @ColumnDefault("0")
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    private Product(Company company, String name, String description, BigDecimal unitPrice) {
        this.company = company;
        this.name = name;
        this.description = description;
        this.unitPrice = unitPrice;
    }

    public static Product create(Company company, String name, String description, BigDecimal unitPrice) {
        validateUnitPrice(unitPrice);
        return new Product(company, name, description, unitPrice);
    }

    // null인 값은 바꾸지 않는다. 단가는 생성과 같은 규칙으로 검사한다
    public void update(String name, String description, BigDecimal unitPrice) {
        if (unitPrice != null) {
            validateUnitPrice(unitPrice);
            this.unitPrice = unitPrice;
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
    }

    // 논리 삭제 (07 서비스 정책 1.3). 이미 삭제된 상품은 서비스에서 404로 걸러진다
    public void delete(UUID actorId) {
        softDelete(actorId);
    }

    // 상품 가격은 0 이상 (07 서비스 정책 4장). 요청 DTO에서 먼저 막지만, 다른 경로로 만들어도 지켜지도록 도메인에서도 검사한다
    private static void validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new BusinessException(ProductErrorCode.PRODUCT_INVALID_UNIT_PRICE);
        }
    }
}
