package com.delivery.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.domain.vo.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "p_delivery",
        uniqueConstraints = @UniqueConstraint(name = "uk_p_delivery_order_id", columnNames = {"order_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery extends BaseEntity {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    @Column(name = "departure_hub_id", nullable = false)
    private UUID departureHubId;

    @Column(name = "arrival_hub_id", nullable = false)
    private UUID arrivalHubId;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "supplier_company_id", nullable = false)
    private UUID supplierCompanyId;

    @Column(name = "supplier_company_manager_id", nullable = false)
    private UUID supplierCompanyManagerId;

    @Column(name = "requester_company_id", nullable = false)
    private UUID requesterCompanyId;

    @Column(name = "requester_company_manager_id", nullable = false)
    private UUID requesterCompanyManagerId;

    @Column(name = "delivery_manager_id")
    private UUID deliveryManagerId;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryRoute> routes = new ArrayList<>();


    @Builder
    private Delivery(UUID orderId,
                     DeliveryStatus status,
                     UUID departureHubId,
                     UUID arrivalHubId,
                     String deliveryAddress,
                     String recipientName,
                     UUID recipientUserId,
                     UUID supplierCompanyId,
                     UUID supplierCompanyManagerId,
                     UUID requesterCompanyId,
                     UUID requesterCompanyManagerId) {
        this.orderId = orderId;
        this.status = status;
        this.departureHubId = departureHubId;
        this.arrivalHubId = arrivalHubId;
        this.deliveryAddress = deliveryAddress;
        this.recipientName = recipientName;
        this.recipientUserId = recipientUserId;
        this.supplierCompanyId = supplierCompanyId;
        this.supplierCompanyManagerId = supplierCompanyManagerId;
        this.requesterCompanyId = requesterCompanyId;
        this.requesterCompanyManagerId = requesterCompanyManagerId;
    }


    public static Delivery create(UUID orderId,
                                  UUID departureHubId,
                                  UUID arrivalHubId,
                                  String deliveryAddress,
                                  String recipientName,
                                  UUID recipientUserId,
                                  RouteEstimate hubRouteEstimate,
                                  DeliveryManagers deliveryManagers,
                                  DeliveryCompanies companies) {
        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .status(determineInitialStatus(departureHubId, arrivalHubId))
                .departureHubId(departureHubId)
                .arrivalHubId(arrivalHubId)
                .deliveryAddress(deliveryAddress)
                .recipientName(recipientName)
                .recipientUserId(recipientUserId)
                .supplierCompanyId(companies.supplierCompanyId())
                .supplierCompanyManagerId(companies.supplierCompanyManagerId())
                .requesterCompanyId(companies.requesterCompanyId())
                .requesterCompanyManagerId(companies.requesterCompanyManagerId())
                .build();

        delivery.deliveryManagerId = departureHubId.equals(arrivalHubId)
                ? deliveryManagers.arrivalManagerId()
                : deliveryManagers.departureManagerId();

        int sequence = 1;
        if(!departureHubId.equals(arrivalHubId)) {
            delivery.routes.add(DeliveryRoute.builder()
                    .delivery(delivery)
                    .routeSequence(sequence++)
                    .routeType(DeliveryRouteType.HUB_TO_HUB)
                    .departureHubId(departureHubId)
                    .arrivalHubId(arrivalHubId)
                    .estimatedDistanceKm(hubRouteEstimate.distanceKm())
                    .estimatedDurationMin(hubRouteEstimate.durationMin())
                    .deliveryManagerId(deliveryManagers.departureManagerId())
                    .build());
        }
        delivery.routes.add(DeliveryRoute.builder()
                .delivery(delivery)
                .routeSequence(sequence)
                .routeType(DeliveryRouteType.HUB_TO_COMPANY)
                .departureHubId(arrivalHubId)
                .arrivalHubId(null)
                .estimatedDistanceKm(DeliveryRoute.DEFAULT_HUB_TO_COMPANY_DISTANCE_KM)
                .estimatedDurationMin(DeliveryRoute.DEFAULT_HUB_TO_COMPANY_DURATION_MIN)
                .deliveryManagerId(deliveryManagers.arrivalManagerId())
                .build());

        return delivery;
    }

    private static DeliveryStatus determineInitialStatus(UUID departureHubId, UUID arrivalHubId) {
        return departureHubId.equals(arrivalHubId)
                ? DeliveryStatus.HUB_ARRIVED
                : DeliveryStatus.HUB_WAITING;
    }

    public boolean isRelatedTo(UUID userId) {
        return userId.equals(recipientUserId)
                || userId.equals(requesterCompanyManagerId)
                || userId.equals(supplierCompanyManagerId)
                || isManagedBy(userId);
    }

    public boolean isManagedBy(UUID userId) {
        return routes.stream().anyMatch(route -> userId.equals(route.getDeliveryManagerId()));
    }


    public void assignDeliveryManager(DeliveryRouteType routeType, UUID deliveryManagerId) {
        DeliveryRoute route = findRouteByType(routeType)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_ROUTE_TYPE_NOT_FOUND));
        route.assignDeliveryManager(deliveryManagerId);

        // 현재 배송 중인 구간일 경우 배송의 배송담당자도 함께 변경
        if(route.getRouteType() == currentRouteType()) {
            this.deliveryManagerId = deliveryManagerId;
        }
    }

    public void changeStatus(DeliveryStatus nextStatus) {
        if(nextStatus == null) {
            throw new IllegalArgumentException("changeStatus의 next는 null일 수 없습니다."); // 서버 내부 에러
        }
        if(status == nextStatus) return;
        if(!status.canChangeTo(nextStatus)) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_INVALID_STATUS_TRANSITION);
        }
        this.status = nextStatus;
        syncRouteStatus(nextStatus);
    }

    public void cancel() {
        if(status == DeliveryStatus.CANCELED) return;
        if(!isBeforeDispatch()) throw new BusinessException(DeliveryErrorCode.DELIVERY_NOT_CANCELABLE);
        this.status = DeliveryStatus.CANCELED;
        routes.forEach(DeliveryRoute::cancel);
    }

    public void updateRouteActualMeasurement(UUID routeId, BigDecimal actualDistanceKm, int actualDurationMin) {
        DeliveryRoute route = routeById(routeId);
        route.updateActualMeasurement(actualDistanceKm, actualDurationMin);
    }


    // 아직 아무 이동도 시작하지 않았는가
    private boolean isBeforeDispatch() {
        return status == DeliveryStatus.HUB_WAITING
                || (status == DeliveryStatus.HUB_ARRIVED && departureHubId.equals(arrivalHubId));
    }

    private void syncRouteStatus(DeliveryStatus nextStatus) {
        switch (nextStatus) {
            case HUB_MOVING -> routeByType(DeliveryRouteType.HUB_TO_HUB).start();
            case HUB_ARRIVED -> { // 담당자 교대
                routeByType(DeliveryRouteType.HUB_TO_HUB).arrive();
                this.deliveryManagerId = routeByType(DeliveryRouteType.HUB_TO_COMPANY).getDeliveryManagerId();
            }
            case DELIVERING -> routeByType(DeliveryRouteType.HUB_TO_COMPANY).start();
            case DELIVERED -> routeByType(DeliveryRouteType.HUB_TO_COMPANY).arrive();
            default -> {}
        }
    }

    private Optional<DeliveryRoute> findRouteByType(DeliveryRouteType type) {
        return routes.stream()
                .filter(route -> route.getRouteType() == type)
                .findFirst();
    }

    private DeliveryRoute routeByType(DeliveryRouteType type) {
        return findRouteByType(type)
                .orElseThrow(() -> new IllegalStateException("배송에 해당 유형의 구간이 없습니다"));
    }

    public DeliveryRoute routeById(UUID routeId) {
        return routes.stream()
                .filter(route -> route.getId().equals(routeId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("배송에 해당 id의 구간이 없습니다"));
    }

    private DeliveryRouteType currentRouteType() {
        return switch (status) {
            case HUB_WAITING, HUB_MOVING -> DeliveryRouteType.HUB_TO_HUB;
            case HUB_ARRIVED, DELIVERING -> DeliveryRouteType.HUB_TO_COMPANY;
            case DELIVERED, CANCELED -> null; // DELIVERED의 경우 논의 필요
        };
    }
}


