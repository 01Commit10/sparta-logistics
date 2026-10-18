package com.common.page;

import com.common.page.PageQuery.PageOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.stream.LongStream;

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

    static final List<PageOrder> ORDERS = List.of(new PageOrder("createdAt", false), new PageOrder("id", true));

    @Test
    @DisplayName("PageData: 페이지 정보·정렬은 PageQuery에서, 전체 건수는 PageData에서 가져온다")
    void fromPageData() {
        PageData<Integer> data = new PageData<>(List.of(1, 2), 5);
        PageQuery query = new PageQuery(1, 2, ORDERS);

        PageResponse<String> response = PageResponse.from(data, query, n -> "#" + n);

        assertThat(response.content()).containsExactly("#1", "#2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.sort()).containsExactly(
                new SortOrder("createdAt", Sort.Direction.DESC),
                new SortOrder("id", Sort.Direction.ASC));
    }

    @ParameterizedTest(name = "page={0}, size={1}, total={2} → totalPages={3}, hasNext={4}")
    @CsvSource({
            "0, 10, 0,  0, false",   // 0건
            "0, 10, 23, 3, true",    // 첫 페이지
            "2, 10, 23, 3, false",   // 마지막 페이지 (3건)
            "1, 10, 20, 2, false",   // 딱 나누어떨어지는 마지막 페이지
            "0, 10, 10, 1, false",   // 1페이지에 모두 들어감
            "9, 10, 15, 2, false"    // 마지막 페이지를 넘음
    })
    @DisplayName("PageData: totalPages·hasNext 경계값")
    void fromPageDataBoundaries(int page, int size, long total, int totalPages, boolean hasNext) {
        PageResponse<String> response = PageResponse.from(
                new PageData<String>(List.of(), total), new PageQuery(page, size, ORDERS), s -> s);

        assertThat(response.totalPages()).isEqualTo(totalPages);
        assertThat(response.hasNext()).isEqualTo(hasNext);
    }

    @ParameterizedTest(name = "page={0}, size={1}, total={2}")
    @CsvSource({"0, 10, 0", "0, 10, 23", "2, 10, 23", "1, 10, 20", "9, 10, 15", "1, 2, 5"})
    @DisplayName("PageData 방식과 기존 Page 방식은 같은 응답을 만든다")
    void sameAsPageBased(int page, int size, long total) {
        List<String> content = contentOf(page, size, total);
        PageQuery query = new PageQuery(page, size, ORDERS);

        PageResponse<String> viaPageData = PageResponse.from(new PageData<>(content, total), query, s -> s);
        PageResponse<String> viaPage = PageResponse.from(
                new PageImpl<>(content, SpringDataPages.toPageable(query), total), s -> s);

        assertThat(viaPageData).isEqualTo(viaPage);
    }

    /** 전체 total건 중 page번째 페이지에 실제로 들어갈 내용 (범위 밖이면 빈 목록) */
    private static List<String> contentOf(int page, int size, long total) {
        long from = (long) page * size;
        long count = Math.max(0, Math.min(size, total - from));
        return LongStream.range(from, from + count).mapToObj(i -> "item" + i).toList();
    }
}
