package com.company.company.application.dto;

import java.util.UUID;

/**
 * actorId: 요청한 사용자로, 삭제자(deleted_by)로 기록한다. MASTER 여부는 Controller에서 확인한다 (업체 데이터 없이 판단 가능).
 */
public record DeleteCompanyCommand(
        UUID companyId,
        UUID actorId
) {}
