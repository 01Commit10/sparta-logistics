package com.delivery.domain.entity;

import com.common.entity.BaseEntity;
import com.delivery.domain.vo.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_delivery",
        uniqueConstraints = @UniqueConstraint(name = "uk_p_delivery_order_id", columnNames = "{order_id}"))
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
                                  UUID recipientUserId) {
        return Delivery.builder()
                .orderId(orderId)
                .status(determineInitialStatus(departureHubId, arrivalHubId))
                .departureHubId(departureHubId)
                .arrivalHubId(arrivalHubId)
                .deliveryAddress(deliveryAddress)
                .recipientName(recipientName)
                .recipientUserId(recipientUserId)
                .build();
    }

    private static DeliveryStatus determineInitialStatus(UUID departureHubId, UUID arrivalHubId) {
        return departureHubId.equals(arrivalHubId)
                ? DeliveryStatus.HUB_ARRIVED
                : DeliveryStatus.HUB_WAITING;
    }

    public void assignDeliveryManager(UUID deliveryManagerId) {
        this.deliveryManagerId = deliveryManagerId;
    }
}


