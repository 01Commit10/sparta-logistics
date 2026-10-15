package com.hub.hub.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.hub.domain.entity.Hub;
import com.hub.hub.domain.repository.HubRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class HubService {
    private final HubRepository hubRepository;

    public HubService(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

    public List<Hub> getHubs() {
        return hubRepository.findAll();
    }

    public Hub getHub(UUID hubId) {
        return hubRepository.findById(hubId).orElseThrow(()->
                        new BusinessException(
                                CommonErrorCode.RESOURCE_NOT_FOUND,
                                "해당하는 허브를 찾을 수 없습니다."
                        )
                );
    }
}
