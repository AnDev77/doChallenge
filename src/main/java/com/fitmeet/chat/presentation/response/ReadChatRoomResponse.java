package com.fitmeet.chat.presentation.response;

import com.fitmeet.chat.application.ReadChatRoomResult;
import java.time.LocalDateTime;

public record ReadChatRoomResponse(
        Long roomId,
        Long memberId,
        Long lastReadMessageId,
        LocalDateTime lastReadAt
) {

    public static ReadChatRoomResponse from(ReadChatRoomResult result) {
        return new ReadChatRoomResponse(
                result.roomId(),
                result.memberId(),
                result.lastReadMessageId(),
                result.lastReadAt()
        );
    }
}
