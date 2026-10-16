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
        @DisplayName("MASTER 생성: 역할·배송담당 여부를 지정하고 작성자는 MASTER")
        void createByMaster() {
            User user = User.createByMaster(USER_ID, Username.of("courier01"), "배송원", "hashed", "U002",
                    OrganizationType.HUB, "서울센터", UserRole.USER, true, MASTER_ID);

            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(user.isDeliveryManager()).isTrue();
            assertThat(user.getCreatedBy()).isEqualTo(MASTER_ID);
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
        @DisplayName("역할 변경과 배송담당자 지정")
        void roleAndDeliveryManager() {
            User user = signupUser();

            user.changeRole(UserRole.MASTER);
            user.assignDeliveryManager(true);

            assertThat(user.isMaster()).isTrue();
            assertThat(user.isDeliveryManager()).isTrue();
        }
    }
}
