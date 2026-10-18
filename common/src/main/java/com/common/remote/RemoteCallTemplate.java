package com.common.remote;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Feign 호출을 감싸 실패를 원격 예외 3종으로 바꾼다 (C02 3.1). 각 서비스의 infrastructure Adapter에서 쓴다.
 * <pre>
 * remote.execute("hub-ms", () -> hubClient.decrease(request));
 * </pre>
 * <ul>
 *     <li>{@link RemoteBusinessException}: 원격 4xx (ErrorDecoder가 만든 것을 그대로)</li>
 *     <li>{@link RemoteServiceException}: 원격 5xx·401, 연결 실패(거부·연결 타임아웃·인스턴스 없음) — 처리 안 됨</li>
 *     <li>{@link RemoteTimeoutException}: 요청을 보낸 뒤 결과를 알 수 없음(read 타임아웃, 응답 중 연결 끊김,
 *         2xx 본문 변환 실패, 원격 504) — 결과 모름</li>
 * </ul>
 * Feign은 원인을 감싸 던지므로(RetryableException 등) 원인 체인을 따라가며 판단한다. 재시도는 하지 않는다 (C03).
 */
public class RemoteCallTemplate {

    private static final int MAX_CAUSE_DEPTH = 10;
    /** 2xx를 받았지만 본문을 반환 타입으로 못 바꿈. 원격은 처리했다 */
    private static final String DECODE_FAILURE = "feign.codec.DecodeException";

    public <T> T execute(String target, Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            throw translate(target, e);
        }
    }

    public void run(String target, Runnable call) {
        execute(target, () -> {
            call.run();
            return null;
        });
    }

    RuntimeException translate(String target, Throwable failure) {
        Throwable cause = failure;
        IOException ioFailure = null;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (cause instanceof RemoteBusinessException || cause instanceof RemoteServiceException
                    || cause instanceof RemoteTimeoutException) {
                return (RuntimeException) cause;
            }
            if (isConnectFailure(cause)) {
                return new RemoteServiceException(target, cause);
            }
            // Jackson 예외도 IOException이라 원인을 따라가기 전에 먼저 본다 (HTML 등 IOException이 아닌 원인과 같게 분류)
            if (isDecodeFailure(cause)) {
                return new RemoteTimeoutException(target, cause);
            }
            if (cause instanceof IOException io && ioFailure == null) {
                ioFailure = io;
            }
        }
        if (ioFailure != null) {
            return new RemoteTimeoutException(target, ioFailure);
        }
        return new RemoteServiceException(target, failure);
    }

    private static boolean isDecodeFailure(Throwable cause) {
        return cause != null && DECODE_FAILURE.equals(cause.getClass().getName());
    }

    /** 연결 단계 실패: 요청이 상대에게 닿지 않았다. JDK 기본 클라이언트는 연결 타임아웃도 SocketTimeoutException이라 메시지로 구분한다 */
    private static boolean isConnectFailure(Throwable cause) {
        if (cause instanceof ConnectException || cause instanceof NoRouteToHostException
                || cause instanceof UnknownHostException) {
            return true;
        }
        return cause instanceof SocketTimeoutException
                && cause.getMessage() != null
                && cause.getMessage().toLowerCase(Locale.ROOT).contains("connect");
    }
}
