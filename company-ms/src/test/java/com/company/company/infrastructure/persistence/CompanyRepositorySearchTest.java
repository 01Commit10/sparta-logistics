package com.company.company.infrastructure.persistence;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import com.company.company.domain.repository.CompanySearchCondition;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 업체 목록 검색. 기본 테스트 DB(H2, PostgreSQL 모드)로 확인한다. */
@DataJpaTest
@Import(CompanyRepositoryImpl.class)
class CompanyRepositorySearchTest {

    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID OTHER_HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    static final PageOrder ID_ASC = new PageOrder("id", true);
    static final PageOrder NAME_ASC = new PageOrder("name", true);

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    EntityManager em;

    private Company persist(UUID hubId, String name) {
        Company company = Company.create(name, "서울시 강남구 테헤란로 1", hubId, UUID.randomUUID());
        em.persist(company);
        return company;
    }

    // PagePolicy를 거친 것처럼 마지막에 id ASC를 붙여 조회한다
    private PageData<Company> search(UUID hubId, String name, int page, int size, PageOrder... orders) {
        em.flush();
        em.clear();
        List<PageOrder> orderList = new ArrayList<>(List.of(orders));
        orderList.add(ID_ASC);
        return companyRepository.search(new CompanySearchCondition(hubId, name), new PageQuery(page, size, orderList));
    }

    private PageData<Company> search(UUID hubId, String name) {
        return search(hubId, name, 0, 10, NAME_ASC);
    }

    @Test
    @DisplayName("조건이 없으면 전체 업체를 조회하고, 삭제된 업체는 목록과 전체 건수에서 빠진다")
    void excludesDeleted() {
        persist(HUB_ID, "스파르타상사");
        persist(OTHER_HUB_ID, "부산수산");
        Company deleted = persist(HUB_ID, "삭제된상사");
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());

        PageData<Company> result = search(null, null);

        assertThat(result.content()).extracting(Company::getName).containsExactly("부산수산", "스파르타상사");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("hubId는 일치, name은 대소문자를 무시한 부분 일치이고 두 조건은 AND")
    void hubAndName() {
        persist(HUB_ID, "Sparta Trading");
        persist(HUB_ID, "BIG SPARTA");
        persist(HUB_ID, "부산수산");
        persist(OTHER_HUB_ID, "sparta busan");

        assertThat(search(HUB_ID, "sparta").content()).extracting(Company::getName)
                .containsExactly("BIG SPARTA", "Sparta Trading");
        assertThat(search(null, "SPARTA").totalElements()).isEqualTo(3);
        assertThat(search(OTHER_HUB_ID, null).content()).extracting(Company::getName)
                .containsExactly("sparta busan");
    }

    @Test
    @DisplayName("검색어의 %·_는 와일드카드가 아니라 글자로 찾는다")
    void escapesWildcards() {
        persist(HUB_ID, "100%상사");
        persist(HUB_ID, "1000상사");
        persist(HUB_ID, "A_B상사");
        persist(HUB_ID, "AXB상사");

        assertThat(search(null, "100%").content()).extracting(Company::getName).containsExactly("100%상사");
        assertThat(search(null, "a_b").content()).extracting(Company::getName).containsExactly("A_B상사");
    }

    @Test
    @DisplayName("요청한 정렬 순서를 그대로 적용하고, page·size로 자른다")
    void sortAndPaging() {
        persist(HUB_ID, "B상사");
        persist(HUB_ID, "A상사");
        persist(OTHER_HUB_ID, "C상사");

        PageData<Company> first = search(null, null, 0, 2, new PageOrder("name", false));
        PageData<Company> second = search(null, null, 1, 2, new PageOrder("name", false));

        assertThat(first.content()).extracting(Company::getName).containsExactly("C상사", "B상사");
        assertThat(second.content()).extracting(Company::getName).containsExactly("A상사");
        assertThat(second.totalElements()).isEqualTo(3);
    }
}
