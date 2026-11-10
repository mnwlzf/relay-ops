package com.relayops.integration.onebot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OneBotEventTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String body) throws Exception {
        return JSON.readTree(body);
    }

    @Test
    void parsesGroupMessageWithAtSegment() throws Exception {
        OneBotEvent event = OneBotEvent.parse(json("""
                {"post_type":"message","message_type":"group","group_id":10086,"user_id":42,
                 "self_id":999,"raw_message":"[CQ:at,qq=999] 今天号池怎么样",
                 "sender":{"nickname":"小明"},
                 "message":[{"type":"at","data":{"qq":"999"}},{"type":"text","data":{"text":"今天号池怎么样"}}]}
                """));

        assertThat(event).isNotNull();
        assertThat(event.isGroup()).isTrue();
        assertThat(event.groupId()).isEqualTo(10086L);
        assertThat(event.atMe()).isTrue();
        assertThat(event.cleanText()).isEqualTo("今天号池怎么样");
        assertThat(event.nickname()).isEqualTo("小明");
    }

    @Test
    void detectsAtFromRawMessageWhenSegmentsAreMissing() throws Exception {
        OneBotEvent event = OneBotEvent.parse(json("""
                {"post_type":"message","message_type":"group","group_id":1,"user_id":2,
                 "self_id":999,"raw_message":"[CQ:at,qq=999] hi","sender":{}}
                """));

        assertThat(event.atMe()).isTrue();
        assertThat(event.cleanText()).isEqualTo("hi");
    }

    @Test
    void marksSelfMessagesSoTheyAreNeverAnswered() throws Exception {
        OneBotEvent event = OneBotEvent.parse(json("""
                {"post_type":"message","message_type":"private","user_id":999,"self_id":999,
                 "raw_message":"我发的","sender":{}}
                """));

        assertThat(event.fromSelf()).isTrue();
    }

    @Test
    void ignoresNonMessagePosts() throws Exception {
        assertThat(OneBotEvent.parse(json("{\"post_type\":\"notice\",\"notice_type\":\"group_recall\"}")))
                .isNull();
        assertThat(OneBotEvent.parse(json("{\"post_type\":\"message\",\"message_type\":\"guild\"}")))
                .isNull();
    }

    @Test
    void fallsBackToCardThenUserIdForTheSpeakerName() throws Exception {
        OneBotEvent card = OneBotEvent.parse(json("""
                {"post_type":"message","message_type":"group","group_id":1,"user_id":42,"self_id":999,
                 "raw_message":"hi","sender":{"card":"群名片","nickname":"昵称"}}
                """));
        OneBotEvent noName = OneBotEvent.parse(json("""
                {"post_type":"message","message_type":"group","group_id":1,"user_id":42,"self_id":999,
                 "raw_message":"hi","sender":{}}
                """));

        assertThat(card.nickname()).isEqualTo("群名片");
        assertThat(noName.nickname()).isEqualTo("42");
    }
}
