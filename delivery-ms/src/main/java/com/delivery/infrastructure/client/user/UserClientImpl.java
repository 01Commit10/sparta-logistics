package com.delivery.infrastructure.client.user;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.delivery.application.client.UserClient;
import com.delivery.infrastructure.client.user.dto.UserAuthInfoResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserClientImpl implements UserClient {

    private final UserFeignClient userFeignClient;


    @Override
    public boolean isDeliveryManager(UUID userId) {
        try {
            return userFeignClient.getAuthInfo(userId).isDeliveryManager();
        } catch (FeignException.NotFound e) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_REQUESTER_NOT_FOUND);
        }
    }
}
