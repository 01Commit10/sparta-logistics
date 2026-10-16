package com.common.remote;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import lombok.Getter;

/**
 * 원격 서비스를 쓸 수 없음: 5xx, Circuit Breaker OPEN, 연결 실패, 인스턴스 없음, 원격 401(호출 쪽 헤더 누락 버그, C02 E3).
 * 대체로 처리되지 않았다고 본다 (연결 실패·CB OPEN은 확실히, 5xx는 원격이 롤백했다고 봄). 잡지 않으면 503 SERVICE_UNAVAILABLE.
 */
@Getter
public class RemoteServiceException extends BusinessException {

    private final String target;
    /** 원격이 응답한 HTTP 상태. 응답이 없으면(CB OPEN, 연결 실패) null */
    private final Integer status;

    public RemoteServiceException(String target, Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE);
        this.target = target;
        this.status = null;
        initCause(cause);
    }

    public RemoteServiceException(String target, int status) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE);
        this.target = target;
        this.status = status;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[target=" + target + (status != null ? ", status=" + status : "") + "]: "
                + getMessage();
    }
}
