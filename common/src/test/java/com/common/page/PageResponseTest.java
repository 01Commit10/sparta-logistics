package com.common.page;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    static final Sort SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    @Test
    @DisplayName("Page의 페이지 정보와 적용된 정렬을 옮긴다")
    void fromPage() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2, SORT), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.sort()).containsExactly(
                new SortOrder("createdAt", Sort.Direction.DESC),
                new SortOrder("id", Sort.Direction.ASC));
    }

    @Test
    @DisplayName("mapper로 content를 변환한다")
    void fromPageWithMapper() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(0, 10), 2);

        PageResponse<String> response = PageResponse.from(page, n -> "#" + n);

        assertThat(response.content()).containsExactly("#1", "#2");
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("마지막 페이지를 넘으면 빈 content, hasNext false")
    void beyondLastPage() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(9, 10), 15);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("JSON으로 주고받을 수 있다 (Feign 응답 역직렬화)")
    void jsonRoundTrip() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("a"), PageRequest.of(0, 10, SORT), 1));

        String json = objectMapper.writeValueAsString(response);
        PageResponse<?> restored = objectMapper.readValue(json, PageResponse.class);

        assertThat(json).contains("\"direction\":\"DESC\"").doesNotContain("pageable");
        assertThat(restored).isEqualTo(response);
    }
}
