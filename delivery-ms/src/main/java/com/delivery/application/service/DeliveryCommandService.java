package com.delivery.application.service;

import com.common.auth.AuthUser;
import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.application.client.HubRouteClient;
import com.delivery.application.client.UserClient;
import com.delivery.application.dto.command.DeliveryCreateCommand;
import com.delivery.application.dto.command.DeliveryRouteActualMeasurementUpdateCommand;
import com.delivery.application.dto.result.DeliveryCreateResult;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.application.dto.result.DeliveryRouteResult;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.entity.DeliveryRoute;
import com.delivery.domain.repository.DeliveryRepository;
import com.delivery.domain.vo.DeliveryStatus;
import com.delivery.domain.vo.RouteEstimate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;
    private final UserClient userClient;
    private final HubRouteClient hubRouteClient;


    public DeliveryCreateResult createDelivery(DeliveryCreateCommand command) {
        RouteEstimate hubRouteEstimate = command.departureHubId().equals(command.arrivalHubId())
                ? null
                : hubRouteClient.getRouteEstimate(command.departureHubId(), command.arrivalHubId());

        Delivery delivery = Delivery.create(
                command.orderId(),
                command.departureHubId(),
                command.arrivalHubId(),
                command.deliveryAddress(),
                command.recipientName(),
                command.recipientUserId(),
                hubRouteEstimate
        );
        return DeliveryCreateResult.from(deliveryRepository.save(delivery));
    }

    public DeliveryResult assignDeliveryManager(UUID deliveryId, UUID deliveryManagerId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if(!userClient.isDeliveryManager(deliveryManagerId)) {
            throw new BusinessException(DeliveryErrorCode.INVALID_DELIVERY_MANAGER);
        }

        delivery.assignDeliveryManager(deliveryManagerId);
        return DeliveryResult.from(delivery);
    }

    // 배송 취소로 인한 상태 변경은 여기서 다루지 않음
    public DeliveryResult changeDeliveryStatus(UUID deliveryId, DeliveryStatus nextStatus, UUID userId, boolean isMaster) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        // MASTER도, 해당 배송의 배송 담당자도 아닌 경우
        if (!isMaster && !userId.equals(delivery.getDeliveryManagerId())) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED, "배송 상태는 MASTER 또는 해당 배송의 담당자만 변경할 수 있습니다.");
        }

        delivery.changeStatus(nextStatus);
        return DeliveryResult.from(delivery);
    }

    public void cancelDelivery(UUID deliveryId) {
        deliveryRepository.findById(deliveryId)
                .ifPresent(Delivery::cancel);
    }

    public DeliveryRouteResult updateActualMeasurement(UUID routeId, DeliveryRouteActualMeasurementUpdateCommand command,
                                                       UUID userId, boolean isMaster) {
        Delivery delivery = deliveryRepository.findByRouteId(routeId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_ROUTE_NOT_FOUND));

        DeliveryRoute route = delivery.routeById(routeId);
        if(!isMaster && !userId.equals(route.getDeliveryManagerId())) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ACCESS_DENIED, "구간 실측 정보는 MASTER 또는 해당 구간의 배송담당자만 수정할 수 있습니다.");
        }

        delivery.updateRouteActualMeasurement(routeId, command.actualDistanceKm(), command.actualDurationMin());
        return DeliveryRouteResult.from(route);
    }
}
