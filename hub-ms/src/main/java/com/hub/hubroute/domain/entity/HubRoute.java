package com.hub.hubroute.domain.entity;

import com.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_hub_routes",
        schema = "hub",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_hub_routes_from_to",
                        columnNames = {"from_hub_id", "to_hub_id"}
                )
        }
)
public class HubRoute extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "from_hub_id", nullable = false)
    private UUID fromHubId;

    @Column(name = "to_hub_id", nullable = false)
    private UUID toHubId;

    @Column(
            name = "distance_km",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal distanceKm;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;
}
