package com.company.company.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ErrorCode;
import com.common.remote.RemoteServiceException;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.UpdateCompanyCommand;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeHubClient;
import com.company.fake.FakeUserClient;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업체 수정 (04 API 명세서 Company, 07 서비스 정책 4장): MASTER 또는 업체 관리자 본인만, 보낸 필드만 바꾼다.
 * 관리 허브·업체 관리자 변경은 MASTER만 하며, 바뀌는 값만 hub-ms·user-ms로 존재를 검증한다.
 */
class UpdateCompanyTest {

    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID NEW_HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID NEW_MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeUserClient userClient = new FakeUserClient().add(NEW_MANAGER_ID, "USER");
    private final FakeHubClient hubClient = new FakeHubClient().add(NEW_HUB_ID);
    private final CompanyService service =
            new CompanyService(companyRepository, userClient, hubClient, TransactionOperations.withoutTransaction());

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID));

    private UpdateCompanyCommand command(String name, String address, UUID hubId, UUID managerUserId,
                                         UUID actorId, boolean master) {
        return new UpdateCompanyCommand(company.getId(), name, address, hubId, managerUserId, actorId, master);
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private void assertUnchanged() {
        assertThat(company.getName()).isEqualTo("스파르타상사");
        assertThat(company.getAddress()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(company.getHubId()).isEqualTo(HUB_ID);
        assertThat(company.getManagerUserId()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("업체 관리자 본인이 업체명·주소를 수정하면 보낸 필드만 바꾸고, 원격 검증은 하지 않는다")
    void updateByManager() {
        CompanyResult result = service.updateCompany(command("스파르타물산", null, null, null, MANAGER_ID, false));

        assertThat(result.id()).isEqualTo(company.getId());
        assertThat(result.name()).isEqualTo("스파르타물산");
        assertThat(result.address()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(result.hubId()).isEqualTo(HUB_ID);
        assertThat(result.managerUserId()).isEqualTo(MANAGER_ID);
        assertThat(company.getName()).isEqualTo("스파르타물산");
        assertThat(hubClient.calls()).isZero();
        assertThat(userClient.calls()).isZero();
    }

    @Test
    @DisplayName("MASTER는 관리 허브·업체 관리자까지 바꿀 수 있고, 바뀌는 허브·관리자를 검증한다")
    void updateByMaster() {
        CompanyResult result = service.updateCompany(
                command(null, "부산시 해운대구 1", NEW_HUB_ID, NEW_MANAGER_ID, MASTER_ID, true));

        assertThat(result.address()).isEqualTo("부산시 해운대구 1");
        assertThat(result.hubId()).isEqualTo(NEW_HUB_ID);
        assertThat(result.managerUserId()).isEqualTo(NEW_MANAGER_ID);
        assertThat(hubClient.calls()).isEqualTo(1);
        assertThat(userClient.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재와 같은 허브·관리자를 보내면 바뀌지 않는 것으로 보고 원격 검증 없이 관리자도 수정할 수 있다")
    void sameHubAndManager() {
        CompanyResult result = service.updateCompany(
                command("스파르타물산", null, HUB_ID, MANAGER_ID, MANAGER_ID, false));

        assertThat(result.name()).isEqualTo("스파르타물산");
        assertThat(hubClient.calls()).isZero();
        assertThat(userClient.calls()).isZero();
    }

    @Test
    @DisplayName("업체 관리자가 아닌 USER는 ACCESS_DENIED, 바꾸지 않는다")
    void notManager() {
        assertError(() -> service.updateCompany(command("바뀐 이름", null, null, null, OTHER_USER_ID, false)),
                CommonErrorCode.ACCESS_DENIED);

        assertUnchanged();
    }

    @Test
    @DisplayName("업체 관리자라도 관리 허브를 바꾸면 ACCESS_DENIED, 원격 호출 없이 바꾸지 않는다")
    void managerCannotChangeHub() {
        assertError(() -> service.updateCompany(command("바뀐 이름", null, NEW_HUB_ID, null, MANAGER_ID, false)),
                CommonErrorCode.ACCESS_DENIED);

        assertThat(hubClient.calls()).isZero();
        assertUnchanged();
    }

    @Test
    @DisplayName("업체 관리자라도 업체 관리자를 바꾸면 ACCESS_DENIED, 원격 호출 없이 바꾸지 않는다")
    void managerCannotChangeManager() {
        assertError(() -> service.updateCompany(command(null, null, null, NEW_MANAGER_ID, MANAGER_ID, false)),
                CommonErrorCode.ACCESS_DENIED);

        assertThat(userClient.calls()).isZero();
        assertUnchanged();
    }

    @Test
    @DisplayName("없는 업체면 COMPANY_NOT_FOUND (권한 없는 USER도 404)")
    void companyNotFound() {
        assertError(() -> service.updateCompany(new UpdateCompanyCommand(
                        UUID.randomUUID(), "바뀐 이름", null, null, null, OTHER_USER_ID, false)),
                CompanyErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 업체면 COMPANY_NOT_FOUND, 바꾸지 않는다")
    void deletedCompany() {
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertError(() -> service.updateCompany(command("바뀐 이름", null, null, null, MASTER_ID, true)),
                CompanyErrorCode.COMPANY_NOT_FOUND);

        assertUnchanged();
    }

    @Test
    @DisplayName("없는 허브로 바꾸면 COMPANY_HUB_NOT_FOUND, 사용자 조회 없이 바꾸지 않는다")
    void hubNotFound() {
        assertError(() -> service.updateCompany(
                        command("바뀐 이름", null, UUID.randomUUID(), NEW_MANAGER_ID, MASTER_ID, true)),
                CompanyErrorCode.COMPANY_HUB_NOT_FOUND);

        assertThat(userClient.calls()).isZero();
        assertUnchanged();
    }

    @Test
    @DisplayName("없거나 삭제된 사용자를 관리자로 바꾸면 COMPANY_MANAGER_NOT_FOUND, 바꾸지 않는다")
    void managerNotFound() {
        assertError(() -> service.updateCompany(
                        command("바뀐 이름", null, null, UUID.randomUUID(), MASTER_ID, true)),
                CompanyErrorCode.COMPANY_MANAGER_NOT_FOUND);

        assertUnchanged();
    }

    @Test
    @DisplayName("hub-ms 장애는 원격 예외 그대로 던지고(503) 바꾸지 않는다")
    void hubUnavailable() {
        hubClient.failWith(new RemoteServiceException("hub-ms", 500));

        assertError(() -> service.updateCompany(command("바뀐 이름", null, NEW_HUB_ID, null, MASTER_ID, true)),
                CommonErrorCode.SERVICE_UNAVAILABLE);

        assertUnchanged();
    }

    @Test
    @DisplayName("원격 검증 중 업체가 삭제되면 저장 단계에서 다시 읽어 COMPANY_NOT_FOUND, 되살리지 않는다")
    void deletedDuringRemoteValidation() {
        TransactionOperations deleteBeforeWrite = new TransactionOperations() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());
                return action.doInTransaction(null);
            }
        };
        CompanyService racing = new CompanyService(companyRepository, userClient, hubClient, deleteBeforeWrite);

        assertError(() -> racing.updateCompany(command("바뀐 이름", null, NEW_HUB_ID, null, MASTER_ID, true)),
                CompanyErrorCode.COMPANY_NOT_FOUND);

        assertThat(company.isDeleted()).isTrue();
        assertThat(company.getName()).isEqualTo("스파르타상사");
    }
}
