package com.hub.hub.infrastructure.persistence;

import com.hub.hub.domain.entity.Hub;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubJpaRepository extends JpaRepository<Hub, UUID> {

    // 삭제되지 않은 허브만 정렬 기준으로 조회
    @Query("select h from Hub h where h.deletedAt is null")
    List<Hub> findAllByDeletedAtIsNull(Sort sort);

    // 삭제되지 않은 허브만 이름 부분 검색 (%, _와 같은 특수기호도 일반 글자 취급하여 검색)
    @Query("""
            select h from Hub h
            where h.deletedAt is null
              and h.name like %?#{escape([0])}% escape ?#{escapeCharacter()}
            """)
    List<Hub> findByNameContainingAndDeletedAtIsNull(String name, Sort sort);

    //  삭제되지 않은 허브만 ID로 조회
    Optional<Hub> findByIdAndDeletedAtIsNull(UUID id);
}
