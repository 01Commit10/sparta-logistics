package com.gateway.domain.request;

/**
 * 접근 로그 한 줄 (GW-08): {@code method path status durationMs requestId userId}.
 * 경로만 기록하고 쿼리스트링·헤더(Authorization 등)는 기록하지 않는다. 값이 없으면 {@code -}.
 *
 * @param status 응답 상태 코드. 응답 전에 클라이언트가 끊으면 null
 * @param userId 인증된 사용자 UUID. 공개 경로·인증 실패면 null
 */
public record AccessLogLine(String method, String path, Integer status, long durationMs, String requestId, String userId) {

    private static final String ABSENT = "-";

    public String format() {
        return String.join(" ",
                orAbsent(method),
                orAbsent(withoutQuery(path)),
                status == null ? ABSENT : status.toString(),
                Long.toString(durationMs),
                orAbsent(requestId),
                orAbsent(userId));
    }

    private static String withoutQuery(String path) {
        if (path == null) {
            return null;
        }
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }

    private static String orAbsent(String value) {
        return value == null || value.isBlank() ? ABSENT : value;
    }
}
