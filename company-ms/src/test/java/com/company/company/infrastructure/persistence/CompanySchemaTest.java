package com.company.company.infrastructure.persistence;

import com.company.company.domain.entity.Company;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 생성된 테이블(H2, PostgreSQL 모드)의 컬럼 길이·인덱스 확인 (06 테이블 명세서 p_companies)
 */
@DataJpaTest
class CompanySchemaTest {

    @Autowired
    EntityManager em;

    private void persist(String name, String address) {
        em.persist(Company.create(name, address, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("업체명 150자, 주소 255자까지는 저장된다")
    void maxLength() {
        persist("가".repeat(150), "가".repeat(255));

        assertThatCode(() -> em.flush()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("업체명이 150자를 넘으면 저장되지 않는다")
    void nameTooLong() {
        persist("가".repeat(151), "서울시 강남구 테헤란로 1");

        assertThatThrownBy(() -> em.flush()).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("주소가 255자를 넘으면 저장되지 않는다")
    void addressTooLong() {
        persist("스파르타상사", "가".repeat(256));

        assertThatThrownBy(() -> em.flush()).isInstanceOf(PersistenceException.class);
    }
}
