package com.user.infrastructure.persistence;

import com.user.domain.entity.User;
import com.user.domain.repository.PageData;
import com.user.domain.repository.PageQuery;
import com.user.domain.repository.UserRepository;
import com.user.domain.repository.UserSearchCondition;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * USR-11 목록 검색 실제 쿼리 (U13 테스트 8, H2 PostgreSQL 모드). PostgreSQL 확인은 UserRepositoryPostgresTest.
 */
@DataJpaTest
@Import(UserRepositoryImpl.class)
class UserRepositorySearchTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UserSearchCondition ACTIVE = new UserSearchCondition(null, null, null, null, null, false);
    private static final List<PageQuery.Order> DEFAULT_ORDERS = List.of(
            new PageQuery.Order("createdAt", false), new PageQuery.Order("id", true));

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager em;

    private User signup(String username, String name, OrganizationType type, LocalDateTime createdAt) {
        User user = userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), name, "hashed",
                "U-" + username, type, "센터"));
        setCreatedAt(user, createdAt);
        return user;
    }

    private User created(String username, String name, UserRole role, OrganizationType type, boolean deliveryManager,
                         LocalDateTime createdAt) {
        User user = userRepository.save(User.createByMaster(UUID.randomUUID(), Username.of(username), name, "hashed",
                "U-" + username, type, "센터", role, deliveryManager, MASTER_ID));
        setCreatedAt(user, createdAt);
        return user;
    }

    /** 생성 일시는 Auditing이 넣고 수정할 수 없는 컬럼이라, 정렬 확인용으로 쿼리로 직접 바꾼다 */
    private void setCreatedAt(User user, LocalDateTime createdAt) {
        em.createQuery("update User u set u.createdAt = :createdAt where u.id = :id")
                .setParameter("createdAt", createdAt).setParameter("id", user.getId()).executeUpdate();
    }

    private static LocalDateTime day(int day) {
        return LocalDateTime.of(2026, 10, day, 9, 0);
    }

    private PageData<User> search(UserSearchCondition condition, int page, int size, List<PageQuery.Order> orders) {
        em.flush();
        em.clear();
        return userRepository.search(condition, new PageQuery(page, size, orders));
    }

    private static List<String> usernames(PageData<User> page) {
        return page.content().stream().map(User::getUsername).toList();
    }

    @Test
    @DisplayName("1. 조건 없음: 활성 사용자만, 생성일 역순")
    void activeOnly_createdAtDesc() {
        signup("user01", "가", OrganizationType.HUB, day(1));
        signup("user02", "나", OrganizationType.HUB, day(3));
        signup("user03", "다", OrganizationType.HUB, day(2));
        signup("user04", "라", OrganizationType.HUB, day(4)).withdraw(MASTER_ID);

        PageData<User> result = search(ACTIVE, 0, 10, DEFAULT_ORDERS);

        assertThat(usernames(result)).containsExactly("user02", "user03", "user01");
        assertThat(result.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("3. includeDeleted=true → 탈퇴·비활성화 사용자도 포함")
    void includeDeleted() {
        signup("user01", "가", OrganizationType.HUB, day(1));
        User withdrawn = signup("user02", "나", OrganizationType.HUB, day(2));
        withdrawn.withdraw(MASTER_ID);
        userRepository.save(withdrawn);

        PageData<User> result = search(new UserSearchCondition(null, null, null, null, null, true), 0, 10, DEFAULT_ORDERS);

        assertThat(usernames(result)).containsExactly("user02", "user01");
    }

    @Test
    @DisplayName("2. username·name 부분 일치 (대소문자 무시), role·배송담당·소속 유형 일치를 모두 AND로 건다")
    void conditions() {
        created("hubman01", "Kim 담당", UserRole.USER, OrganizationType.HUB, true, day(1));
        created("hubman02", "Kim 담당", UserRole.USER, OrganizationType.HUB, false, day(2));
        created("hubman03", "Kim 담당", UserRole.MASTER, OrganizationType.HUB, true, day(3));
        created("hubman04", "Kim 담당", UserRole.USER, OrganizationType.COMPANY, false, day(4));
        created("hubman05", "Lee 담당", UserRole.USER, OrganizationType.HUB, true, day(5));
        created("comman06", "Kim 담당", UserRole.USER, OrganizationType.HUB, true, day(6));

        assertThat(usernames(search(new UserSearchCondition("bma", "kim", UserRole.USER, true, OrganizationType.HUB, false),
                0, 10, DEFAULT_ORDERS))).containsExactly("hubman01");
        assertThat(usernames(search(new UserSearchCondition(null, null, null, null, OrganizationType.COMPANY, false),
                0, 10, DEFAULT_ORDERS))).containsExactly("hubman04");
        assertThat(usernames(search(new UserSearchCondition(null, null, UserRole.MASTER, null, null, false),
                0, 10, DEFAULT_ORDERS))).containsExactly("hubman03");
        assertThat(usernames(search(new UserSearchCondition(null, "IM 담", null, false, null, false),
                0, 10, DEFAULT_ORDERS))).containsExactly("hubman04", "hubman02");
    }

    @Test
    @DisplayName("2. 검색어의 %·_는 와일드카드가 아니라 글자로 찾는다")
    void likeEscape() {
        signup("user01", "100%상사", OrganizationType.COMPANY, day(1));
        signup("user02", "100원상사", OrganizationType.COMPANY, day(2));
        signup("user03", "a_b", OrganizationType.COMPANY, day(3));
        signup("user04", "axb", OrganizationType.COMPANY, day(4));

        assertThat(usernames(search(new UserSearchCondition(null, "0%", null, null, null, false), 0, 10, DEFAULT_ORDERS)))
                .containsExactly("user01");
        assertThat(usernames(search(new UserSearchCondition(null, "a_", null, null, null, false), 0, 10, DEFAULT_ORDERS)))
                .containsExactly("user03");
    }

    @Test
    @DisplayName("정렬: username·name 오름/내림차순")
    void sortByUsernameAndName() {
        signup("bbbb", "나", OrganizationType.HUB, day(1));
        signup("aaaa", "다", OrganizationType.HUB, day(2));
        signup("cccc", "가", OrganizationType.HUB, day(3));

        assertThat(usernames(search(ACTIVE, 0, 10, List.of(new PageQuery.Order("username", true)))))
                .containsExactly("aaaa", "bbbb", "cccc");
        assertThat(usernames(search(ACTIVE, 0, 10, List.of(new PageQuery.Order("name", false)))))
                .containsExactly("aaaa", "bbbb", "cccc");
        assertThat(usernames(search(ACTIVE, 0, 10, List.of(new PageQuery.Order("username", false)))))
                .containsExactly("cccc", "bbbb", "aaaa");
    }

    @Test
    @DisplayName("같은 생성일: 보조 정렬 id로 페이지 사이에 빠지거나 겹치는 행이 없다")
    void tieBreaker() {
        for (int i = 1; i <= 7; i++) {
            signup("user0" + i, "같은날", OrganizationType.HUB, day(1));
        }

        List<String> all = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            all.addAll(usernames(search(ACTIVE, page, 3, DEFAULT_ORDERS)));
        }

        assertThat(all).hasSize(7).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("페이지: offset·limit와 조건에 맞는 전체 건수 (마지막 페이지는 남은 만큼)")
    void paging() {
        for (int i = 1; i <= 5; i++) {
            signup("user0" + i, "홍길동", OrganizationType.HUB, day(i));
        }

        PageData<User> last = search(ACTIVE, 2, 2, DEFAULT_ORDERS);
        PageData<User> beyond = search(ACTIVE, 9, 2, DEFAULT_ORDERS);

        assertThat(usernames(last)).containsExactly("user01");
        assertThat(last.totalElements()).isEqualTo(5);
        assertThat(beyond.content()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(5);
    }
}
