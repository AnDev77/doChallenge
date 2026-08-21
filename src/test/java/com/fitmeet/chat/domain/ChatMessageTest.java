package com.fitmeet.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    void textMessageTrimsContent() {
        ChatMessage message = ChatMessage.text(1L, 10L, " hello ");

        assertThat(message.getRoomId()).isEqualTo(1L);
        assertThat(message.getSenderMemberId()).isEqualTo(10L);
        assertThat(message.getContent()).isEqualTo("hello");
        assertThat(message.getType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(message.getCreatedAt()).isNotNull();
    }

    @Test
    void textMessageCannotBeBlank() {
        assertThatThrownBy(() -> ChatMessage.text(1L, 10L, " "))
                .isInstanceOf(BaseException.class)
                .extracting(exception -> ((BaseException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHAT_MESSAGE);
    }
}
