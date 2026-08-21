package com.fitmeet.chat.application;

import java.time.LocalDateTime;

public record ReadChatRoomResult(
        Long roomId,
        Long memberId,
        Long lastReadMessageId,
        LocalDateTime lastReadAt
) {
}
