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
}
