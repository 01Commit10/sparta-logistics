package com.common.remote;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import lombok.Getter;

/**
 * 요청은 보냈지만 결과를 알 수 없음: read 타임아웃, 응답 중 연결 끊김, 2xx 응답 본문 변환 실패, 원격 504(원격이 그다음 호출에서 타임아웃).
 * <b>원격에서 처리됐는지 알 수 없다.</b>
 * 호출한 서비스는 조회로 결과를 확인하거나 멱등성 키로 다시 보내야 한다 (SYS-13 8장). 잡지 않으면 504 SERVICE_TIMEOUT.
 */
@Getter
public class RemoteTimeoutException extends BusinessException {

    private final String target;
    /** 원격이 응답한 HTTP 상태. 응답을 못 받았거나(타임아웃) 2xx 본문을 못 읽었으면 null */
    private final Integer status;

    public RemoteTimeoutException(String target, Throwable cause) {
        super(CommonErrorCode.SERVICE_TIMEOUT);
        this.target = target;
        this.status = null;
        initCause(cause);
    }

    public RemoteTimeoutException(String target, int status) {
        super(CommonErrorCode.SERVICE_TIMEOUT);
        this.target = target;
        this.status = status;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[target=" + target + (status != null ? ", status=" + status : "") + "]: "
                + getMessage();
    }
}
