package com.company.company.application.service;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.CompanySearchQuery;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeHubClient;
import com.company.fake.FakeUserClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 업체 목록 조회 (04 API 명세서 Company, 07 서비스 정책 1.5·4장): 로그인 사용자 모두, hubId·name 검색은 선택.
 */
class SearchCompaniesTest {

    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID OTHER_HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final CompanyService service =
            new CompanyService(companyRepository, new FakeUserClient(), new FakeHubClient(), TransactionOperations.withoutTransaction());

    private Company saveCompany(UUID hubId, String name) {
        return companyRepository.save(Company.create(name, "서울시 강남구 테헤란로 1", hubId, UUID.randomUUID()));
    }

    private PageData<CompanyResult> search(UUID hubId, String name) {
        return service.searchCompanies(new CompanySearchQuery(hubId, name),
                new PageQuery(0, 10, List.of(new PageQuery.PageOrder("createdAt", false))));
    }

    @Test
    @DisplayName("조건이 없으면 모든 허브의 업체를 돌려주고, 삭제된 업체는 목록과 전체 건수에서 빠진다")
    void noCondition() {
        saveCompany(HUB_ID, "스파르타상사");
        saveCompany(OTHER_HUB_ID, "부산수산");
        Company deleted = saveCompany(HUB_ID, "삭제된상사");
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());

        PageData<CompanyResult> result = search(null, null);

        assertThat(result.content()).extracting(CompanyResult::name).containsExactly("스파르타상사", "부산수산");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("hubId·name을 함께 보내면 두 조건을 모두 만족하는 업체만 돌려준다 (name은 부분 일치, 앞뒤 공백 무시)")
    void hubAndName() {
        saveCompany(HUB_ID, "스파르타상사");
        saveCompany(HUB_ID, "스파르타물산");
        saveCompany(HUB_ID, "부산수산");
        saveCompany(OTHER_HUB_ID, "스파르타유통");

        PageData<CompanyResult> result = search(HUB_ID, " 스파르타 ");

        assertThat(result.content()).extracting(CompanyResult::name).containsExactly("스파르타상사", "스파르타물산");
        assertThat(result.content()).extracting(CompanyResult::hubId).containsOnly(HUB_ID);
    }

    @Test
    @DisplayName("빈 name은 조건이 없는 것으로 본다")
    void blankName() {
        saveCompany(HUB_ID, "스파르타상사");
        saveCompany(OTHER_HUB_ID, "부산수산");

        assertThat(search(null, "  ").totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("업체가 없는 허브로 검색하면 404가 아니라 빈 목록 (허브 존재는 hub-ms로 확인하지 않는다)")
    void unknownHub() {
        saveCompany(HUB_ID, "스파르타상사");

        assertThat(search(UUID.randomUUID(), null).content()).isEmpty();
    }
}
