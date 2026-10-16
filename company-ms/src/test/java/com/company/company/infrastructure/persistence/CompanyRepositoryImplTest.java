package com.company.company.infrastructure.persistence;

import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 기본 테스트 DB(H2, PostgreSQL 모드)로 확인한다. */
@DataJpaTest
@Import(CompanyRepositoryImpl.class)
class CompanyRepositoryImplTest {

    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("저장하면 ID가 생성되고, ID로 다시 조회된다 (생성 일시 자동 기록)")
    void saveAndFind() {
        Company saved = companyRepository.save(Company.create("스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID));
        em.flush();
        em.clear();

        assertThat(saved.getId()).isNotNull();
        Company found = companyRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("스파르타상사");
        assertThat(found.getAddress()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(found.getHubId()).isEqualTo(HUB_ID);
        assertThat(found.getManagerUserId()).isEqualTo(MANAGER_ID);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("없는 ID는 empty")
    void notFound() {
        assertThat(companyRepository.findById(UUID.randomUUID())).isEmpty();
    }
}
