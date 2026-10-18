package com.delivery.presentation.dto.request;

import com.delivery.domain.vo.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record DeliveryStatusChangeRequest(
        @NotNull DeliveryStatus status
) {
}
