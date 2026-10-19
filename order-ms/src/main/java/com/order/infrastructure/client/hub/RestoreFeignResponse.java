package com.order.infrastructure.client.hub;

import java.util.UUID;

public record RestoreFeignResponse(UUID operationId, int remainingQuantity) {
}
