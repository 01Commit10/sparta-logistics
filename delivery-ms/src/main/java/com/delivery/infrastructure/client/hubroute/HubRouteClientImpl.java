package com.delivery.infrastructure.client.hubroute;

import com.common.exception.BusinessException;
import com.common.exception.DeliveryErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.delivery.application.client.HubRouteClient;
import com.delivery.domain.vo.RouteEstimate;
import com.delivery.infrastructure.client.hubroute.dto.HubRouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubRouteClientImpl implements HubRouteClient {

    private final HubRouteFeignClient hubRouteFeignClient;
    private final RemoteCallTemplate remoteCallTemplate;

    @Override
    public RouteEstimate getRouteEstimate(UUID departureHubId, UUID arrivalHubId) {
        try {
            HubRouteResponse hubRouteResponse = remoteCallTemplate
                    .execute("hub-ms", () -> hubRouteFeignClient.getHubRoute(departureHubId, arrivalHubId));
            return new RouteEstimate(hubRouteResponse.distanceKm(), hubRouteResponse.durationMin());
        } catch (RemoteBusinessException e) {
            if (e.getStatus() == 404) {
                throw new BusinessException(DeliveryErrorCode.HUB_ROUTE_NOT_FOUND);
            }
            throw e;
        }
    }
}
