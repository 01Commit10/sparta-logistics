package com.user.fake;

import com.user.domain.service.PasswordHasher;

/** 테스트용: 해시 대신 접두사만 붙인다. 비교하는 동안 다른 요청이 끼어드는 상황을 duringNextMatches로 만든다. */
public class FakePasswordHasher implements PasswordHasher {

    private Runnable duringNextMatches;

    /** 다음 matches 호출 중에 한 번 실행한다 (BCrypt가 도는 동안 무효화가 일어나는 경우) */
    public void duringNextMatches(Runnable action) {
        this.duringNextMatches = action;
    }

    @Override
    public String hash(String rawPassword) {
        return "hashed:" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        Runnable action = duringNextMatches;
        duringNextMatches = null;
        if (action != null) {
            action.run();
        }
        return passwordHash.equals(hash(rawPassword));
    }
}
