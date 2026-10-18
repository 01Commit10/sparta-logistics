package com.hub.hubroute.domain.repository;

import com.hub.hubroute.domain.entity.HubRoute;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRouteRepository {
    //논리 삭제되지 않은 Hub Route 목록 전체 조회.
    List<HubRoute> findAll();

    //논리 삭제되지 않은 Hub Route Id로 단일 조회.
    Optional<HubRoute> findById(UUID routeId);

    //논리 삭제되지 않은 출발 도착 Hub Id 조합으로 조회.
    Optional<HubRoute> findByFromHubIdAndToHubId(
            UUID fromHubId,
            UUID toHubId
    );
}
