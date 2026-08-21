package com.fitmeet.chat.application;

import com.fitmeet.chat.domain.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageResult(
        Long messageId,
        Long roomId,
        Long senderMemberId,
        String content,
        LocalDateTime createdAt
) {

    public static ChatMessageResult from(ChatMessage message) {
        return new ChatMessageResult(
                message.getId(),
                message.getRoomId(),
                message.getSenderMemberId(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
