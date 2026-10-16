package com.common.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Slack 채널에 보낼 알림 한 건 (NTF, 설계 3.2).
 * 잘못 만들면 생성 시점에 IllegalArgumentException을 던진다(P5). 발송({@link SlackNotifier#send})은 예외를 던지지 않는다.
 * 개인정보(연락처·주소·자유 입력 원문)는 넣지 않는다. 무엇을 넣을지는 호출하는 서비스가 책임진다.
 *
 * @param title          제목. 필수
 * @param fields         "라벨: 값" 목록, 최대 {@value #MAX_FIELDS}개
 * @param mentionSlackId 멘션할 Slack 멤버 ID. null이면 멘션 없음. 형식이 맞지 않으면 렌더러가 생략한다
 * @param reference      로그 추적용 값(예: 주문 ID). Slack으로는 보내지 않는다
 */
public record SlackMessage(String title, List<SlackField> fields, String mentionSlackId, String reference) {

    public static final int MAX_FIELDS = 10;

    public SlackMessage {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title은 비어 있을 수 없습니다");
        }
        fields = fields == null ? List.of() : copyOf(fields);
        if (fields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("fields는 최대 " + MAX_FIELDS + "개입니다: " + fields.size());
        }
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    private static List<SlackField> copyOf(List<SlackField> fields) {
        if (fields.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("fields에 null 항목이 있습니다");
        }
        return List.copyOf(fields);
    }

    public static final class Builder {

        private final String title;
        private final List<SlackField> fields = new ArrayList<>();
        private String mentionSlackId;
        private String reference;

        private Builder(String title) {
            this.title = title;
        }

        public Builder field(String label, String value) {
            fields.add(new SlackField(label, value));
            return this;
        }

        public Builder mention(String slackId) {
            this.mentionSlackId = slackId;
            return this;
        }

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public SlackMessage build() {
            return new SlackMessage(title, fields, mentionSlackId, reference);
        }
    }
}
