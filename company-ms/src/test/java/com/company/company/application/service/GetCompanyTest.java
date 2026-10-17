package com.company.company.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.company.company.application.dto.CompanyResult;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeHubClient;
import com.company.fake.FakeUserClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업체 상세 조회 (04 API 명세서 Company, 07 서비스 정책 4장): 로그인 사용자 모두, 없거나 삭제된 업체는 404.
 */
class GetCompanyTest {

    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final CompanyService service =
            new CompanyService(companyRepository, new FakeUserClient(), new FakeHubClient(), TransactionOperations.withoutTransaction());

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID));

    private void assertNotFound(UUID companyId) {
        assertThatThrownBy(() -> service.getCompany(companyId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CompanyErrorCode.COMPANY_NOT_FOUND));
    }

    @Test
    @DisplayName("업체 정보와 관리 허브·관리자 ID를 돌려준다")
    void found() {
        CompanyResult result = service.getCompany(company.getId());

        assertThat(result.id()).isEqualTo(company.getId());
        assertThat(result.name()).isEqualTo("스파르타상사");
        assertThat(result.address()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(result.hubId()).isEqualTo(HUB_ID);
        assertThat(result.managerUserId()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("없는 업체면 COMPANY_NOT_FOUND")
    void notFound() {
        assertNotFound(UUID.randomUUID());
    }

    @Test
    @DisplayName("삭제된 업체면 COMPANY_NOT_FOUND")
    void deleted() {
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertNotFound(company.getId());
    }
}
