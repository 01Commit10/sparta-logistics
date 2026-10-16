package com.company.company.infrastructure.client;

import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.company.company.domain.client.UserClient;
import com.company.company.domain.client.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserClientImpl implements UserClient {

    private static final String TARGET = "user-ms";

    private final UserFeignClient userFeignClient;
    private final RemoteCallTemplate remote;

    @Override
    public Optional<UserInfo> findActiveUser(UUID userId) {
        try {
            UserFeignDto user = remote.execute(TARGET, () -> userFeignClient.getUser(userId));
            return Optional.of(new UserInfo(user.id(), user.role()));
        } catch (RemoteBusinessException e) {
            // 없거나 삭제된 사용자는 404. 그 밖의 실패(5xx·타임아웃·CB OPEN)는 그대로 던진다
            if (e.getStatus() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
