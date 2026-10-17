package com.company.company.domain.entity;

import com.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_companies", indexes = @Index(name = "idx_companies_hub_id", columnList = "hub_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "hub_id", nullable = false)
    private UUID hubId;

    @Column(name = "manager_user_id", nullable = false)
    private UUID managerUserId;

    private Company(String name, String address, UUID hubId, UUID managerUserId) {
        this.name = name;
        this.address = address;
        this.hubId = hubId;
        this.managerUserId = managerUserId;
    }

    public static Company create(String name, String address, UUID hubId, UUID managerUserId) {
        return new Company(name, address, hubId, managerUserId);
    }

    // null인 값은 바꾸지 않는다. 허브·관리자 존재 확인은 원격 조회가 필요해 서비스에서 먼저 한다
    public void update(String name, String address, UUID hubId, UUID managerUserId) {
        if (name != null) {
            this.name = name;
        }
        if (address != null) {
            this.address = address;
        }
        if (hubId != null) {
            this.hubId = hubId;
        }
        if (managerUserId != null) {
            this.managerUserId = managerUserId;
        }
    }

    // 업체 관리자 본인 여부 (07 서비스 정책 "본인 데이터" 판별: manager_user_id == 로그인 사용자)
    public boolean isManagedBy(UUID userId) {
        return managerUserId.equals(userId);
    }
}
