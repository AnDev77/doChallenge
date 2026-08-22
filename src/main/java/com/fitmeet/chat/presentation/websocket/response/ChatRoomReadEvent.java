package com.fitmeet.chat.presentation.websocket.response;

import com.fitmeet.chat.application.ReadChatRoomResult;
import java.time.LocalDateTime;

public record ChatRoomReadEvent(
        String type,
        Long roomId,
        Long memberId,
        Long lastReadMessageId,
        LocalDateTime lastReadAt
) {

    private static final String TYPE = "ROOM_READ";

    public static ChatRoomReadEvent from(ReadChatRoomResult result) {
        return new ChatRoomReadEvent(
                TYPE,
                result.roomId(),
                result.memberId(),
                result.lastReadMessageId(),
                result.lastReadAt()
        );
    }
}
