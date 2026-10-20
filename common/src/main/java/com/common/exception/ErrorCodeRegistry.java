package com.common.exception;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * code 문자열 → ErrorCode 복원 (SYS-13 5장, C02 E4). 서비스 간 Feign 호출에서 원격 서비스가 내려준 code를
 * 원래 enum으로 되돌려, 호출한 서비스가 {@code e.getErrorCode() == HubErrorCode.INSUFFICIENT_STOCK}처럼 비교할 수 있게 한다.
 * 새 ErrorCode enum을 만들면 ALL에 추가한다 (빠뜨리면 ErrorCodeUniquenessTest가 실패한다).
 */
public final class ErrorCodeRegistry {

    private static final List<ErrorCode> ALL = Stream.of(
                    CommonErrorCode.values(),
                    AuthErrorCode.values(),
                    UserErrorCode.values(),
                    CompanyErrorCode.values(),
                    ProductErrorCode.values(),
                    OrderErrorCode.values(),
                    DeliveryErrorCode.values(),
                    InventoryErrorCode.values())
            .flatMap(Arrays::stream)
            .map(ErrorCode.class::cast)
            .toList();

    /** code가 겹치면 클래스 로딩 시점에 실패한다 (어느 enum으로 복원할지 모호해지므로) */
    private static final Map<String, ErrorCode> BY_CODE = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(ErrorCode::getCode, Function.identity()));

    public static List<ErrorCode> all() {
        return ALL;
    }

    public static Optional<ErrorCode> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code));
    }

    private ErrorCodeRegistry() {
    }
}
