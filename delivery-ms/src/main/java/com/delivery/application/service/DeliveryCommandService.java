package com.delivery.application.service;

import com.common.auth.AuthUser;
import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.application.client.UserClient;
import com.delivery.application.dto.command.DeliveryCreateCommand;
import com.delivery.application.dto.result.DeliveryCreateResult;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import com.delivery.domain.vo.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;
    private final UserClient userClient;


    public DeliveryCreateResult createDelivery(DeliveryCreateCommand command) {
        Delivery delivery = Delivery.create(
                command.orderId(),
                command.departureHubId(),
                command.arrivalHubId(),
                command.deliveryAddress(),
                command.recipientName(),
                command.recipientUserId()
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
}
