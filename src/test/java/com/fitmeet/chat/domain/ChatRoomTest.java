package com.fitmeet.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
}
