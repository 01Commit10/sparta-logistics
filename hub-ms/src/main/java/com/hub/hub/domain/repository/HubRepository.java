package com.hub.hub.domain.repository;

import com.hub.hub.domain.entity.Hub;
import com.hub.hub.domain.vo.HubSort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRepository {
    //전체 허브 조회 + 정렬 조건
    List<Hub> findAll(HubSort hubSort);
    //단일 허브 조회
    Optional<Hub> findById(UUID Id);
    //허브 이름 검색 + 정렬 조건
    List<Hub> findByNameContaining(String name, HubSort hubSort);
}
