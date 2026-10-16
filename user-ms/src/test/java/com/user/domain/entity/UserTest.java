package com.user.domain.entity;

import com.common.exception.BusinessException;
import com.common.exception.ErrorCode;
import com.common.exception.UserErrorCode;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static User signupUser() {
        return User.signup(USER_ID, Username.of("user01"), "홍길동", "hashed", "U001",
                OrganizationType.COMPANY, "스파르타상사");
    }

    static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("회원가입: USER, 배송담당 아님, 비밀번호 변경 불필요, 작성자는 본인, 활성")
        void signup() {
            User user = signupUser();

            assertThat(user.getId()).isEqualTo(USER_ID);
            assertThat(user.getUsername()).isEqualTo("user01");
            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(user.isDeliveryManager()).isFalse();
            assertThat(user.isPasswordChangeRequired()).isFalse();
            assertThat(user.getOrganizationId()).isNull();
            assertThat(user.getCreatedBy()).isEqualTo(USER_ID);
            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("회원가입으로는 본사(HQ) 소속을 선택할 수 없다")
        void signup_hqNotAllowed() {
            assertError(() -> User.signup(USER_ID, Username.of("user01"), "홍길동", "hashed", "U001",
                    OrganizationType.HQ, "본사"), UserErrorCode.INVALID_ORGANIZATION);
        }

        @Test
        @DisplayName("MASTER 생성: 역할·배송담당 여부를 지정하고 작성자는 MASTER, MASTER가 정한 비밀번호라 변경 필요")
        void createByMaster() {
            User user = User.createByMaster(USER_ID, Username.of("courier01"), "배송원", "hashed", "U002",
                    OrganizationType.HUB, "서울센터", UserRole.USER, true, MASTER_ID);

            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(user.isDeliveryManager()).isTrue();
            assertThat(user.getCreatedBy()).isEqualTo(MASTER_ID);
            assertThat(user.isPasswordChangeRequired()).isTrue();
            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("MASTER 생성: 본사(HQ) 소속은 MASTER 역할일 때만")
        void createByMaster_hqOnlyForMaster() {
            assertError(() -> User.createByMaster(USER_ID, Username.of("user02"), "직원", "hashed", "U003",
                    OrganizationType.HQ, "본사", UserRole.USER, false, MASTER_ID), UserErrorCode.INVALID_ORGANIZATION);

            User master = User.createByMaster(USER_ID, Username.of("master02"), "관리자", "hashed", "U004",
                    OrganizationType.HQ, "본사", UserRole.MASTER, false, MASTER_ID);
            assertThat(master.isMaster()).isTrue();
        }

        @Test
        @DisplayName("초기 MASTER: 본사 소속, 연결 조직 없음, 작성자 없음(시스템)")
        void initialMaster() {
            User master = User.initialMaster(MASTER_ID, Username.of("master01"), "관리자", "hashed", "U000");

            assertThat(master.getRole()).isEqualTo(UserRole.MASTER);
            assertThat(master.getOrganizationType()).isEqualTo(OrganizationType.HQ);
            assertThat(master.getOrganizationId()).isNull();
            assertThat(master.getCreatedBy()).isNull();
        }
    }

    @Nested
    @DisplayName("삭제·복구")
    class DeleteAndRestore {

        @Test
        @DisplayName("탈퇴·비활성화하면 삭제 일시와 삭제자가 기록되고 비활성이 된다")
        void withdraw() {
            User user = signupUser();

            LocalDateTime before = LocalDateTime.now();
            user.withdraw(USER_ID);

            assertThat(user.getDeletedAt()).isBetween(before, LocalDateTime.now());
            assertThat(user.getDeletedBy()).isEqualTo(USER_ID);
            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("이미 삭제된 사용자는 다시 삭제할 수 없다")
        void withdraw_alreadyDeleted() {
            User user = signupUser();
            user.withdraw(USER_ID);

            assertError(() -> user.withdraw(MASTER_ID), UserErrorCode.USER_ALREADY_DELETED);
        }

        @Test
        @DisplayName("재활성화하면 삭제 기록이 지워진다")
        void restore() {
            User user = signupUser();
            user.withdraw(USER_ID);

            user.restore();

            assertThat(user.getDeletedAt()).isNull();
            assertThat(user.getDeletedBy()).isNull();
            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("활성 사용자는 재활성화할 수 없다")
        void restore_active() {
            assertError(() -> signupUser().restore(), UserErrorCode.USER_NOT_DELETED);
        }
    }

    @Nested
    @DisplayName("변경")
    class Change {

        @Test
        @DisplayName("비밀번호 초기화하면 변경이 필요해지고, 본인이 바꾸면 해제된다")
        void passwordChangeRequired() {
            User user = signupUser();

            user.requirePasswordChange("temp-hash");
            assertThat(user.isPasswordChangeRequired()).isTrue();
            assertThat(user.getPasswordHash()).isEqualTo("temp-hash");

            user.changePassword("new-hash");
            assertThat(user.isPasswordChangeRequired()).isFalse();
            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        }

        @Test
        @DisplayName("소속 연결: 업체·허브는 ID를 연결하고, 본사(HQ)는 ID를 연결할 수 없다")
        void linkOrganization() {
            User user = signupUser();
            UUID companyId = UUID.randomUUID();

            user.linkOrganization(OrganizationType.COMPANY, companyId);
            assertThat(user.getOrganizationId()).isEqualTo(companyId);

            assertError(() -> user.linkOrganization(OrganizationType.HQ, UUID.randomUUID()), UserErrorCode.INVALID_ORGANIZATION);
        }

        @Test
        @DisplayName("정보 수정에서 소속 유형이 바뀌면 연결된 조직 ID가 지워진다")
        void updateProfile_resetsOrganizationId() {
            User user = signupUser();
            user.linkOrganization(OrganizationType.COMPANY, UUID.randomUUID());

            user.updateProfile("홍길동", "U001", OrganizationType.HUB, "서울센터");

            assertThat(user.getOrganizationType()).isEqualTo(OrganizationType.HUB);
            assertThat(user.getOrganizationId()).isNull();
        }

        @Test
        @DisplayName("정보 수정에서 소속 유형이 같으면 연결된 조직 ID가 유지된다")
        void updateProfile_keepsOrganizationId() {
            User user = signupUser();
            UUID companyId = UUID.randomUUID();
            user.linkOrganization(OrganizationType.COMPANY, companyId);

            user.updateProfile("홍길순", "U009", OrganizationType.COMPANY, "새상호");

            assertThat(user.getName()).isEqualTo("홍길순");
            assertThat(user.getSlackId()).isEqualTo("U009");
            assertThat(user.getOrganizationId()).isEqualTo(companyId);
        }

        @Test
        @DisplayName("역할 변경: USER↔MASTER")
        void changeRole() {
            User user = signupUser();

            user.changeRole(UserRole.MASTER);
            assertThat(user.isMaster()).isTrue();

            user.changeRole(UserRole.USER);
            assertThat(user.isMaster()).isFalse();
        }

        @Test
        @DisplayName("본사(HQ) 소속 MASTER는 USER로 바꿀 수 없다 (U15)")
        void changeRole_hqStaysMaster() {
            User master = User.initialMaster(MASTER_ID, Username.of("master01"), "관리자", "hashed", "U000");

            assertError(() -> master.changeRole(UserRole.USER), UserErrorCode.INVALID_ORGANIZATION);
            assertThat(master.isMaster()).isTrue();
        }

        @Test
        @DisplayName("배송담당자는 허브가 아닌 소속으로 옮길 수 없다, 바뀌지 않는다 (U14). 허브 안에서 이름만 바꾸는 건 된다")
        void updateProfile_deliveryManagerStaysInHub() {
            User driver = User.signup(USER_ID, Username.of("driver01"), "김배송", "hashed", "U010",
                    OrganizationType.HUB, "서울센터");
            driver.assignDeliveryManager(true);

            assertError(() -> driver.updateProfile("김배송", "U010", OrganizationType.COMPANY, "스파르타상사"),
                    UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
            assertThat(driver.getOrganizationType()).isEqualTo(OrganizationType.HUB);
            assertThat(driver.getOrganizationName()).isEqualTo("서울센터");

            driver.updateProfile("김배송", "U010", OrganizationType.HUB, "경기남부센터");
            assertThat(driver.getOrganizationName()).isEqualTo("경기남부센터");
        }

        @Test
        @DisplayName("정보 수정으로 USER를 본사(HQ)로 옮길 수 없다, 바뀌지 않는다")
        void updateProfile_hqOnlyForMaster() {
            User user = signupUser();

            assertError(() -> user.updateProfile("홍길동", "U001", OrganizationType.HQ, "본사"),
                    UserErrorCode.INVALID_ORGANIZATION);
            assertThat(user.getOrganizationType()).isEqualTo(OrganizationType.COMPANY);
        }
    }

    @Nested
    @DisplayName("배송담당자는 허브 소속만 (U16)")
    class DeliveryManager {

        private User hubUser() {
            return User.signup(USER_ID, Username.of("driver01"), "김배송", "hashed", "U010",
                    OrganizationType.HUB, "서울센터");
        }

        @Test
        @DisplayName("허브 소속 사용자는 지정하고 해제할 수 있다")
        void hubUser_assignAndUnassign() {
            User user = hubUser();

            user.assignDeliveryManager(true);
            assertThat(user.isDeliveryManager()).isTrue();

            user.assignDeliveryManager(false);
            assertThat(user.isDeliveryManager()).isFalse();
        }

        @Test
        @DisplayName("이미 지정된 사용자를 다시 지정해도 그대로다 (멱등)")
        void idempotent() {
            User user = hubUser();
            user.assignDeliveryManager(true);

            user.assignDeliveryManager(true);

            assertThat(user.isDeliveryManager()).isTrue();
        }

        @Test
        @DisplayName("업체 소속은 DELIVERY_MANAGER_REQUIRES_HUB, 바뀌지 않는다")
        void companyUser_rejected() {
            User user = signupUser();

            assertError(() -> user.assignDeliveryManager(true), UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
            assertThat(user.isDeliveryManager()).isFalse();
        }

        @Test
        @DisplayName("본사(HQ) 소속 MASTER도 지정할 수 없다")
        void hqMaster_rejected() {
            User master = User.initialMaster(MASTER_ID, Username.of("master01"), "관리자", "hashed", "U000");

            assertError(() -> master.assignDeliveryManager(true), UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
        }

        @Test
        @DisplayName("해제는 소속과 관계없이 언제나 된다")
        void unassign_anyOrganization() {
            User user = signupUser();

            user.assignDeliveryManager(false);

            assertThat(user.isDeliveryManager()).isFalse();
        }

        @Test
        @DisplayName("MASTER 생성에서도 허브가 아닌 배송담당자는 만들 수 없다 (U12)")
        void createByMaster_requiresHub() {
            assertError(() -> User.createByMaster(USER_ID, Username.of("courier02"), "배송원", "hashed", "U011",
                            OrganizationType.COMPANY, "스파르타상사", UserRole.USER, true, MASTER_ID),
                    UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
            assertError(() -> User.createByMaster(USER_ID, Username.of("master03"), "관리자", "hashed", "U012",
                            OrganizationType.HQ, "본사", UserRole.MASTER, true, MASTER_ID),
                    UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
        }
    }
}
