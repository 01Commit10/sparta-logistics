package com.hub.hub.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.hub.application.dto.HubResult;
import com.hub.hub.domain.entity.Hub;
import com.hub.hub.domain.repository.HubRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class HubService {
    private final HubRepository hubRepository;

    public HubService(HubRepository hubRepository) {

        this.hubRepository = hubRepository;
    }

    public List<HubResult> getHubs() {

        return hubRepository.findAll()
                .stream()
                .map(HubResult::fromEntity)
                .toList();
    }

    public HubResult getHub(UUID hubId) {
        return hubRepository.findById(hubId)
                .map(HubResult::fromEntity)
                .orElseThrow(()->new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "해당하는 허브를 찾을 수 없습니다."
                ));
    }
}
