package com.order.infrastructure.client.hub;

import java.util.UUID;

public record RestoreFeignRequest(UUID orderId) {
}
