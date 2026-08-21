package com.fitmeet.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatRoomReadStateTest {

    @Test
    void markAsReadMovesCursorForward() {
        ChatRoomReadState readState = ChatRoomReadState.create(1L, 10L, 5L);

        readState.markAsRead(10L);

        assertThat(readState.getLastReadMessageId()).isEqualTo(10L);
        assertThat(readState.getLastReadAt()).isNotNull();
    }

    @Test
    void markAsReadDoesNotMoveCursorBackward() {
        ChatRoomReadState readState = ChatRoomReadState.create(1L, 10L, 10L);

        readState.markAsRead(5L);

        assertThat(readState.getLastReadMessageId()).isEqualTo(10L);
    }
}
