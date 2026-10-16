package com.common.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlackMessageTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("1-1. 제목이 없거나 공백이면 만들 때 IllegalArgumentException")
    void blankTitle(String title) {
        assertThatThrownBy(() -> new SlackMessage(title, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    @DisplayName("1-2. fields가 null이면 빈 목록")
    void nullFields() {
        SlackMessage message = new SlackMessage("주문 완료", null, null, null);

        assertThat(message.fields()).isEmpty();
    }

    @Test
    @DisplayName("1-3. fields가 10개를 넘으면 IllegalArgumentException, 10개는 허용")
    void tooManyFields() {
        assertThat(new SlackMessage("주문 완료", fields(10), null, null).fields()).hasSize(10);

        assertThatThrownBy(() -> new SlackMessage("주문 완료", fields(11), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
    }

    @Test
    @DisplayName("1-4. 넘긴 목록을 나중에 바꿔도 메시지는 바뀌지 않고, 메시지의 목록은 수정 불가")
    void immutableFields() {
        List<SlackField> source = new ArrayList<>(fields(2));
        SlackMessage message = new SlackMessage("주문 완료", source, null, null);

        source.add(new SlackField("추가", "값"));

        assertThat(message.fields()).hasSize(2);
        assertThatThrownBy(() -> message.fields().add(new SlackField("추가", "값")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("1-4. fields 안에 null 항목이 있으면 IllegalArgumentException")
    void nullFieldElement() {
        List<SlackField> withNull = new ArrayList<>();
        withNull.add(null);

        assertThatThrownBy(() -> new SlackMessage("주문 완료", withNull, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("1-5. SlackField 라벨이 없거나 공백이면 IllegalArgumentException")
    void blankLabel(String label) {
        assertThatThrownBy(() -> new SlackField(label, "값"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    @Test
    @DisplayName("1-5. SlackField 값이 null이면 \"-\"로 표시")
    void nullValue() {
        assertThat(new SlackField("허브", null).value()).isEqualTo("-");
    }

    @Test
    @DisplayName("1-6. builder로 제목·필드·멘션·참조를 조립한다")
    void builder() {
        SlackMessage message = SlackMessage.builder("주문 완료")
                .field("상품", "생수 × 10")
                .field("허브", null)
                .mention("U07ABCDEF1")
                .reference("order-1")
                .build();

        assertThat(message.title()).isEqualTo("주문 완료");
        assertThat(message.fields()).containsExactly(
                new SlackField("상품", "생수 × 10"),
                new SlackField("허브", "-"));
        assertThat(message.mentionSlackId()).isEqualTo("U07ABCDEF1");
        assertThat(message.reference()).isEqualTo("order-1");
    }

    @Test
    @DisplayName("1-6. builder에서도 필드 11개는 build 시점에 IllegalArgumentException")
    void builderTooManyFields() {
        SlackMessage.Builder builder = SlackMessage.builder("주문 완료");
        IntStream.range(0, 11).forEach(i -> builder.field("라벨" + i, "값"));

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<SlackField> fields(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new SlackField("라벨" + i, "값" + i))
                .toList();
    }
}
