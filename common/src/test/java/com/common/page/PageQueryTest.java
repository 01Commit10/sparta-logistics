package com.common.page;

import com.common.page.PageQuery.PageOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @ParameterizedTest
    @CsvSource({"-1, 10", "0, 0", "0, -5"})
    @DisplayName("page가 음수이거나 size가 1보다 작으면 만들 수 없다")
    void rejectsInvalidPageOrSize(int page, int size) {
        assertThatThrownBy(() -> new PageQuery(page, size, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page=" + page)
                .hasMessageContaining("size=" + size);
    }

    @ParameterizedTest
    @CsvSource({"0, 10, 0", "1, 10, 10", "2, 7, 14"})
    @DisplayName("offset은 page × size")
    void offset(int page, int size, long expected) {
        assertThat(new PageQuery(page, size, List.of()).offset()).isEqualTo(expected);
    }

    @Test
    @DisplayName("offset은 int 범위를 넘어도 long으로 계산한다")
    void offsetDoesNotOverflow() {
        PageQuery query = new PageQuery(Integer.MAX_VALUE, 100, List.of());

        assertThat(query.offset()).isEqualTo((long) Integer.MAX_VALUE * 100);
    }

    @Test
    @DisplayName("정렬 목록을 복사해 두므로 원본 리스트를 바꿔도 영향이 없고, 꺼낸 목록은 수정할 수 없다")
    void copiesOrderList() {
        List<PageOrder> original = new ArrayList<>(List.of(new PageOrder("name", true)));

        PageQuery query = new PageQuery(0, 10, original);
        original.add(new PageOrder("id", true));

        assertThat(query.orderList()).containsExactly(new PageOrder("name", true));
        assertThatThrownBy(() -> query.orderList().add(new PageOrder("id", true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
