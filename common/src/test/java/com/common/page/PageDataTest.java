package com.common.page;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageDataTest {

    @Test
    @DisplayName("map은 content만 바꾸고 전체 건수는 그대로 둔다")
    void mapKeepsTotalElements() {
        PageData<Integer> data = new PageData<>(List.of(1, 2), 23);

        PageData<String> mapped = data.map(n -> "#" + n);

        assertThat(mapped.content()).containsExactly("#1", "#2");
        assertThat(mapped.totalElements()).isEqualTo(23);
    }

    @Test
    @DisplayName("content를 복사해 두므로 원본 리스트를 바꿔도 영향이 없고, 꺼낸 목록은 수정할 수 없다")
    void copiesContent() {
        List<String> original = new ArrayList<>(List.of("a"));

        PageData<String> data = new PageData<>(original, 1);
        original.add("b");

        assertThat(data.content()).containsExactly("a");
        assertThatThrownBy(() -> data.content().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("빈 페이지도 만들 수 있다 (범위 밖 페이지 요청)")
    void emptyContent() {
        PageData<String> data = new PageData<>(List.of(), 15);

        assertThat(data.content()).isEmpty();
        assertThat(data.map(String::length).totalElements()).isEqualTo(15);
    }
}
