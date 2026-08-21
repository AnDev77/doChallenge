package com.fitmeet.chat.application;

import java.time.LocalDateTime;

public record SendChatMessageResult(
        Long messageId,
        Long roomId,
        Long senderMemberId,
        String content,
        LocalDateTime createdAt
) {
}
