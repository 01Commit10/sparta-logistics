package com.common.page;

import com.common.page.PageQuery.PageOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringDataPagesTest {

    @Test
    @DisplayName("toPageable: page·size와 정렬 순서·방향을 그대로 옮긴다")
    void toPageable() {
        PageQuery query = new PageQuery(2, 20, List.of(
                new PageOrder("unitPrice", false), new PageOrder("name", true), new PageOrder("id", true)));

        Pageable pageable = SpringDataPages.toPageable(query);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getOffset()).isEqualTo(query.offset());
        assertThat(pageable.getSort()).containsExactly(
                Sort.Order.desc("unitPrice"), Sort.Order.asc("name"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("toPageable: 정렬이 없으면 unsorted")
    void toPageableWithoutSort() {
        Pageable pageable = SpringDataPages.toPageable(new PageQuery(0, 10, List.of()));

        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("toPageData: Page에서 content와 전체 건수만 꺼낸다")
    void toPageData() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageData<String> data = SpringDataPages.toPageData(page);

        assertThat(data.content()).containsExactly("a", "b");
        assertThat(data.totalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("PagePolicy.toQuery → toPageable 결과는 기존 PagePolicy.apply 결과와 같다 (두 방식이 같은 쿼리를 만든다)")
    void sameAsApply() {
        PagePolicy policy = PagePolicy.of("name", "unitPrice");
        Pageable requested = PageRequest.of(3, 500, Sort.by(Sort.Order.desc("unitPrice"), Sort.Order.asc("name")));

        Pageable viaQuery = SpringDataPages.toPageable(policy.toQuery(requested));

        assertThat(viaQuery).isEqualTo(policy.apply(requested));
    }
}
