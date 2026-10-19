package com.delivery.domain.entity;

import com.common.entity.BaseEntity;
import com.delivery.domain.vo.DeliveryRouteStatus;
import com.delivery.domain.vo.DeliveryRouteType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "p_delivery_routes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_p_delivery_routes_delivery_id_route_sequence",
                columnNames = {"delivery_id", "route_sequence"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryRoute extends BaseEntity {

    // 허브 -> 업체 이동 예상 거리/시간 고정값
    static final BigDecimal DEFAULT_HUB_TO_COMPANY_DISTANCE_KM = new BigDecimal("10.00");
    static final int DEFAULT_HUB_TO_COMPANY_DURATION_MIN = 30;

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Column(name = "route_sequence", nullable = false)
    private int routeSequence;

    @Column(name = "route_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryRouteType routeType;

    @Column(name = "departure_hub_id", nullable = false)
    private UUID departureHubId;

    @Column(name = "arrival_hub_id")
    private UUID arrivalHubId;

    @Column(name = "estimated_distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedDistanceKm;

    @Column(name = "actual_distance_km", precision = 10, scale = 2)
    private BigDecimal actualDistanceKm;

    @Column(name = "estimated_duration_min", nullable = false)
    private int estimatedDurationMin;

    @Column(name = "actual_duration_min")
    private Integer actualDurationMin;

    @Column(name = "route_status", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    private DeliveryRouteStatus routeStatus;

    @Column(name = "delivery_manager_id")
    private UUID deliveryManagerId;


    @Builder(access = AccessLevel.PACKAGE)
    DeliveryRoute(Delivery delivery, int routeSequence, DeliveryRouteType routeType,
                          UUID departureHubId, UUID arrivalHubId,
                          BigDecimal estimatedDistanceKm, Integer estimatedDurationMin) {
        this.delivery = delivery;
        this.routeSequence = routeSequence;
        this.routeType = routeType;
        this.departureHubId = departureHubId;
        this.arrivalHubId = arrivalHubId;
        this.estimatedDistanceKm = estimatedDistanceKm;
        this.estimatedDurationMin = estimatedDurationMin;
        this.routeStatus = DeliveryRouteStatus.WAITING;
    }


    void start() {
        if (routeStatus != DeliveryRouteStatus.WAITING) {
            throw new IllegalArgumentException("WAITING 상태의 구간만 이동을 시작할 수 있습니다.");
        }
        this.routeStatus = DeliveryRouteStatus.MOVING;
    }

    void arrive() {
        if (routeStatus != DeliveryRouteStatus.MOVING) {
            throw new IllegalArgumentException("MOVING 상태의 구간만 도착 처리할 수 있습니다.");
        }
        this.routeStatus = DeliveryRouteStatus.ARRIVED;
    }

    void cancel() {
        if (routeStatus != DeliveryRouteStatus.WAITING) {
            throw new IllegalArgumentException("WAITING 상태의 구간만 취소할 수 있습니다.");
        }
        this.routeStatus = DeliveryRouteStatus.CANCELED;
    }

    void updateActualMeasurement(BigDecimal actualDistanceKm, Integer actualDurationMin) {
        this.actualDistanceKm = actualDistanceKm;
        this.actualDurationMin = actualDurationMin;
    }

}
