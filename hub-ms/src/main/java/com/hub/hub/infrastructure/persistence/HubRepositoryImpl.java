package com.hub.hub.infrastructure.persistence;

import com.hub.hub.domain.entity.Hub;
import com.hub.hub.domain.repository.HubRepository;
import com.hub.hub.domain.vo.HubSort;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class HubRepositoryImpl implements HubRepository {
    // 함수 정렬에는 사용자 입력 대신 서버에 고정한 한국어 정렬 표현식만 사용한다.
    private static final String KOREAN_NAME_SORT = "collate(h.name as `ko-x-icu`)";

    private final HubJpaRepository hubJpaRepository;

    public HubRepositoryImpl(HubJpaRepository hubJpaRepository) {
        this.hubJpaRepository = hubJpaRepository;
    }

    @Override
    public List<Hub> findAll(HubSort sort) {
        return hubJpaRepository.findAllByDeletedAtIsNull(toSort(sort));
    }

    @Override
    public Optional<Hub> findById(UUID id) {
        return hubJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Hub> findByNameContaining(String name, HubSort sort) {
        return hubJpaRepository
                .findByNameContainingAndDeletedAtIsNull(name, toSort(sort) );
    }


    //Hub 정렬 조건을 Jpa 조회용으로 변환과 동시에 비교값이 동일한 경우 Id 오름차순.
    private Sort toSort(HubSort sort) {
        Sort primarySort = switch (sort){
            case CREATED_AT_DESC ->
                    Sort.by(Sort.Direction.DESC, "createdAt");
            case CREATED_AT_ASC ->
                    Sort.by(Sort.Direction.ASC, "createdAt");
            case NAME_ASC ->
                    JpaSort.unsafe(Sort.Direction.ASC, KOREAN_NAME_SORT);
            case NAME_DESC ->
                    JpaSort.unsafe(Sort.Direction.DESC, KOREAN_NAME_SORT);
        };
        return primarySort.and(Sort.by(Sort.Direction.ASC, "id"));
    }

}
