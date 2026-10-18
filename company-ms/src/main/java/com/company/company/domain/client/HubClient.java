package com.company.company.domain.client;

import java.util.UUID;

/**
 * hub-ms 허브 조회 Port. 구현은 infrastructure.client (Feign).
 */
public interface HubClient {

    boolean existsHub(UUID hubId);
}
