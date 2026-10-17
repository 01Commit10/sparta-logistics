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
    @DisplayName("수정: 넘긴 값만 바꾸고 null인 값은 그대로 둔다")
    void updatePartially() {
        Product product = Product.create(COMPANY, "생수 2L", "6개입", new BigDecimal("12000.00"));

        product.update(null, null, new BigDecimal("9000"));

        assertThat(product.getName()).isEqualTo("생수 2L");
        assertThat(product.getDescription()).isEqualTo("6개입");
        assertThat(product.getUnitPrice()).isEqualByComparingTo("9000");

        product.update("생수 500ml", "20개입", null);

        assertThat(product.getName()).isEqualTo("생수 500ml");
        assertThat(product.getDescription()).isEqualTo("20개입");
        assertThat(product.getUnitPrice()).isEqualByComparingTo("9000");
    }

    @Test
    @DisplayName("수정: 단가가 음수면 PRODUCT_INVALID_UNIT_PRICE, 다른 필드도 바꾸지 않는다")
    void updateInvalidPrice() {
        Product product = Product.create(COMPANY, "생수 2L", "6개입", new BigDecimal("12000.00"));

        assertThatThrownBy(() -> product.update("바뀐 이름", null, new BigDecimal("-1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_INVALID_UNIT_PRICE));

        assertThat(product.getName()).isEqualTo("생수 2L");
        assertThat(product.getUnitPrice()).isEqualByComparingTo("12000");
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
