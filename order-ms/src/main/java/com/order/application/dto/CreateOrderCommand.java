package com.order.application.dto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public record CreateOrderCommand(
        UUID requesterCompanyId,  // 받는 업체 (요청 body)
        UUID supplierCompanyId,   // 보내는 업체 (요청 body)
        UUID productId,           // 상품 (요청 body)
        int quantity,             // 수량 (요청 body)
        BigDecimal unitPrice,     // 단가 (요청 body, 업체 서비스 미호출 결정)
        String requestNote,       // 요청사항 (요청 body)
        UUID orderedBy,           // 주문자 (헤더 X-User-Id)
        String idempotencyKey     // 멱등성 키 (헤더 Idempotency-Key)
) {
    /**
     * 요청 내용의 지문, 같은 내용이면 항상 같은 값이 나온다
     * idempotencyKey는 내용이 아니므로 제외
     */

    public String requestHash() {
        String raw = String.join("|",
                String.valueOf(requesterCompanyId),
                String.valueOf(supplierCompanyId),
                String.valueOf(productId),
                String.valueOf(quantity),
                unitPrice.stripTrailingZeros().toPlainString(),
                String.valueOf(requestNote),
                String.valueOf(orderedBy));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

}
