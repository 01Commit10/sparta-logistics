package com.user.application.service;

import com.user.application.dto.UserResult;
import com.user.application.dto.UserSearchQuery;
import com.user.domain.entity.User;
import com.user.domain.repository.PageQuery;
import com.user.domain.repository.UserSearchCondition;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * USR-11 관리자 목록 검색 (U13 테스트 1~4). 조건 필터는 Fake로, 실제 쿼리·정렬은 UserRepositorySearchTest에서 확인한다.
 */
class UserSearchServiceTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UserSearchQuery NO_CONDITION = new UserSearchQuery(null, null, null, null, null, null);
    /** PagePolicy가 정규화한 기본값 (createdAt DESC, id ASC) */
    private static final Pageable DEFAULT_PAGE = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserQueryService service = new UserQueryService(userRepository);

    private User signup(String username, String name, OrganizationType type) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), name, "hashed",
                "U-" + username, type, "센터"));
    }

    private User created(String username, UserRole role, boolean deliveryManager) {
        return userRepository.save(User.createByMaster(UUID.randomUUID(), Username.of(username), "관리생성", "hashed",
                "U-" + username, OrganizationType.HUB, "서울센터", role, deliveryManager, MASTER_ID));
    }

    @Test
    @DisplayName("1. 조건 없음: 활성 사용자만, 조건은 하나도 걸지 않는다")
    void noCondition_activeOnly() {
        User active = signup("user01", "홍길동", OrganizationType.HUB);
        signup("user02", "김철수", OrganizationType.COMPANY).withdraw(MASTER_ID);

        Page<UserResult> result = service.search(NO_CONDITION, DEFAULT_PAGE);

        assertThat(result.getContent()).extracting(UserResult::id).containsExactly(active.getId());
        assertThat(userRepository.lastCondition())
                .isEqualTo(new UserSearchCondition(null, null, null, null, null, false));
    }

    @Test
    @DisplayName("1. 정렬·페이지를 PageQuery로 그대로 넘긴다 (기본: createdAt 역순, id 보조)")
    void pageQueryFromPageable() {
        service.search(NO_CONDITION, PageRequest.of(2, 20,
                Sort.by(Sort.Order.asc("username"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));

        assertThat(userRepository.lastPageQuery()).isEqualTo(new PageQuery(2, 20, List.of(
                new PageQuery.Order("username", true),
                new PageQuery.Order("createdAt", false),
                new PageQuery.Order("id", true))));
    }

    @Test
    @DisplayName("2. username·name 부분 일치, role·배송담당·소속 유형 일치 조건을 domain 타입으로 바꿔 넘긴다")
    void conditions() {
        User target = created("hubman01", UserRole.USER, true);
        created("hubman02", UserRole.USER, false);
        created("hubboss01", UserRole.MASTER, false);
        signup("hubman03", "관리생성", OrganizationType.COMPANY);

        Page<UserResult> result = service.search(
                new UserSearchQuery("man", "관리", "USER", true, "HUB", null), DEFAULT_PAGE);

        assertThat(result.getContent()).extracting(UserResult::id).containsExactly(target.getId());
        assertThat(userRepository.lastCondition()).isEqualTo(new UserSearchCondition(
                "man", "관리", UserRole.USER, true, OrganizationType.HUB, false));
    }

    @Test
    @DisplayName("2. 빈 문자열 조건은 걸지 않는다 (username= 처럼 값 없이 온 경우)")
    void blankCondition() {
        signup("user01", "홍길동", OrganizationType.HUB);

        Page<UserResult> result = service.search(new UserSearchQuery(" ", "", null, null, null, null), DEFAULT_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(userRepository.lastCondition().username()).isNull();
        assertThat(userRepository.lastCondition().name()).isNull();
    }

    @Test
    @DisplayName("3. includeDeleted=true → 탈퇴·비활성화 사용자도 포함, active=false로 보인다")
    void includeDeleted() {
        signup("user01", "홍길동", OrganizationType.HUB);
        User withdrawn = signup("user02", "김철수", OrganizationType.COMPANY);
        withdrawn.withdraw(MASTER_ID);

        Page<UserResult> result = service.search(new UserSearchQuery(null, null, null, null, null, true), DEFAULT_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).filteredOn(r -> r.id().equals(withdrawn.getId()))
                .singleElement().satisfies(r -> assertThat(r.active()).isFalse());
        assertThat(userRepository.lastCondition().includeDeleted()).isTrue();
    }

    @Test
    @DisplayName("4. 결과는 Page<UserResult>: 요청한 page·size·sort와 전체 건수·전체 페이지를 담는다")
    void pageMetadata() {
        for (int i = 1; i <= 5; i++) {
            signup("user0" + i, "홍길동", OrganizationType.HUB);
        }
        Pageable second = PageRequest.of(1, 2, DEFAULT_PAGE.getSort());

        Page<UserResult> result = service.search(NO_CONDITION, second);

        assertThat(result.getContent()).extracting(UserResult::username).containsExactly("user03", "user04");
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getSort()).isEqualTo(DEFAULT_PAGE.getSort());
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }
}
