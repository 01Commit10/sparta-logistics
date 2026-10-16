package com.user.domain.repository;

import com.user.domain.entity.User;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 저장소 Port. 구현은 infrastructure.persistence (DIP).
 * 목록 검색(USR-11)은 U13에서 추가한다.
 */
public interface UserRepository {

    User save(User user);

    /** 삭제된 사용자 포함 (MASTER 상세 조회·재활성화) */
    Optional<User> findById(UUID id);

    Optional<User> findActiveById(UUID id);

    /** 로그인: 활성 사용자만 */
    Optional<User> findActiveByUsername(String username);

    /** 탈퇴한 ID도 재사용할 수 없으므로 삭제된 사용자까지 확인한다 */
    boolean existsByUsername(String username);

    /** Slack ID는 활성 사용자 사이에서만 유일하다 */
    boolean existsActiveBySlackId(String slackId);

    /** Slack ID 변경: 본인을 뺀 다른 활성 사용자가 쓰는지 본다 (지금 값 그대로 저장해도 통과) */
    boolean existsActiveBySlackIdAndIdNot(String slackId, UUID id);

    /** 활성 MASTER 수. 행을 잠그고 센다 (마지막 MASTER 보호의 경쟁 조건 방지). 트랜잭션 안에서 호출한다. */
    long countActiveMastersForUpdate();

    boolean existsActiveMaster();
}
