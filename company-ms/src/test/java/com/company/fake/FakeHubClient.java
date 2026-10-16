package com.company.fake;

import com.company.company.domain.client.HubClient;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 등록한 허브만 있다고 답한다. failWith()를 지정하면 원격 장애처럼 그 예외를 던진다. */
public class FakeHubClient implements HubClient {

    private final Set<UUID> hubs = new HashSet<>();
    private RuntimeException failure;
    private int calls;

    public FakeHubClient add(UUID hubId) {
        hubs.add(hubId);
        return this;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public int calls() {
        return calls;
    }

    @Override
    public boolean existsHub(UUID hubId) {
        calls++;
        if (failure != null) {
            throw failure;
        }
        return hubs.contains(hubId);
    }
}
