package com.company.fake;

import com.company.company.domain.client.UserClient;
import com.company.company.domain.client.UserInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 등록한 사용자만 활성 사용자로 돌려준다. failWith()를 지정하면 원격 장애처럼 그 예외를 던진다. */
public class FakeUserClient implements UserClient {

    private final Map<UUID, UserInfo> users = new HashMap<>();
    private RuntimeException failure;
    private int calls;

    public FakeUserClient add(UUID userId, String role) {
        users.put(userId, new UserInfo(userId, role));
        return this;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public int calls() {
        return calls;
    }

    @Override
    public Optional<UserInfo> findActiveUser(UUID userId) {
        calls++;
        if (failure != null) {
            throw failure;
        }
        return Optional.ofNullable(users.get(userId));
    }
}
