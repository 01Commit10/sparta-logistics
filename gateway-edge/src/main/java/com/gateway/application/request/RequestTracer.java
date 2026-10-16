package com.gateway.application.request;

import com.gateway.domain.request.AccessLogLine;
import com.gateway.domain.request.RequestId;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 요청 추적 규칙 (GW-08). presentation 필터는 이 결과만 적용한다.
 */
@Service
public class RequestTracer {

    /** 클라이언트가 보낸 X-Request-Id가 형식에 맞으면 그대로, 아니면 새 UUID */
    public String resolveRequestId(String candidate) {
        return RequestId.of(candidate, UUID::randomUUID).value();
    }

    /** 접근 로그 한 줄. 쿼리스트링과 헤더는 넣지 않는다 */
    public String accessLogLine(String method, String path, Integer status, long durationMs, String requestId, String userId) {
        return new AccessLogLine(method, path, status, durationMs, requestId, userId).format();
    }
}
