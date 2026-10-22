package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.DeliveryManagerAssignment;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import com.user.fake.FakeRandomPicker;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-23 배송담당자 무작위 배정: delivery-ms가 배송 생성 시 출발지·도착지 허브를 주면 허브마다 담당자 1명을 고른다.
 */
class AssignRandomDeliveryManagersTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SEOUL = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BUSAN = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakeRandomPicker randomPicker = new FakeRandomPicker();
    private final DeliveryManagerAssignService service = new DeliveryManagerAssignService(userRepository, randomPicker);

    private User hubUser(String username, UUID hubId, boolean deliveryManager) {
        User user = User.createByMaster(UUID.randomUUID(), Username.of(username), "김" + username, "hashed",
                "U-" + username, OrganizationType.HUB, "허브", UserRole.USER, deliveryManager, MASTER_ID);
        if (hubId != null) {
            user.linkOrganization(OrganizationType.HUB, hubId, "허브");
        }
        return userRepository.save(user);
    }

    private User manager(String username, UUID hubId) {
        return hubUser(username, hubId, true);
    }

    private void assertNotFound(UUID departureHubId, UUID arrivalHubId, String side) {
        assertThatThrownBy(() -> service.assignRandomly(departureHubId, arrivalHubId))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.DELIVERY_MANAGER_NOT_FOUND);
                    assertThat(e.getMessage()).startsWith(side);
                });
    }

    @Test
    @DisplayName("출발지·도착지 허브에서 각각 담당자 1명을 골라 id·이름·Slack ID·허브 ID를 돌려준다")
    void assign() {
        User seoul = manager("seoul01", SEOUL);
        User busan = manager("busan01", BUSAN);

        DeliveryManagerAssignment result = service.assignRandomly(SEOUL, BUSAN);

        assertThat(result.departureManager().id()).isEqualTo(seoul.getId());
        assertThat(result.departureManager().name()).isEqualTo("김seoul01");
        assertThat(result.departureManager().slackId()).isEqualTo("U-seoul01");
        assertThat(result.departureManager().hubId()).isEqualTo(SEOUL);
        assertThat(result.arrivalManager().id()).isEqualTo(busan.getId());
        assertThat(result.arrivalManager().hubId()).isEqualTo(BUSAN);
    }

    @Test
    @DisplayName("후보 중 누구를 고를지는 RandomPicker가 정한다 (출발지 후보 → 도착지 후보 순으로 묻는다)")
    void pickerDecides() {
        User seoul1 = manager("seoul01", SEOUL);
        User seoul2 = manager("seoul02", SEOUL);
        User busan1 = manager("busan01", BUSAN);
        User busan2 = manager("busan02", BUSAN);
        randomPicker.pickIndex(1);

        DeliveryManagerAssignment result = service.assignRandomly(SEOUL, BUSAN);

        assertThat(result.departureManager().id()).isEqualTo(seoul2.getId());
        assertThat(result.arrivalManager().id()).isEqualTo(busan2.getId());
        assertThat(randomPicker.received()).hasSize(2);
        assertThat(ids(randomPicker.received().get(0))).containsExactlyInAnyOrder(seoul1.getId(), seoul2.getId());
        assertThat(ids(randomPicker.received().get(1))).containsExactlyInAnyOrder(busan1.getId(), busan2.getId());
    }

    @Test
    @DisplayName("후보는 그 허브에 소속이 연결된 활성 배송담당자만 (다른 허브·담당자 아님·비활성화·소속 미연결 제외)")
    void candidates() {
        User target = manager("seoul01", SEOUL);
        manager("busan01", BUSAN);
        hubUser("seoul02", SEOUL, false);
        manager("seoul03", SEOUL).withdraw(MASTER_ID);
        manager("seoul04", null);

        service.assignRandomly(SEOUL, SEOUL);

        assertThat(ids(randomPicker.received().get(0))).containsExactly(target.getId());
    }

    @Test
    @DisplayName("출발지와 도착지가 같은 허브면 같은 사람이 양쪽에 배정될 수 있다")
    void sameHub() {
        User only = manager("seoul01", SEOUL);

        DeliveryManagerAssignment result = service.assignRandomly(SEOUL, SEOUL);

        assertThat(result.departureManager().id()).isEqualTo(only.getId());
        assertThat(result.arrivalManager().id()).isEqualTo(only.getId());
    }

    @Test
    @DisplayName("출발지 허브에 후보가 없으면 DELIVERY_MANAGER_NOT_FOUND (메시지에 출발지)")
    void noDepartureManager() {
        manager("busan01", BUSAN);

        assertNotFound(SEOUL, BUSAN, "출발지");
    }

    @Test
    @DisplayName("도착지 허브에 후보가 없으면 DELIVERY_MANAGER_NOT_FOUND (메시지에 도착지)")
    void noArrivalManager() {
        manager("seoul01", SEOUL);

        assertNotFound(SEOUL, BUSAN, "도착지");
    }

    private static List<UUID> ids(List<?> users) {
        return users.stream().map(u -> ((User) u).getId()).toList();
    }
}
