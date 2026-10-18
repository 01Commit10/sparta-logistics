package com.company.company.presentation.dto;

import com.company.company.application.dto.CompanyResult;

import java.util.UUID;

/**
 * 서비스 간 계약 응답 (04 API 명세서 Internal API "업체 검증", 소비자: order-ms). 외부 API 응답과 섞어 쓰지 않는다.
 * hubId: 출발·도착 허브와 재고 차감 허브 (SA 설계보완리포트 D-1), name: 주문 알림 메시지 (07 서비스 정책 8장).
 * 필드를 바꾸면 호출하는 서비스의 Feign DTO도 함께 바꿔야 한다.
 */
public record InternalCompanyResponse(
        UUID id,
        String name,
        UUID hubId
) {

    public static InternalCompanyResponse from(CompanyResult result) {
        return new InternalCompanyResponse(result.id(), result.name(), result.hubId());
    }
}
