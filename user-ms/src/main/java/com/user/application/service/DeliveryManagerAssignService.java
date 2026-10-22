package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.DeliveryManagerAssignment;
import com.user.application.dto.DeliveryManagerResult;
import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.service.RandomPicker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * USR-23 배송담당자 무작위 배정 (delivery-ms가 배송 생성 시 호출). user-ms는 고르기만 하고 저장은 delivery-ms가 한다.
 */
@Service
@Transactional(readOnly = true)
public class DeliveryManagerAssignService {

    private final UserRepository userRepository;
    private final RandomPicker randomPicker;

    public DeliveryManagerAssignService(UserRepository userRepository, RandomPicker randomPicker) {
        this.userRepository = userRepository;
        this.randomPicker = randomPicker;
    }

    /**
     * 허브마다 따로 고르므로 출발지와 도착지가 같은 허브면 같은 사람이 양쪽에 배정될 수 있다.
     * 허브 존재 여부는 확인하지 않는다 (없는 허브는 후보가 없으므로 DELIVERY_MANAGER_NOT_FOUND).
     */
    public DeliveryManagerAssignment assignRandomly(UUID departureHubId, UUID arrivalHubId) {
        DeliveryManagerResult departure = pick(departureHubId, "출발지");
        DeliveryManagerResult arrival = pick(arrivalHubId, "도착지");
        return new DeliveryManagerAssignment(departure, arrival);
    }

    private DeliveryManagerResult pick(UUID hubId, String side) {
        List<User> candidates = userRepository.findActiveDeliveryManagersByHubId(hubId);
        if (candidates.isEmpty()) {
            throw new BusinessException(UserErrorCode.DELIVERY_MANAGER_NOT_FOUND,
                    side + " 허브에 배정할 수 있는 배송담당자가 없습니다.");
        }
        return DeliveryManagerResult.from(randomPicker.pick(candidates));
    }
}
