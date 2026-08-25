package com.fitmeet.chat.application.event;

import java.time.LocalDateTime;

public record ChatMessageCreatedEvent(
        Long roomId,
        Long meetupId,
        Long messageId,
        Long senderMemberId,
        String content,
        LocalDateTime createdAt
) {
}
