package com.order.infrastructure.client.stub;

import com.order.domain.client.UserClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@Profile("stub")
public class StubUserClient implements UserClient {
    @Override
    public Optional<String> findSlackId(UUID userId) {
        return Optional.empty();
    }
}
