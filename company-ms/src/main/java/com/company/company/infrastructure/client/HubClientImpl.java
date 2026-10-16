package com.company.company.infrastructure.client;

import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.company.company.domain.client.HubClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubClientImpl implements HubClient {

    private static final String TARGET = "hub-ms";

    private final HubFeignClient hubFeignClient;
    private final RemoteCallTemplate remote;

    @Override
    public boolean existsHub(UUID hubId) {
        try {
            remote.execute(TARGET, () -> hubFeignClient.getHub(hubId));
            return true;
        } catch (RemoteBusinessException e) {
            // 없는 허브는 404. 그 밖의 실패(5xx·타임아웃·CB OPEN)는 그대로 던진다
            if (e.getStatus() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            throw e;
        }
    }
}
