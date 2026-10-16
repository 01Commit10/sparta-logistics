package com.user.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.domain.service.PasswordPolicy;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 사용자 (auth.p_users, docs/design/06 테이블 명세서).
 *
 * <p>생성은 정적 팩토리로만, 상태 변경은 의도가 드러나는 메서드로만 한다 (setter 없음).
 * 비밀번호 원문은 받지 않는다. 원문 규칙 검사(PasswordPolicy)와 해시는 application이 한다.
 * 탈퇴·비활성화 사용자도 조회·재활성화해야 하므로 @SQLRestriction을 쓰지 않는다.
 */
@Getter
@Entity
@Table(name = "p_users", uniqueConstraints = @UniqueConstraint(name = User.UK_USERNAME, columnNames = "username"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /** 탈퇴 포함 username 유일 제약 이름. 저장 시 위반을 DUPLICATE_USERNAME으로 바꿀 때 쓴다 */
    public static final String UK_USERNAME = "uk_p_users_username";
    /** 활성 사용자 사이 slack_id 부분 UNIQUE 인덱스 이름 (PostgreSQL, UserSchemaInitializer가 만든다) */
    public static final String UK_SLACK_ID_ACTIVE = "uk_p_users_slack_id_active";

    @Id
    private UUID id;

    @Column(nullable = false, length = 10)
    private String username;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "slack_id", nullable = false, length = 80)
    private String slackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "is_delivery_manager", nullable = false)
    private boolean deliveryManager;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 20)
    private OrganizationType organizationType;

    @Column(name = "organization_name", nullable = false, length = 150)
    private String organizationName;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    private User(UUID id, Username username, String name, String passwordHash, String slackId,
                 UserRole role, boolean deliveryManager, OrganizationType organizationType, String organizationName) {
        this.id = id;
        this.username = username.value();
        this.name = name;
        this.passwordHash = passwordHash;
        this.slackId = slackId;
        this.role = role;
        this.deliveryManager = deliveryManager;
        this.organizationType = organizationType;
        this.organizationName = organizationName;
        this.passwordChangeRequired = false;
    }

    /** 회원가입: 항상 USER로 즉시 활성화. 본사(HQ)는 고를 수 없다. 작성자는 본인. */
    public static User signup(UUID id, Username username, String name, String passwordHash, String slackId,
                              OrganizationType organizationType, String organizationName) {
        if (organizationType == OrganizationType.HQ) {
            throw new BusinessException(UserErrorCode.INVALID_ORGANIZATION);
        }
        User user = new User(id, username, name, passwordHash, slackId, UserRole.USER, false,
                organizationType, organizationName);
        user.recordCreatedBy(id);
        return user;
    }

    /** MASTER가 만든 계정: 역할·배송담당 여부 지정 가능. 본사(HQ)는 MASTER 역할만, 배송담당자는 허브 소속만. 작성자는 MASTER. */
    public static User createByMaster(UUID id, Username username, String name, String passwordHash, String slackId,
                                      OrganizationType organizationType, String organizationName,
                                      UserRole role, boolean deliveryManager, UUID masterId) {
        if (organizationType == OrganizationType.HQ && role != UserRole.MASTER) {
            throw new BusinessException(UserErrorCode.INVALID_ORGANIZATION);
        }
        checkDeliveryManagerOrganization(deliveryManager, organizationType);
        User user = new User(id, username, name, passwordHash, slackId, role, deliveryManager,
                organizationType, organizationName);
        user.recordCreatedBy(masterId);
        return user;
    }

    /** 기동 시 MASTER가 없을 때 .env 값으로 만드는 첫 관리자. 요청 밖 작업이라 작성자는 시스템 UUID(JPA Auditing). */
    public static User initialMaster(UUID id, Username username, String name, String passwordHash, String slackId) {
        return new User(id, username, name, passwordHash, slackId, UserRole.MASTER, false,
                OrganizationType.HQ, "본사");
    }

    public boolean isActive() {
        return !isDeleted();
    }

    public boolean isMaster() {
        return role == UserRole.MASTER;
    }

    /** 본인 비밀번호 변경. 초기화로 생긴 변경 필요 상태도 해제된다. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordChangeRequired = false;
    }

    /** MASTER의 비밀번호 초기화: 임시 비밀번호로 바꾸고 다음 로그인 후 변경을 강제한다. */
    public void requirePasswordChange(String temporaryPasswordHash) {
        this.passwordHash = temporaryPasswordHash;
        this.passwordChangeRequired = true;
    }

    /** 본인이 수정할 수 있는 유일한 정보 */
    public void updateSlackId(String slackId) {
        this.slackId = slackId;
    }

    /** MASTER의 정보 수정. 소속 유형이 바뀌면 연결된 조직 ID는 다시 연결해야 하므로 지운다. */
    public void updateProfile(String name, String slackId, OrganizationType organizationType, String organizationName) {
        if (organizationType == OrganizationType.HQ && !isMaster()) {
            throw new BusinessException(UserErrorCode.INVALID_ORGANIZATION);
        }
        if (this.organizationType != organizationType) {
            this.organizationId = null;
        }
        this.name = name;
        this.slackId = slackId;
        this.organizationType = organizationType;
        this.organizationName = organizationName;
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }

    /** 배송담당자 지정·해제. 담당자의 소속 허브가 출발지 기준이므로 허브 소속만 지정할 수 있다. 해제는 언제나 된다. */
    public void assignDeliveryManager(boolean deliveryManager) {
        checkDeliveryManagerOrganization(deliveryManager, organizationType);
        this.deliveryManager = deliveryManager;
    }

    private static void checkDeliveryManagerOrganization(boolean deliveryManager, OrganizationType organizationType) {
        if (deliveryManager && organizationType != OrganizationType.HUB) {
            throw new BusinessException(UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
        }
    }

    /** 가입 때 입력한 소속을 실제 업체·허브 ID와 연결한다. 본사(HQ)는 연결할 조직이 없다. */
    public void linkOrganization(OrganizationType organizationType, UUID organizationId) {
        if (!organizationType.isLinkable() || organizationId == null) {
            throw new BusinessException(UserErrorCode.INVALID_ORGANIZATION);
        }
        this.organizationType = organizationType;
        this.organizationId = organizationId;
    }

    /** 탈퇴(본인) 또는 비활성화(MASTER). 논리 삭제. 삭제 시각은 BaseEntity가 현재 시각으로 기록한다. */
    public void withdraw(UUID actorId) {
        if (isDeleted()) {
            throw new BusinessException(UserErrorCode.USER_ALREADY_DELETED);
        }
        softDelete(actorId);
    }

    /** MASTER의 재활성화. BaseEntity.restore()와 달리 삭제되지 않은 사용자면 거절한다. */
    @Override
    public void restore() {
        if (!isDeleted()) {
            throw new BusinessException(UserErrorCode.USER_NOT_DELETED);
        }
        super.restore();
    }
}
