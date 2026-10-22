package com.user.infrastructure.random;

import com.user.domain.service.RandomPicker;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 배정 결과는 보안 값이 아니므로 SecureRandom이 아니라 ThreadLocalRandom을 쓴다.
 */
@Component
public class ThreadLocalRandomPicker implements RandomPicker {

    @Override
    public <T> T pick(List<T> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("고를 후보가 없습니다.");
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
