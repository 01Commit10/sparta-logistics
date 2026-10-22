package com.common.notification.slack;

import com.common.notification.SlackField;
import com.common.notification.SlackMessage;

import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * {@link SlackMessage}를 Slack mrkdwn 본문으로 만든다 (N14, P4·P6).
 * 호출하는 서비스가 넣은 값은 모두 평문으로 취급한다: 줄바꿈 → 공백, 길이 제한, {@code & < >} 이스케이프.
 * 그래서 값으로 {@code <!channel>}·링크·가짜 줄을 끼워 넣을 수 없고, 멘션은 이 클래스만 만든다.
 */
final class SlackMessageRenderer {

    static final int MAX_TITLE = 100;
    static final int MAX_LABEL = 50;
    static final int MAX_VALUE = 200;

    /** Slack 멤버 ID (예: U07ABCDEF1, W0123456789). */
    private static final Pattern MEMBER_ID = Pattern.compile("[UW][A-Z0-9]{8,}");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    String render(SlackMessage message) {
        StringJoiner lines = new StringJoiner("\n");
        lines.add("*[" + plain(message.title(), MAX_TITLE) + "]*" + mention(message.mentionSlackId()));
        for (SlackField field : message.fields()) {
            lines.add("• " + plain(field.label(), MAX_LABEL) + ": " + plain(field.value(), MAX_VALUE));
        }
        return lines.toString();
    }

    private static String mention(String slackId) {
        if (slackId == null || !MEMBER_ID.matcher(slackId).matches()) {
            return "";
        }
        return " <@" + slackId + ">";
    }

    /** 줄바꿈 제거 → 글자 수(code point) 기준 자르기 → 이스케이프. 이스케이프 후에 자르면 &amp; 같은 엔티티가 깨진다. */
    private static String plain(String text, int maxLength) {
        return escape(truncate(LINE_BREAK.matcher(text).replaceAll(" "), maxLength));
    }

    private static String truncate(String text, int maxLength) {
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength)) + "…";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
