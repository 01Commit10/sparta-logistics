package com.company.product.infrastructure.persistence;

import com.company.company.domain.entity.Company;
import com.company.product.domain.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 생성된 테이블(H2, PostgreSQL 모드)의 컬럼·제약조건 확인 */
@DataJpaTest
class ProductSchemaTest {

    @Autowired
    EntityManager em;

    private Company saveCompany() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());
        em.persist(company);
        return company;
    }

    @Test
    @DisplayName("저장 후 다시 조회하면 단가가 소수 둘째 자리까지 보존된다")
    void saveAndFind() {
        Product saved = Product.create(saveCompany(), "생수 2L", "6개입", new BigDecimal("12000.50"));
        em.persist(saved);
        em.flush();
        em.clear();

        Product found = em.find(Product.class, saved.getId());

        assertThat(found.getUnitPrice()).isEqualByComparingTo("12000.50");
        assertThat(found.getCompany().getName()).isEqualTo("스파르타상사");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("단가가 음수면 CHECK 제약조건으로 저장되지 않는다 (도메인 검증을 거치지 않은 경우의 마지막 방어)")
    void negativePriceRejected() {
        Product product = Product.create(saveCompany(), "생수 2L", null, BigDecimal.ONE);
        ReflectionTestUtils.setField(product, "unitPrice", new BigDecimal("-1"));
        em.persist(product);

        assertThatThrownBy(() -> em.flush()).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("상품명이 150자를 넘으면 저장되지 않는다")
    void nameTooLong() {
        em.persist(Product.create(saveCompany(), "가".repeat(151), null, BigDecimal.ONE));

        assertThatThrownBy(() -> em.flush()).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("view_count를 빼고 INSERT하면 DEFAULT 0이 들어간다 (기존 행에 컬럼을 추가할 때와 같은 조건)")
    void viewCountDefaultsToZero() {
        Company company = saveCompany();
        em.flush();
        UUID productId = UUID.randomUUID();

        em.createNativeQuery("insert into company.p_products (id, company_id, name, unit_price, created_at) "
                        + "values (?, ?, '생수 2L', 1000, now())")
                .setParameter(1, productId)
                .setParameter(2, company.getId())
                .executeUpdate();
        em.clear();

        assertThat(em.find(Product.class, productId).getViewCount()).isZero();
    }
}
