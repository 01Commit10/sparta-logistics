package com.hub.hubroute.infrastructure.persistence;

import com.hub.hubroute.domain.entity.HubRoute;
import com.hub.hubroute.domain.repository.HubRouteRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class HubRouteRepositoryImpl implements HubRouteRepository {
    private final HubRouteJpaRepository hubRouteJpaRepository;

    public HubRouteRepositoryImpl(HubRouteJpaRepository hubRouteJpaRepository) {
        this.hubRouteJpaRepository = hubRouteJpaRepository;
    }

    @Override
    public List<HubRoute> findAll(){
        return hubRouteJpaRepository.findAllByDeletedAtIsNullOrderByCreatedAtDescIdAsc();
    }

    @Override
    public Optional<HubRoute> findById(UUID routeId){
        return hubRouteJpaRepository.findByIdAndDeletedAtIsNull(routeId);
    }

    @Override
    public Optional<HubRoute> findByFromHubIdAndToHubId(UUID fromHubId, UUID toHubId){
        return hubRouteJpaRepository.findByFromHubIdAndToHubIdAndDeletedAtIsNull(fromHubId, toHubId);
    }
}
