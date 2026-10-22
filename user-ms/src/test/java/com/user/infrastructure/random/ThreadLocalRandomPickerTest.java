package com.user.infrastructure.random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreadLocalRandomPickerTest {

    private final ThreadLocalRandomPicker picker = new ThreadLocalRandomPicker();

    @Test
    @DisplayName("후보가 1명이면 그 사람을 고른다")
    void single() {
        assertThat(picker.pick(List.of("a"))).isEqualTo("a");
    }

    @Test
    @DisplayName("여러 번 고르면 후보 안에서만, 모든 후보가 한 번 이상 나온다")
    void spread() {
        List<String> candidates = List.of("a", "b", "c");
        Set<String> picked = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            picked.add(picker.pick(candidates));
        }
        assertThat(picked).containsExactlyInAnyOrderElementsOf(candidates);
    }

    @Test
    @DisplayName("후보가 없으면 IllegalArgumentException (후보 없음은 호출하는 쪽이 먼저 거른다)")
    void empty() {
        assertThatThrownBy(() -> picker.pick(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
