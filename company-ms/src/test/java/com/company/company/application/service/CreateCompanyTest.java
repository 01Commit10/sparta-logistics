package com.company.company.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ErrorCode;
import com.common.remote.RemoteServiceException;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.CreateCompanyCommand;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeHubClient;
import com.company.fake.FakeUserClient;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업체 등록 (04 API 명세서 Company, 07 서비스 정책 4장): 관리 허브는 hub-ms, 업체 관리자는 user-ms로 존재를 검증한 뒤 저장한다.
 */
class CreateCompanyTest {

    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeUserClient userClient = new FakeUserClient().add(MANAGER_ID, "USER");
    private final FakeHubClient hubClient = new FakeHubClient().add(HUB_ID);
    private final CompanyService service = new CompanyService(companyRepository, userClient, hubClient, TransactionOperations.withoutTransaction());

    private static CreateCompanyCommand command(UUID hubId, UUID managerUserId) {
        return new CreateCompanyCommand("스파르타상사", "서울시 강남구 테헤란로 1", hubId, managerUserId);
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("허브와 관리자가 있으면 업체를 저장하고 생성된 ID와 입력값을 돌려준다")
    void create() {
        CompanyResult result = service.createCompany(command(HUB_ID, MANAGER_ID));

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("스파르타상사");
        assertThat(result.address()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(result.hubId()).isEqualTo(HUB_ID);
        assertThat(result.managerUserId()).isEqualTo(MANAGER_ID);

        Company saved = companyRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("스파르타상사");
        assertThat(saved.getHubId()).isEqualTo(HUB_ID);
        assertThat(saved.getManagerUserId()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("없는 허브면 COMPANY_HUB_NOT_FOUND, 사용자 조회 없이 저장하지 않는다")
    void hubNotFound() {
        assertError(() -> service.createCompany(command(UUID.randomUUID(), MANAGER_ID)),
                CompanyErrorCode.COMPANY_HUB_NOT_FOUND);

        assertThat(userClient.calls()).isZero();
        assertThat(companyRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("없거나 삭제된 사용자를 관리자로 지정하면 COMPANY_MANAGER_NOT_FOUND, 저장하지 않는다")
    void managerNotFound() {
        assertError(() -> service.createCompany(command(HUB_ID, UUID.randomUUID())),
                CompanyErrorCode.COMPANY_MANAGER_NOT_FOUND);

        assertThat(companyRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("hub-ms 장애는 원격 예외 그대로 던지고(503) 저장하지 않는다")
    void hubUnavailable() {
        hubClient.failWith(new RemoteServiceException("hub-ms", 500));

        assertError(() -> service.createCompany(command(HUB_ID, MANAGER_ID)),
                CommonErrorCode.SERVICE_UNAVAILABLE);

        assertThat(userClient.calls()).isZero();
        assertThat(companyRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("user-ms 장애는 원격 예외 그대로 던지고(503) 저장하지 않는다")
    void userUnavailable() {
        userClient.failWith(new RemoteServiceException("user-ms", 500));

        assertThatThrownBy(() -> service.createCompany(command(HUB_ID, MANAGER_ID)))
                .isInstanceOf(RemoteServiceException.class);

        assertThat(companyRepository.all()).isEmpty();
    }
}
