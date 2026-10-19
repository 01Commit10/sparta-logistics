package com.company.product.infrastructure.client;

import com.common.exception.CommonErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.common.remote.RemoteServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 상품의 재고 삭제를 hub-ms에 요청한다. hub-ms가 멱등이라 실패는 해석하지 않고 그대로 던진다. */
class InventoryClientImplTest {

    static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private static InventoryClientImpl client(InventoryFeignClient feign) {
        return new InventoryClientImpl(feign, new RemoteCallTemplate());
    }

    @Test
    @DisplayName("상품 ID로 재고 삭제를 요청한다")
    void delete() {
        List<UUID> requested = new ArrayList<>();
        InventoryClientImpl client = client(requested::add);

        client.deleteInventory(PRODUCT_ID);

        assertThat(requested).containsExactly(PRODUCT_ID);
    }

    @Test
    @DisplayName("hub-ms 4xx는 그대로 던진다")
    void businessError() {
        InventoryClientImpl client = client(id -> {
            throw new RemoteBusinessException("hub-ms", 400, CommonErrorCode.INVALID_INPUT, "잘못된 요청");
        });

        assertThatThrownBy(() -> client.deleteInventory(PRODUCT_ID))
                .isInstanceOfSatisfying(RemoteBusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    @DisplayName("예상하지 못한 실패는 RemoteServiceException(503)으로 바뀐다")
    void unavailable() {
        InventoryClientImpl client = client(id -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(() -> client.deleteInventory(PRODUCT_ID)).isInstanceOf(RemoteServiceException.class);
    }
}
