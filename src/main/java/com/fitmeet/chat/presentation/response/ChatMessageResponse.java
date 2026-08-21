package com.fitmeet.chat.presentation.response;

import com.fitmeet.chat.application.ChatMessageResult;
import com.fitmeet.chat.application.SendChatMessageResult;
import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long messageId,
        Long roomId,
        Long senderMemberId,
        String content,
        LocalDateTime createdAt
) {

    public static ChatMessageResponse from(SendChatMessageResult result) {
        return new ChatMessageResponse(
                result.messageId(),
                result.roomId(),
                result.senderMemberId(),
                result.content(),
                result.createdAt()
        );
    }

    public static ChatMessageResponse from(ChatMessageResult result) {
        return new ChatMessageResponse(
                result.messageId(),
                result.roomId(),
                result.senderMemberId(),
                result.content(),
                result.createdAt()
        );
    }
}
