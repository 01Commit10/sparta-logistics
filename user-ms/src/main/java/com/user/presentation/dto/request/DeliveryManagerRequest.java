package com.user.presentation.dto.request;

import com.user.application.dto.AssignDeliveryManagerCommand;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** USR-15 배송담당자 지정(true)·해제(false). 값이 없으면 어느 쪽인지 알 수 없으므로 400 */
public record DeliveryManagerRequest(@NotNull Boolean isDeliveryManager) {

    public AssignDeliveryManagerCommand toCommand(UUID userId) {
        return new AssignDeliveryManagerCommand(userId, isDeliveryManager);
    }
}
