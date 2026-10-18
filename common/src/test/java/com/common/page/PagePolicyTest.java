package com.common.page;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.page.PageQuery.PageOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagePolicyTest {

    static final PagePolicy POLICY = PagePolicy.of("name");

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 30, 50, 100})
    @DisplayName("size: 1~100은 그대로 쓴다")
    void keepsSizeInRange(int size) {
        Pageable result = POLICY.apply(PageRequest.of(2, size));

        assertThat(result.getPageSize()).isEqualTo(size);
        assertThat(result.getPageNumber()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {101, 150, 2000})
    @DisplayName("size: 100을 넘으면 10으로 바꾼다")
    void replacesOversizedWithDefault(int size) {
        assertThat(POLICY.apply(PageRequest.of(0, size)).getPageSize()).isEqualTo(PagePolicy.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("정렬이 없으면 createdAt DESC, 보조 정렬 id ASC")
    void appliesDefaultSort() {
        Pageable result = POLICY.apply(PageRequest.of(0, 10));

        assertThat(result.getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("Pageable이 없거나 unpaged면 0페이지, 10건, 기본 정렬")
    void handlesUnpaged() {
        for (Pageable pageable : new Pageable[]{null, Pageable.unpaged()}) {
            Pageable result = POLICY.apply(pageable);

            assertThat(result.getPageNumber()).isZero();
            assertThat(result.getPageSize()).isEqualTo(PagePolicy.DEFAULT_SIZE);
            assertThat(result.getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        }
    }

    @Test
    @DisplayName("허용한 필드 정렬은 순서를 유지하고 마지막에 id를 붙인다")
    void keepsAllowedSort() {
        Sort requested = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("createdAt"));

        Pageable result = POLICY.apply(PageRequest.of(0, 10, requested));

        assertThat(result.getSort()).containsExactly(
                Sort.Order.asc("name"), Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("createdAt은 따로 허용하지 않아도 정렬할 수 있다")
    void createdAtAlwaysAllowed() {
        Pageable result = PagePolicy.of().apply(PageRequest.of(0, 10, Sort.by("createdAt")));

        assertThat(result.getSort()).containsExactly(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("요청에 id 정렬이 이미 있으면 보조 정렬을 또 붙이지 않는다")
    void doesNotDuplicateTieBreaker() {
        Pageable result = PagePolicy.of("id").apply(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(result.getSort()).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    @DisplayName("허용하지 않은 필드로 정렬하면 INVALID_SORT_PROPERTY")
    void rejectsUnknownSortProperty() {
        assertThatThrownBy(() -> POLICY.apply(PageRequest.of(0, 10, Sort.by("password"))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_SORT_PROPERTY);
                    assertThat(e.getMessage()).contains("password");
                });
    }

    @Test
    @DisplayName("toQuery: apply와 같은 규칙으로 정규화한 결과를 PageQuery로 돌려준다 (size 보정, 정렬 유지, id 보조 정렬)")
    void toQueryNormalizesLikeApply() {
        Sort requested = Sort.by(Sort.Order.desc("name"), Sort.Order.asc("createdAt"));

        PageQuery query = POLICY.toQuery(PageRequest.of(1, 500, requested));

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(PagePolicy.DEFAULT_SIZE);
        assertThat(query.orderList()).containsExactly(
                new PageOrder("name", false), new PageOrder("createdAt", true), new PageOrder("id", true));
    }

    @Test
    @DisplayName("toQuery: 정렬이 없거나 unpaged면 0페이지, 10건, createdAt DESC + id ASC")
    void toQueryDefaults() {
        for (Pageable pageable : new Pageable[]{null, Pageable.unpaged(), PageRequest.of(0, 10)}) {
            PageQuery query = POLICY.toQuery(pageable);

            assertThat(query.page()).isZero();
            assertThat(query.size()).isEqualTo(PagePolicy.DEFAULT_SIZE);
            assertThat(query.orderList()).containsExactly(
                    new PageOrder("createdAt", false), new PageOrder("id", true));
        }
    }

    @Test
    @DisplayName("toQuery: 허용하지 않은 필드로 정렬하면 INVALID_SORT_PROPERTY")
    void toQueryRejectsUnknownSortProperty() {
        assertThatThrownBy(() -> POLICY.toQuery(PageRequest.of(0, 10, Sort.by("password"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_SORT_PROPERTY));
    }
}
