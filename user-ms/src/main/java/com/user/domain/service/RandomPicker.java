package com.user.domain.service;

import java.util.List;

/**
 * 후보 중 하나를 무작위로 고르는 Port (배송담당자 무작위 배정). 구현은 infrastructure.random.
 * 테스트에서 결과를 고정할 수 있도록 Port로 둔다.
 */
public interface RandomPicker {

    /** candidates는 비어 있으면 안 된다 (후보 없음 판단은 호출하는 쪽) */
    <T> T pick(List<T> candidates);
}
