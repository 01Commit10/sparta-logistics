package com.company.product.domain.entity;

import com.common.exception.BusinessException;
import com.common.exception.ProductErrorCode;
import com.company.company.domain.entity.Company;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    static final Company COMPANY = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());

    @Test
    @DisplayName("생성: 소속 업체·상품명·설명·단가를 담고, ID는 저장 전까지 비어 있으며 삭제되지 않은 상태")
    void create() {
        Product product = Product.create(COMPANY, "생수 2L", "6개입", new BigDecimal("12000.00"));

        assertThat(product.getCompany()).isSameAs(COMPANY);
        assertThat(product.getName()).isEqualTo("생수 2L");
        assertThat(product.getDescription()).isEqualTo("6개입");
        assertThat(product.getUnitPrice()).isEqualByComparingTo("12000");
        assertThat(product.getId()).isNull();
        assertThat(product.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("단가 0은 허용한다")
    void zeroPrice() {
        assertThat(Product.create(COMPANY, "샘플", null, BigDecimal.ZERO).getUnitPrice()).isZero();
    }

    @Test
    @DisplayName("단가가 음수이거나 없으면 PRODUCT_INVALID_UNIT_PRICE")
    void invalidPrice() {
        for (BigDecimal price : new BigDecimal[]{new BigDecimal("-0.01"), null}) {
            assertThatThrownBy(() -> Product.create(COMPANY, "생수 2L", null, price))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_INVALID_UNIT_PRICE));
        }
    }
}
