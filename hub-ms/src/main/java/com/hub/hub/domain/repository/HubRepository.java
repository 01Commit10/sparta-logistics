package com.hub.hub.domain.repository;

import com.hub.hub.domain.entity.Hub;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRepository {
    //전체 허브 조회
    List<Hub> findAll();
    //단일 허브 조회
    Optional<Hub> findById(UUID Id);
}
