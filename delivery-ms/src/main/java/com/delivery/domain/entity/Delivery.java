package com.delivery.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.domain.vo.DeliveryRouteType;
import com.delivery.domain.vo.DeliveryStatus;
import com.delivery.domain.vo.RouteEstimate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "recipient_name", length = 10)
    private String recipientName;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "delivery_manager_id")
    private UUID deliveryManagerId;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryRoute> routes = new ArrayList<>();


    @Builder
    private Delivery(UUID orderId, DeliveryStatus status, UUID departureHubId, UUID arrivalHubId, String deliveryAddress, String recipientName, UUID recipientUserId) {
        this.orderId = orderId;
        this.status = status;
        this.departureHubId = departureHubId;
        this.arrivalHubId = arrivalHubId;
        this.deliveryAddress = deliveryAddress;
        this.recipientName = recipientName;
        this.recipientUserId = recipientUserId;
    }


    public static Delivery create(UUID orderId,
                                  UUID departureHubId,
                                  UUID arrivalHubId,
                                  String deliveryAddress,
                                  String recipientName,
                                  UUID recipientUserId,
                                  RouteEstimate hubRouteEstimate) {
        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .status(determineInitialStatus(departureHubId, arrivalHubId))
                .departureHubId(departureHubId)
                .arrivalHubId(arrivalHubId)
                .deliveryAddress(deliveryAddress)
                .recipientName(recipientName)
                .recipientUserId(recipientUserId)
                .build();

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
                .build());

        return delivery;
    }

    private static DeliveryStatus determineInitialStatus(UUID departureHubId, UUID arrivalHubId) {
        return departureHubId.equals(arrivalHubId)
                ? DeliveryStatus.HUB_ARRIVED
                : DeliveryStatus.HUB_WAITING;
    }


    public void assignDeliveryManager(UUID deliveryManagerId) {
        this.deliveryManagerId = deliveryManagerId;
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
    }

    public void cancel() {
        if(status == DeliveryStatus.CANCELED) return;
        if(!isBeforeDispatch()) throw new BusinessException(DeliveryErrorCode.DELIVERY_NOT_CANCELABLE);
        this.status = DeliveryStatus.CANCELED;
    }

    // 아직 아무 이동도 시작하지 않았는가
    private boolean isBeforeDispatch() {
        return status == DeliveryStatus.HUB_WAITING
                || (status == DeliveryStatus.HUB_ARRIVED && departureHubId.equals(arrivalHubId));
    }
}


