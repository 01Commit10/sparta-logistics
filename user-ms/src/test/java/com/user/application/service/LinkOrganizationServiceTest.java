package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.ErrorCode;
import com.common.exception.UserErrorCode;
import com.user.application.dto.LinkOrganizationCommand;
import com.user.application.dto.UserResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeTemporaryPasswordGenerator;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-16 소속 연결 (U20 테스트 1~2). 업체·허브 존재 확인은 하지 않는다 (2026-10-16 결정).
 */
class LinkOrganizationServiceTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher(),
            new FakeTemporaryPasswordGenerator());

    private User saved(String username, OrganizationType organizationType, String organizationName) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed",
                "U-" + username, organizationType, organizationName));
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. 같은 유형이면 소속명 없이 연결하고 기존 소속명을 유지한다 (COMPANY)")
    void linkSameType() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");
        UUID companyId = UUID.randomUUID();

        UserResult result = service.linkOrganization(new LinkOrganizationCommand(user.getId(), "COMPANY", companyId, null));

        assertThat(result.organizationType()).isEqualTo("COMPANY");
        assertThat(result.organizationId()).isEqualTo(companyId);
        assertThat(result.organizationName()).isEqualTo("스파르타상사");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getOrganizationId()).isEqualTo(companyId);
    }

    @Test
    @DisplayName("1. 같은 유형에서 소속명을 보내면 함께 바꾼다 (HUB)")
    void linkSameTypeWithName() {
        User user = saved("user01", OrganizationType.HUB, "서울");
        UUID hubId = UUID.randomUUID();

        UserResult result = service.linkOrganization(new LinkOrganizationCommand(user.getId(), "HUB", hubId, "서울특별시 센터"));

        assertThat(result.organizationId()).isEqualTo(hubId);
        assertThat(result.organizationName()).isEqualTo("서울특별시 센터");
    }

    @Test
    @DisplayName("1. 다른 연결로 바꿀 수 있다 (재연결)")
    void relink() {
        User user = saved("user01", OrganizationType.HUB, "서울센터");
        service.linkOrganization(new LinkOrganizationCommand(user.getId(), "HUB", UUID.randomUUID(), null));
        UUID other = UUID.randomUUID();

        assertThat(service.linkOrganization(new LinkOrganizationCommand(user.getId(), "HUB", other, "경기센터"))
                .organizationId()).isEqualTo(other);
    }

    @Test
    @DisplayName("1. 유형이 바뀌면 소속명과 함께 바꾼다 (COMPANY → HUB)")
    void linkTypeChange() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");
        UUID hubId = UUID.randomUUID();

        UserResult result = service.linkOrganization(new LinkOrganizationCommand(user.getId(), "HUB", hubId, "서울센터"));

        assertThat(result.organizationType()).isEqualTo("HUB");
        assertThat(result.organizationName()).isEqualTo("서울센터");
        assertThat(result.organizationId()).isEqualTo(hubId);
    }

    @Test
    @DisplayName("2. 유형이 바뀌는데 소속명이 없으면 INVALID_INPUT, 그대로")
    void typeChangeWithoutName() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");

        assertError(() -> service.linkOrganization(new LinkOrganizationCommand(user.getId(), "HUB", UUID.randomUUID(), null)),
                CommonErrorCode.INVALID_INPUT);
        assertThat(user.getOrganizationType()).isEqualTo(OrganizationType.COMPANY);
        assertThat(user.getOrganizationId()).isNull();
    }

    @Test
    @DisplayName("2. HQ·알 수 없는 유형이면 INVALID_ORGANIZATION (presentation을 거치지 않은 호출 방어)")
    void hq() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");

        assertError(() -> service.linkOrganization(new LinkOrganizationCommand(user.getId(), "HQ", UUID.randomUUID(), "본사")),
                UserErrorCode.INVALID_ORGANIZATION);
        assertError(() -> service.linkOrganization(new LinkOrganizationCommand(user.getId(), "hub", UUID.randomUUID(), "서울")),
                UserErrorCode.INVALID_ORGANIZATION);
        assertThat(user.getOrganizationId()).isNull();
    }

    @Test
    @DisplayName("2. 배송담당자를 업체에 연결하면 DELIVERY_MANAGER_REQUIRES_HUB, 그대로")
    void deliveryManager() {
        User driver = userRepository.save(User.createByMaster(UUID.randomUUID(), Username.of("driver01"), "김배송",
                "hashed", "U001", OrganizationType.HUB, "서울센터", UserRole.USER, true, MASTER_ID));

        assertError(() -> service.linkOrganization(new LinkOrganizationCommand(driver.getId(), "COMPANY", UUID.randomUUID(),
                "스파르타상사")), UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
        assertThat(driver.getOrganizationType()).isEqualTo(OrganizationType.HUB);
    }

    @Test
    @DisplayName("2. HQ 소속 MASTER는 업체·허브에 연결할 수 있다 (소속명 필수)")
    void hqMaster() {
        User master = userRepository.save(User.initialMaster(UUID.randomUUID(), Username.of("master02"), "관리자",
                "hashed", "M002"));
        UUID hubId = UUID.randomUUID();

        UserResult result = service.linkOrganization(new LinkOrganizationCommand(master.getId(), "HUB", hubId, "서울센터"));

        assertThat(result.organizationType()).isEqualTo("HUB");
        assertThat(result.role()).isEqualTo("MASTER");
    }

    @Test
    @DisplayName("2. 없거나 삭제된 사용자면 USER_NOT_FOUND")
    void notFound() {
        User gone = saved("gone01", OrganizationType.HUB, "서울센터");
        gone.withdraw(gone.getId());

        assertError(() -> service.linkOrganization(new LinkOrganizationCommand(UUID.randomUUID(), "HUB", UUID.randomUUID(), null)),
                UserErrorCode.USER_NOT_FOUND);
        assertError(() -> service.linkOrganization(new LinkOrganizationCommand(gone.getId(), "HUB", UUID.randomUUID(), null)),
                UserErrorCode.USER_NOT_FOUND);
        assertThat(gone.getOrganizationId()).isNull();
    }
}
