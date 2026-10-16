package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.ErrorCode;
import com.common.exception.UserErrorCode;
import com.user.application.dto.UpdateUserCommand;
import com.user.application.dto.UserResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-13 MASTER의 정보 수정 (U14 테스트 1~4).
 */
class UpdateByMasterTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher());

    private User saved(String username, OrganizationType type, String organizationName) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed",
                "U-" + username, type, organizationName));
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. 보낸 필드만 바뀌고 보내지 않은(null) 필드는 그대로다. 역할·배송담당은 바뀌지 않는다")
    void partialUpdate() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");

        UserResult result = service.updateByMaster(new UpdateUserCommand(user.getId(), "김철수", null, null, null));

        assertThat(result.name()).isEqualTo("김철수");
        assertThat(result.slackId()).isEqualTo("U-user01");
        assertThat(result.organizationType()).isEqualTo("COMPANY");
        assertThat(result.organizationName()).isEqualTo("스파르타상사");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("소속명만 바꿀 수 있다 (유형 그대로, 연결된 조직 ID 유지)")
    void organizationNameOnly() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");
        UUID companyId = UUID.randomUUID();
        user.linkOrganization(OrganizationType.COMPANY, companyId);

        UserResult result = service.updateByMaster(new UpdateUserCommand(user.getId(), null, null, null, "스파르타물산"));

        assertThat(result.organizationName()).isEqualTo("스파르타물산");
        assertThat(result.organizationId()).isEqualTo(companyId);
    }

    @Test
    @DisplayName("2. 다른 활성 사용자의 Slack ID면 DUPLICATE_SLACK_ID, 자기 자신의 값은 허용")
    void slackId() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");
        saved("user02", OrganizationType.COMPANY, "스파르타상사");

        assertError(() -> service.updateByMaster(new UpdateUserCommand(user.getId(), null, "U-user02", null, null)),
                UserErrorCode.DUPLICATE_SLACK_ID);
        assertThat(user.getSlackId()).isEqualTo("U-user01");
        assertThat(service.updateByMaster(new UpdateUserCommand(user.getId(), null, "U-user01", null, null)).slackId())
                .isEqualTo("U-user01");
        assertThat(service.updateByMaster(new UpdateUserCommand(user.getId(), null, "U-new", null, null)).slackId())
                .isEqualTo("U-new");
    }

    @Test
    @DisplayName("3. 소속 유형을 바꾸면 소속명도 바뀌고 연결된 조직 ID는 지워진다")
    void organizationTypeChange() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");
        user.linkOrganization(OrganizationType.COMPANY, UUID.randomUUID());

        UserResult result = service.updateByMaster(new UpdateUserCommand(user.getId(), null, null, "HUB", "서울센터"));

        assertThat(result.organizationType()).isEqualTo("HUB");
        assertThat(result.organizationName()).isEqualTo("서울센터");
        assertThat(result.organizationId()).isNull();
    }

    @Test
    @DisplayName("소속 유형을 바꾸면서 소속명이 없으면 INVALID_INPUT, 바뀌지 않는다. 같은 유형을 다시 보내는 건 된다")
    void organizationTypeWithoutName() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");

        assertError(() -> service.updateByMaster(new UpdateUserCommand(user.getId(), null, null, "HUB", null)),
                CommonErrorCode.INVALID_INPUT);
        assertThat(user.getOrganizationType()).isEqualTo(OrganizationType.COMPANY);

        UserResult same = service.updateByMaster(new UpdateUserCommand(user.getId(), "김철수", null, "COMPANY", null));
        assertThat(same.organizationName()).isEqualTo("스파르타상사");
    }

    @Test
    @DisplayName("없는 소속 유형은 INVALID_ORGANIZATION")
    void unknownOrganizationType() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");

        assertError(() -> service.updateByMaster(new UpdateUserCommand(user.getId(), null, null, "SHOP", "가게")),
                UserErrorCode.INVALID_ORGANIZATION);
    }

    @Test
    @DisplayName("4. USER를 본사(HQ)로 옮기면 INVALID_ORGANIZATION, MASTER는 된다")
    void hqRule() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");
        User master = userRepository.save(User.createByMaster(UUID.randomUUID(), Username.of("master02"), "관리자",
                "hashed", "U-master02", OrganizationType.HUB, "서울센터", UserRole.MASTER, false, UUID.randomUUID()));

        assertError(() -> service.updateByMaster(new UpdateUserCommand(user.getId(), null, null, "HQ", "본사")),
                UserErrorCode.INVALID_ORGANIZATION);
        assertThat(service.updateByMaster(new UpdateUserCommand(master.getId(), null, null, "HQ", "본사"))
                .organizationType()).isEqualTo("HQ");
    }

    @Test
    @DisplayName("배송담당자를 허브가 아닌 곳으로 옮기면 DELIVERY_MANAGER_REQUIRES_HUB")
    void deliveryManagerStaysInHub() {
        User driver = saved("driver01", OrganizationType.HUB, "서울센터");
        driver.assignDeliveryManager(true);

        assertError(() -> service.updateByMaster(new UpdateUserCommand(driver.getId(), null, null, "COMPANY", "상사")),
                UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
    }

    @Test
    @DisplayName("삭제된 사용자·없는 사용자는 USER_NOT_FOUND")
    void notFound() {
        User user = saved("user01", OrganizationType.COMPANY, "스파르타상사");
        user.withdraw(user.getId());

        assertError(() -> service.updateByMaster(new UpdateUserCommand(user.getId(), "김철수", null, null, null)),
                UserErrorCode.USER_NOT_FOUND);
        assertError(() -> service.updateByMaster(new UpdateUserCommand(UUID.randomUUID(), "김철수", null, null, null)),
                UserErrorCode.USER_NOT_FOUND);
    }
}
