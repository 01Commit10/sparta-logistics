package com.hub.hubroute.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.hubroute.application.dto.HubRouteResult;
import com.hub.hubroute.domain.repository.HubRouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class HubRouteService {

    private final HubRouteRepository hubRouteRepository;

    public HubRouteService(HubRouteRepository hubRouteRepository) {
        this.hubRouteRepository = hubRouteRepository;
    }

    public List<HubRouteResult> getHubRoutes(){
        return hubRouteRepository.findAll()
                .stream()
                .map(HubRouteResult::fromEntity)
                .toList();
    }

    public HubRouteResult getHubRoute(UUID routeId){
        return hubRouteRepository.findById(routeId)
                .map(HubRouteResult::fromEntity)
                .orElseThrow(()->new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "해당하는 Hub 경로를 찾을 수 없습니다."
                ));
    }

    public HubRouteResult getHubRoute(UUID fromHubId, UUID toHubId){
        return hubRouteRepository.findByFromHubIdAndToHubId(fromHubId,toHubId)
                .map(HubRouteResult::fromEntity)
                .orElseThrow(()-> new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "해당하는 Hub 경로를 찾을 수 없습니다."
                ));
    }

}
