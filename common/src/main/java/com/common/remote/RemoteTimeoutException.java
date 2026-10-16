package com.common.remote;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import lombok.Getter;

/**
 * 요청은 보냈지만 응답을 받지 못함 (read 타임아웃). <b>원격에서 처리됐는지 알 수 없다.</b>
 * 호출한 서비스는 조회로 결과를 확인하거나 멱등성 키로 다시 보내야 한다 (SYS-13 8장). 잡지 않으면 504 SERVICE_TIMEOUT.
 */
@Getter
public class RemoteTimeoutException extends BusinessException {

    private final String target;

    public RemoteTimeoutException(String target, Throwable cause) {
        super(CommonErrorCode.SERVICE_TIMEOUT);
        this.target = target;
        initCause(cause);
    }

    @Override
    public String toString() {
        return getClass().getName() + "[target=" + target + "]: " + getMessage();
    }
}
