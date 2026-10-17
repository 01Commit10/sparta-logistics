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

import java.math.BigDecimal;
import java.util.UUID;


@Getter
@Entity
@Table(name = "p_products", indexes = @Index(name = "idx_products_company_id", columnList = "company_id"))
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

    // 상품 가격은 0 이상 (07 서비스 정책 4장). 요청 DTO에서 먼저 막지만, 다른 경로로 만들어도 지켜지도록 도메인에서도 검사한다
    private static void validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new BusinessException(ProductErrorCode.PRODUCT_INVALID_UNIT_PRICE);
        }
    }
}
