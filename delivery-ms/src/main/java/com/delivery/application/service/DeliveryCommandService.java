package com.delivery.application.service;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.application.client.CompanyClient;
import com.delivery.application.client.HubRouteClient;
import com.delivery.application.client.UserClient;
import com.delivery.application.dto.command.DeliveryCreateCommand;
import com.delivery.application.dto.command.DeliveryManagerAssignCommand;
import com.delivery.application.dto.command.DeliveryRouteActualMeasurementUpdateCommand;
import com.delivery.application.dto.result.DeliveryCreateResult;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.application.dto.result.DeliveryRouteResult;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.entity.DeliveryRoute;
import com.delivery.domain.repository.DeliveryRepository;
import com.delivery.domain.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;
    private final UserClient userClient;
    private final CompanyClient companyClient;
    private final HubRouteClient hubRouteClient;


    // 원격 조회(업체·경로·담당자·이름)는 트랜잭션 밖에서 해서 기다리는 동안 DB 커넥션을 잡지 않는다 (P02).
    // 저장은 deliveryRepository.save의 짧은 트랜잭션 하나로 끝난다 (배송·구간 함께 저장)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DeliveryCreateResult createDelivery(DeliveryCreateCommand command) {
        UUID departureHubId = command.departureHubId();

        CompanyInfo supplier = companyClient.findCompany(command.supplierCompanyId())
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_INVALID_SUPPLIER_COMPANY));
        CompanyInfo requester = companyClient.findCompany(command.requesterCompanyId())
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_INVALID_REQUESTER_COMPANY));

        UUID arrivalHubId = requester.hubId();

        RouteEstimate hubRouteEstimate = departureHubId.equals(arrivalHubId)
                ? null
                : hubRouteClient.getRouteEstimate(departureHubId, arrivalHubId);

        DeliveryManagers deliveryManagers = userClient.pickDeliveryManagers(departureHubId, arrivalHubId);
        String recipientName = userClient.getUserName(command.recipientUserId());
        DeliveryCompanies companies = new DeliveryCompanies(
                command.supplierCompanyId(), supplier.managerUserId(),
                command.requesterCompanyId(), requester.managerUserId()
        );

        Delivery delivery = Delivery.create(
                command.orderId(),
                departureHubId,
                arrivalHubId,
                requester.address(),
                recipientName,
                command.recipientUserId(),
                hubRouteEstimate,
                deliveryManagers,
                companies
        );
        return DeliveryCreateResult.from(deliveryRepository.save(delivery));
    }

    public DeliveryResult assignDeliveryManager(UUID deliveryId, DeliveryManagerAssignCommand command) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if(!userClient.isDeliveryManager(command.deliveryManagerId())) {
            throw new BusinessException(DeliveryErrorCode.INVALID_DELIVERY_MANAGER);
        }

        delivery.assignDeliveryManager(command.routeType(), command.deliveryManagerId());
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
