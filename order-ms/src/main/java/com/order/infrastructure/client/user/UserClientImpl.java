package com.order.infrastructure.client.user;

import com.common.exception.BusinessException;
import com.common.remote.RemoteCallTemplate;
import com.order.domain.client.UserClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@Profile("!stub")
@RequiredArgsConstructor
public class UserClientImpl implements UserClient {
    private static final String TARGET = "user-ms";
    private final UserFeignClient userFeignClient;
    private final RemoteCallTemplate remoteCallTemplate;


    @Override
    public Optional<String> findSlackId(UUID userId) {
        try {
            UserFeignResponse user = remoteCallTemplate.execute(TARGET, () -> userFeignClient.getUser(userId));
            return Optional.ofNullable(user.slackId());
        } catch (BusinessException e) {  // 404, 5xx, 타임아웃 모두 멘션만 빠지고 알림은 간다.
            return Optional.empty();
        }

    }
}
