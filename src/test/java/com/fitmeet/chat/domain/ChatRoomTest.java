package com.fitmeet.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ChatRoomTest {

    @Test
    void meetupChatRoomIsActive() {
        ChatRoom chatRoom = ChatRoom.createMeetupRoom(1L);

        assertThat(chatRoom.getMeetupId()).isEqualTo(1L);
        assertThat(chatRoom.getType()).isEqualTo(ChatRoomType.MEETUP);
        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(chatRoom.getLastMessageId()).isNull();
        assertThat(chatRoom.getCreatedAt()).isNotNull();
    }

    @Test
    void chatRoomRecordsLastMessageSummary() {
        ChatRoom chatRoom = ChatRoom.createMeetupRoom(1L);
        LocalDateTime createdAt = LocalDateTime.now();

        chatRoom.recordLastMessage(10L, "hello", createdAt);

        assertThat(chatRoom.getLastMessageId()).isEqualTo(10L);
        assertThat(chatRoom.getLastMessageContent()).isEqualTo("hello");
        assertThat(chatRoom.getLastMessageAt()).isEqualTo(createdAt);
    }
}
