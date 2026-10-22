package com.hub.hubroute.infrastructure.persistence;

import com.hub.hubroute.domain.entity.HubRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRouteJpaRepository extends JpaRepository<HubRoute, UUID> {
    // 삭제되지 않은 경로를 생성일 내림차순, ID 오름차순으로 조회
    List<HubRoute> findAllByDeletedAtIsNullOrderByCreatedAtDescIdAsc();

    // 삭제되지 않은 경로를 Id로 조회
    Optional<HubRoute> findByIdAndDeletedAtIsNull(UUID routeId);

    //출발, 도착 HubId 쌍이 일치하는 삭제되지 않은 경로 조회
    Optional<HubRoute> findByFromHubIdAndToHubIdAndDeletedAtIsNull(
            UUID fromHubId,
            UUID toHubId
    );
}
