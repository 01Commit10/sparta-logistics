package com.gateway.application.auth;

import com.gateway.domain.token.TokenVerifier;
import com.gateway.domain.token.VerificationResult;

import java.util.ArrayList;
import java.util.List;

/** 미리 정한 결과를 돌려주고, 받은 토큰을 기록한다 */
class FakeTokenVerifier implements TokenVerifier {

    private final VerificationResult result;
    private final List<String> received = new ArrayList<>();

    FakeTokenVerifier(VerificationResult result) {
        this.result = result;
    }

    @Override
    public VerificationResult verify(String token) {
        received.add(token);
        return result;
    }

    List<String> received() {
        return received;
    }
}
