package com.user.infrastructure.persistence;

import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * USR-23 배정 후보 실제 쿼리 (H2 PostgreSQL 모드).
 */
@DataJpaTest
@Import(UserRepositoryImpl.class)
class UserRepositoryDeliveryManagerTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SEOUL = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BUSAN = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager em;

    private User hubUser(String username, UUID hubId, boolean deliveryManager) {
        User user = User.createByMaster(UUID.randomUUID(), Username.of(username), "김배송", "hashed",
                "U-" + username, OrganizationType.HUB, "허브", UserRole.USER, deliveryManager, MASTER_ID);
        if (hubId != null) {
            user.linkOrganization(OrganizationType.HUB, hubId, "허브");
        }
        return userRepository.save(user);
    }

    @Test
    @DisplayName("그 허브에 연결된 활성 배송담당자만 조회한다")
    void candidates() {
        User seoul1 = hubUser("seoul01", SEOUL, true);
        User seoul2 = hubUser("seoul02", SEOUL, true);
        hubUser("busan01", BUSAN, true);
        hubUser("seoul03", SEOUL, false);
        hubUser("seoul04", SEOUL, true).withdraw(MASTER_ID);
        hubUser("seoul05", null, true);
        User company = userRepository.save(User.createByMaster(UUID.randomUUID(), Username.of("comp01"), "김업체",
                "hashed", "U-comp01", OrganizationType.COMPANY, "업체", UserRole.USER, false, MASTER_ID));
        company.linkOrganization(OrganizationType.COMPANY, SEOUL, "업체");
        em.flush();
        em.clear();

        List<User> result = userRepository.findActiveDeliveryManagersByHubId(SEOUL);

        assertThat(result).extracting(User::getId).containsExactlyInAnyOrder(seoul1.getId(), seoul2.getId());
    }

    @Test
    @DisplayName("담당자가 없는 허브는 빈 목록")
    void empty() {
        hubUser("seoul01", SEOUL, true);
        em.flush();
        em.clear();

        assertThat(userRepository.findActiveDeliveryManagersByHubId(COMPANY)).isEmpty();
    }
}
