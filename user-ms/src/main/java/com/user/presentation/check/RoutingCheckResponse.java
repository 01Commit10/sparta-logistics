package com.user.presentation.check;

import java.util.Map;

/**
 * 라우팅 확인 응답. 요청이 어느 서비스에 어떤 경로·헤더로 도착했는지 보여준다.
 * Gateway를 거쳤는지는 호출 주소(19090 vs 19095)로 구분한다. 서비스는 이를 믿을 만하게 판단할 수 없다
 * (Gateway는 trusted-proxies 설정 없이는 X-Forwarded-* 헤더를 붙이지 않음).
 */
public record RoutingCheckResponse(
        String service,
        String method,
        String path,
        String query,
        Map<String, String> headers
) {
}
