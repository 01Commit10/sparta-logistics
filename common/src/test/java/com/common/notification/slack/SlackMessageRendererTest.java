package com.common.notification.slack;

import com.common.notification.SlackMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlackMessageRendererTest {

    final SlackMessageRenderer renderer = new SlackMessageRenderer();

    @Test
    @DisplayName("2-1. 굵은 제목 줄 다음에 \"• 라벨: 값\" 줄을 순서대로 붙인다")
    void layout() {
        SlackMessage message = SlackMessage.builder("주문 완료")
                .field("주문 ID", "order-1")
                .field("상품", "생수 × 10")
                .build();

        assertThat(renderer.render(message)).isEqualTo("""
                *[주문 완료]*
                • 주문 ID: order-1
                • 상품: 생수 × 10""");
    }

    @Test
    @DisplayName("2-2. 제목·라벨·값의 & < > 를 이스케이프한다 (&를 먼저 바꿔 이중 변환하지 않음)")
    void escape() {
        SlackMessage message = SlackMessage.builder("<!channel> 공지")
                .field("A&B", "<@U07ABCDEF1> & <https://evil|클릭>")
                .build();

        assertThat(renderer.render(message)).isEqualTo("""
                *[&lt;!channel&gt; 공지]*
                • A&amp;B: &lt;@U07ABCDEF1&gt; &amp; &lt;https://evil|클릭&gt;""");
    }

    @Test
    @DisplayName("2-3. 길이 제한: 제목 100자, 라벨 50자, 값 200자를 넘으면 잘라서 …를 붙인다")
    void truncate() {
        SlackMessage message = SlackMessage.builder("t".repeat(101))
                .field("l".repeat(51), "v".repeat(201))
                .field("짧은 라벨", "v".repeat(200))
                .build();

        String[] lines = renderer.render(message).split("\n");

        assertThat(lines[0]).isEqualTo("*[" + "t".repeat(100) + "…]*");
        assertThat(lines[1]).isEqualTo("• " + "l".repeat(50) + "…: " + "v".repeat(200) + "…");
        assertThat(lines[2]).isEqualTo("• 짧은 라벨: " + "v".repeat(200));
    }

    @Test
    @DisplayName("2-3. 길이는 이스케이프 전 글자 수로 세고, 이모지를 반으로 자르지 않는다")
    void truncateByCodePointBeforeEscape() {
        String value = "&".repeat(200) + "x";
        String emojis = "😀".repeat(201);
        SlackMessage message = SlackMessage.builder("주문 완료")
                .field("기호", value)
                .field("이모지", emojis)
                .build();

        String[] lines = renderer.render(message).split("\n");

        assertThat(lines[1]).isEqualTo("• 기호: " + "&amp;".repeat(200) + "…");
        assertThat(lines[2]).isEqualTo("• 이모지: " + "😀".repeat(200) + "…");
    }

    @Test
    @DisplayName("2-4. 멘션이 Slack 멤버 ID 형식이면 제목 뒤에 <@ID>를 붙인다")
    void mention() {
        SlackMessage user = SlackMessage.builder("주문 완료").mention("U07ABCDEF1").build();
        SlackMessage workspaceUser = SlackMessage.builder("주문 완료").mention("W0123456789").build();

        assertThat(renderer.render(user)).isEqualTo("*[주문 완료]* <@U07ABCDEF1>");
        assertThat(renderer.render(workspaceUser)).isEqualTo("*[주문 완료]* <@W0123456789>");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"hong", "u07abcdef1", "U07ABC", "<!here>", "U07ABCDEF1>", "U07ABCDEF1 ", "B07ABCDEF1", "U07ABCDEF1\n"})
    @DisplayName("2-5. 멘션이 멤버 ID 형식이 아니면 멘션 없이 렌더링한다")
    void invalidMention(String slackId) {
        SlackMessage message = SlackMessage.builder("주문 완료").mention(slackId).build();

        assertThat(renderer.render(message)).isEqualTo("*[주문 완료]*");
    }

    @Test
    @DisplayName("2-6. 필드가 없으면 제목 줄만")
    void titleOnly() {
        assertThat(renderer.render(SlackMessage.builder("주문 완료").build())).isEqualTo("*[주문 완료]*");
    }

    @Test
    @DisplayName("2-7. 제목·라벨·값의 줄바꿈은 공백으로 바꿔 가짜 줄을 만들지 못하게 한다")
    void newlines() {
        SlackMessage message = SlackMessage.builder("주문\n완료")
                .field("업체\r\n명", "A상사\n• 연락처: 010-0000-0000")
                .build();

        assertThat(renderer.render(message)).isEqualTo("""
                *[주문 완료]*
                • 업체 명: A상사 • 연락처: 010-0000-0000""");
    }
}
