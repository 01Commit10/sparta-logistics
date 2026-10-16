package com.gateway.domain.policy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 경로 정책과 비교할 요청 경로 (원문은 request.getPath().value()).
 * 라우트(PathPattern)와 하위 서비스(Tomcat)는 퍼센트 인코딩을 디코딩하고 ;뒤를 지운 경로로 처리한다.
 * 정책이 원문으로 비교하면 {@code /api/v1/%75sers/x}가 MASTER 규칙을 피하므로, 같은 디코딩 경로로 비교한다.
 * <p>
 * 해석이 구성 요소마다 달라질 수 있는 경로는 아예 거부한다 (Spring Security StrictHttpFirewall과 같은 기준).
 * <ul>
 *     <li>원문에 {@code ;}, {@code \}, {@code %2e}, {@code %2f}, {@code %5c}, {@code %00}, {@code %25}(이중 인코딩)</li>
 *     <li>잘못된 퍼센트 인코딩·UTF-8</li>
 *     <li>디코딩 후 {@code .}, {@code ..} 세그먼트</li>
 * </ul>
 * 정상 API 경로에는 이런 값이 없다.
 */
public final class RequestPath {

    private static final List<String> REJECTED_RAW = List.of(";", "\\", "%2e", "%2f", "%5c", "%00", "%25");

    private final String value;

    private RequestPath(String value) {
        this.value = value;
    }

    /** 거부할 경로면 empty */
    public static Optional<RequestPath> parse(String rawPath) {
        String lower = rawPath.toLowerCase(Locale.ROOT);
        if (REJECTED_RAW.stream().anyMatch(lower::contains)) {
            return Optional.empty();
        }
        return decode(rawPath)
                .filter(RequestPath::hasNoDotSegment)
                .map(RequestPath::new);
    }

    /** 디코딩한 경로 (예: /api/v1/users/u-1) */
    public String value() {
        return value;
    }

    /** URL 디코딩과 달리 +를 공백으로 바꾸지 않는다 (경로에서 +는 문자 그대로다) */
    private static Optional<String> decode(String raw) {
        if (raw.indexOf('%') < 0) {
            return Optional.of(raw);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '%') {
                bytes.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
                continue;
            }
            if (i + 2 >= raw.length()) {
                return Optional.empty();
            }
            int high = Character.digit(raw.charAt(i + 1), 16);
            int low = Character.digit(raw.charAt(i + 2), 16);
            if (high < 0 || low < 0) {
                return Optional.empty();
            }
            bytes.write(high * 16 + low);
            i += 2;
        }
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    private static boolean hasNoDotSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return value;
    }
}
