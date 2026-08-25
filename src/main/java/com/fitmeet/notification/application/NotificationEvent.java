package com.fitmeet.notification.application;

import java.time.LocalDateTime;

public record NotificationEvent(
        String type,
        Long receiverMemberId,
        Long roomId,
        Long messageId,
        Long senderMemberId,
        String content,
        LocalDateTime occurredAt
) {

    public static NotificationEvent chatMessageCreated(
            Long receiverMemberId,
            Long roomId,
            Long messageId,
            Long senderMemberId,
            String content,
            LocalDateTime occurredAt
    ) {
        return new NotificationEvent(
                "CHAT_MESSAGE_CREATED",
                receiverMemberId,
                roomId,
                messageId,
                senderMemberId,
                content,
                occurredAt
        );
    }
}
