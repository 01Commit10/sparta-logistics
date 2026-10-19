package com.delivery.infrastructure.client.user;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.common.exception.UserErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.delivery.application.client.UserClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserClientImpl implements UserClient {

    private final UserFeignClient userFeignClient;
    private final RemoteCallTemplate remoteCallTemplate;


    @Override
    public boolean isDeliveryManager(UUID userId) {
        try {
            return remoteCallTemplate
                    .execute("user-ms", () -> userFeignClient.getUser(userId))
                    .isDeliveryManager();
        } catch (RemoteBusinessException e) {
            if(e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(DeliveryErrorCode.DELIVERY_USER_NOT_FOUND);
            }
            throw e;
        }
    }
}
