package com.user.application.dto;

import com.user.domain.entity.User;

/**
 * 로그인 비밀번호 확인용: 활성 사용자와 저장된 비밀번호 해시. BCrypt 비교를 트랜잭션 밖(AuthFacade)에서 하려고 조회 결과로 넘긴다.
 * 해시는 AuthFacade 밖으로 내보내지 않는다 (응답·로그 금지).
 */
public record UserCredential(AuthenticatedUser user, String passwordHash) {

    public static UserCredential from(User user) {
        return new UserCredential(AuthenticatedUser.from(user), user.getPasswordHash());
    }

    @Override
    public String toString() {
        return "UserCredential[user=" + user + ", passwordHash=***]";
    }
}
