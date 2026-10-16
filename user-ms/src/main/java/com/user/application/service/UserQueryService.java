package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.UserResult;
import com.user.application.dto.UserSearchQuery;
import com.user.domain.repository.PageData;
import com.user.domain.repository.PageQuery;
import com.user.domain.repository.UserRepository;
import com.user.domain.repository.UserSearchCondition;
import com.user.domain.service.PasswordHasher;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 읽기 유스케이스.
 */
@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserQueryService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * USR-02 로그인: 활성 사용자의 ID·비밀번호를 확인한다.
     * 없는 ID·틀린 비밀번호·탈퇴(비활성화) 계정은 모두 같은 INVALID_CREDENTIALS (어느 쪽인지 드러내지 않음).
     * 없는 ID는 비밀번호 비교를 건너뛴다. ID 존재 여부는 공개 API(check-username, USR-20)가 이미 알려 주므로
     * 응답 시간을 맞춰도 숨겨지는 정보가 없다 (U05 D2).
     */
    public AuthenticatedUser authenticate(String username, String rawPassword) {
        return userRepository.findActiveByUsername(username)
                .filter(user -> passwordHasher.matches(rawPassword, user.getPasswordHash()))
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_CREDENTIALS));
    }

    /** USR-03 재발급: 토큰에 든 값이 아니라 DB의 최신 역할·비밀번호 변경 필요 여부로 새 Access를 만든다 */
    public Optional<AuthenticatedUser> findActiveAuthUser(UUID userId) {
        return userRepository.findActiveById(userId).map(AuthenticatedUser::from);
    }

    /** USR-20: 탈퇴한 사용자의 ID도 재사용할 수 없으므로 삭제된 사용자까지 확인한다 */
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(Username.of(username).value());
    }

    /** USR-18: 다른 서비스의 사용자 검증용. 탈퇴·비활성화된 사용자는 없는 것으로 본다 */
    public UserResult getActiveUser(UUID userId) {
        return userRepository.findActiveById(userId)
                .map(UserResult::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    /** USR-12 관리자 상세 조회 (MASTER): 탈퇴·비활성화된 사용자도 보여 준다 (active=false, 삭제 일시·삭제자) */
    public UserResult getUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResult::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * USR-11 관리자 목록 검색 (MASTER). pageable은 presentation이 PagePolicy로 정규화해서 넘긴다 (size·정렬 필드·기본 정렬).
     * domain Port는 Spring 타입을 모르므로 PageQuery로 바꿔 부르고, 결과를 다시 Page로 돌려준다.
     */
    public Page<UserResult> search(UserSearchQuery query, Pageable pageable) {
        UserSearchCondition condition = new UserSearchCondition(query.username(), query.name(),
                query.role() == null ? null : UserRole.valueOf(query.role()),
                query.deliveryManager(),
                query.organizationType() == null ? null : OrganizationType.valueOf(query.organizationType()),
                Boolean.TRUE.equals(query.includeDeleted()));
        PageData<UserResult> page = userRepository.search(condition, toPageQuery(pageable)).map(UserResult::from);
        return new PageImpl<>(page.content(), pageable, page.totalElements());
    }

    private static PageQuery toPageQuery(Pageable pageable) {
        return new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().stream()
                .map(order -> new PageQuery.Order(order.getProperty(), order.isAscending()))
                .toList());
    }
}
