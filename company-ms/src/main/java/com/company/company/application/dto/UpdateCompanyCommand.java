package com.company.company.application.dto;

import java.util.UUID;

/**
 * PATCH 의미: name·address·hubId·managerUserId 중 null인 값은 바꾸지 않는다.
 * actorId·actorIsMaster: 요청한 사용자. 업체 관리자 본인인지는 업체 데이터를 봐야 알 수 있어 유스케이스에서 판단한다.
 */
public record UpdateCompanyCommand(
        UUID companyId,
        String name,
        String address,
        UUID hubId,
        UUID managerUserId,
        UUID actorId,
        boolean actorIsMaster
) {}
