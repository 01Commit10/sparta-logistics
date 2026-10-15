package com.common.auth.web;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.common.auth.AuthUser;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * X-User-Id·X-User-Role 해석을 한 곳에서 한다 (C01 D4). 리졸버·Provider·입구 인터셉터·AuditorAware가 모두 이 결과를 쓴다.
 * <ul>
 *     <li>{@link Absent}: 둘 다 없음 (공개 경로, 요청 밖). 빈 값은 없는 것으로 본다</li>
 *     <li>{@link Valid}: 둘 다 있고 형식이 맞음. 앞뒤 공백은 지운다</li>
 *     <li>{@link Invalid}: 하나만 있음, UUID 표준 형식이 아님, 역할이 MASTER·USER가 아님, 같은 헤더가 두 번 옴</li>
 * </ul>
 * 형식만 본다. 사용자가 실제로 있는지는 확인하지 않는다 (Gateway가 서명된 토큰에서 꺼낸 값).
 */
public final class CurrentUserHeaderParser {

    public sealed interface Result permits Absent, Valid, Invalid {
    }

    public record Absent() implements Result {
    }

    public record Valid(AuthUser user) implements Result {
    }

    public record Invalid() implements Result {
    }

    private static final Absent ABSENT = new Absent();
    private static final Invalid INVALID = new Invalid();

    public static Result parse(String userId, String role) {
        String id = trimToNull(userId);
        String roleValue = trimToNull(role);
        if (id == null && roleValue == null) {
            return ABSENT;
        }
        if (id == null || roleValue == null) {
            return INVALID;
        }
        Optional<UUID> uuid = parseUuid(id);
        Optional<AuthRole> authRole = AuthRole.from(roleValue);
        if (uuid.isEmpty() || authRole.isEmpty()) {
            return INVALID;
        }
        return new Valid(new AuthUser(uuid.get(), authRole.get()));
    }

    /** 같은 헤더가 여러 번 오면 어느 값을 믿을지 모호하므로 {@link Invalid} */
    public static Result parse(HttpServletRequest request) {
        List<String> ids = values(request, AuthContract.HEADER_USER_ID);
        List<String> roles = values(request, AuthContract.HEADER_USER_ROLE);
        if (ids.size() > 1 || roles.size() > 1) {
            return INVALID;
        }
        return parse(first(ids), first(roles));
    }

    private static List<String> values(HttpServletRequest request, String name) {
        return Collections.list(request.getHeaders(name));
    }

    private static String first(List<String> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    /** {@link UUID#fromString}은 "1-1-1-1-1"도 받으므로, 다시 문자열로 바꿔 원래 값과 같은지 확인한다 */
    private static Optional<UUID> parseUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equalsIgnoreCase(value) ? Optional.of(uuid) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CurrentUserHeaderParser() {
    }
}
