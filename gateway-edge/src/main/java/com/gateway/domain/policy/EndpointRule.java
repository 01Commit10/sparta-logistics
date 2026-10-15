package com.gateway.domain.policy;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 메서드 + 경로 규칙 하나. 설정 형식은 {@code "GET,PATCH /api/v1/users/*"}이고, 메서드 {@code *}는 전체를 뜻한다.
 * 경로 비교는 {@link PathRule}을 따른다.
 */
public final class EndpointRule {

    private static final String ANY_METHOD = "*";
    private static final Set<String> KNOWN_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final String spec;
    private final Set<String> methods;
    private final PathRule path;

    private EndpointRule(String spec, Set<String> methods, PathRule path) {
        this.spec = spec;
        this.methods = methods;
        this.path = path;
    }

    public static EndpointRule parse(String spec) {
        String trimmed = spec.strip();
        int pathStart = trimmed.indexOf('/');
        if (pathStart <= 0) {
            throw invalid(spec, "'메서드 경로' 형식이어야 합니다");
        }
        String pathPart = trimmed.substring(pathStart);
        if (pathPart.chars().anyMatch(Character::isWhitespace)) {
            throw invalid(spec, "경로에 공백이 있습니다");
        }
        return new EndpointRule(spec, parseMethods(spec, trimmed.substring(0, pathStart)), PathRule.of(pathPart));
    }

    public boolean matches(String method, String requestPath) {
        boolean methodMatches = methods.contains(ANY_METHOD) || methods.contains(method.toUpperCase(Locale.ROOT));
        return methodMatches && path.matches(requestPath);
    }

    private static Set<String> parseMethods(String spec, String methodPart) {
        Set<String> methods = Arrays.stream(methodPart.split(",", -1))
                .map(m -> m.strip().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        boolean valid = methods.equals(Set.of(ANY_METHOD)) || KNOWN_METHODS.containsAll(methods);
        if (!valid) {
            throw invalid(spec, "메서드는 " + KNOWN_METHODS + " 또는 '*' 하나여야 합니다");
        }
        return methods;
    }

    private static IllegalArgumentException invalid(String spec, String reason) {
        return new IllegalArgumentException("경로 정책 규칙 오류 (" + reason + "): \"" + spec + "\"");
    }

    @Override
    public String toString() {
        return spec;
    }
}
