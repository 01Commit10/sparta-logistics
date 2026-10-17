package com.company.company.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyTest {

    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Test
    @DisplayName("생성: 업체명·주소·관리 허브·업체 관리자를 담고, ID는 저장 전까지 비어 있으며 삭제되지 않은 상태")
    void create() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID);

        assertThat(company.getName()).isEqualTo("스파르타상사");
        assertThat(company.getAddress()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(company.getHubId()).isEqualTo(HUB_ID);
        assertThat(company.getManagerUserId()).isEqualTo(MANAGER_ID);
        assertThat(company.getId()).isNull();
        assertThat(company.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("isManagedBy: 업체 관리자 본인만 true")
    void isManagedBy() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID);

        assertThat(company.isManagedBy(MANAGER_ID)).isTrue();
        assertThat(company.isManagedBy(UUID.randomUUID())).isFalse();
        assertThat(company.isManagedBy(null)).isFalse();
    }

    @Test
    @DisplayName("update: 보낸(null 아닌) 값만 바꾼다")
    void update() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID);
        UUID newHub = UUID.randomUUID();

        company.update("스파르타물산", null, newHub, null);

        assertThat(company.getName()).isEqualTo("스파르타물산");
        assertThat(company.getAddress()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(company.getHubId()).isEqualTo(newHub);
        assertThat(company.getManagerUserId()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("삭제: 삭제 시각·삭제자를 기록한다")
    void delete() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID);
        UUID actorId = UUID.randomUUID();

        company.delete(actorId);

        assertThat(company.isDeleted()).isTrue();
        assertThat(company.getDeletedAt()).isNotNull();
        assertThat(company.getDeletedBy()).isEqualTo(actorId);
    }
}
