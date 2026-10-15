package com.gateway.domain.policy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * 경로 패턴 하나. 세그먼트 단위로 비교한다.
 * <ul>
 *     <li>{@code *}: 세그먼트 하나 (예: {@code /api/v1/users/*} → {@code /api/v1/users/u-1})</li>
 *     <li>{@code **}: 패턴 마지막에서만, 세그먼트 0개 이상</li>
 * </ul>
 * 비교 전에 경로를 정규화한다: 빈 세그먼트·{@code .} 제거, {@code ..} 해석, 소문자.
 */
public final class PathRule {

    private static final String ANY_SEGMENT = "*";
    private static final String ANY_REMAINING = "**";

    private final String pattern;
    private final List<String> segments;

    private PathRule(String pattern, List<String> segments) {
        this.pattern = pattern;
        this.segments = segments;
    }

    public static PathRule of(String pattern) {
        List<String> segments = normalize(pattern);
        int doubleWildcard = segments.indexOf(ANY_REMAINING);
        if (doubleWildcard >= 0 && doubleWildcard != segments.size() - 1) {
            throw new IllegalArgumentException("'**'는 패턴 마지막에만 쓸 수 있습니다: " + pattern);
        }
        return new PathRule(pattern, segments);
    }

    public boolean matches(String path) {
        List<String> target = normalize(path);
        for (int i = 0; i < segments.size(); i++) {
            String expected = segments.get(i);
            if (ANY_REMAINING.equals(expected)) {
                return true;
            }
            if (i >= target.size()) {
                return false;
            }
            if (!ANY_SEGMENT.equals(expected) && !expected.equals(target.get(i))) {
                return false;
            }
        }
        return segments.size() == target.size();
    }

    public String pattern() {
        return pattern;
    }

    private static List<String> normalize(String path) {
        Deque<String> result = new ArrayDeque<>();
        for (String segment : path.toLowerCase(Locale.ROOT).split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                result.pollLast();
                continue;
            }
            result.addLast(segment);
        }
        return List.copyOf(result);
    }

    @Override
    public String toString() {
        return pattern;
    }
}
