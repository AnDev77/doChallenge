package com.fitmeet.chat.presentation.websocket.response;

import com.fitmeet.chat.application.SendChatMessageResult;
import java.time.LocalDateTime;

public record ChatMessageCreatedEvent(
        String type,
        Long roomId,
        Long messageId,
        Long senderMemberId,
        String content,
        LocalDateTime createdAt
) {

    private static final String TYPE = "MESSAGE_CREATED";

    public static ChatMessageCreatedEvent from(SendChatMessageResult result) {
        return new ChatMessageCreatedEvent(
                TYPE,
                result.roomId(),
                result.messageId(),
                result.senderMemberId(),
                result.content(),
                result.createdAt()
        );
    }
}
