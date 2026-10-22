package com.common.remote;

import com.common.exception.BusinessException;
import com.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 원격 서비스가 요청을 업무 규칙으로 거절함 (4xx, 401 제외). 처리되지 않은 것이 확실하다.
 * {@link #getErrorCode()}는 원격 code를 복원한 값이라, 등록된 code면 원래 enum과 {@code ==}로 비교할 수 있다.
 * 잡지 않으면 원격과 같은 상태·code·메시지로 응답한다. 대상 서비스 이름은 클라이언트 메시지에 넣지 않는다 (C02 E6).
 */
@Getter
public class RemoteBusinessException extends BusinessException {

    private final String target;
    private final int status;

    public RemoteBusinessException(String target, int status, ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.target = target;
        this.status = status;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[target=" + target + ", status=" + status + ", code="
                + getErrorCode().getCode() + "]: " + getMessage();
    }
}
