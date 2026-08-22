package com.fitmeet.chat.presentation.websocket.request;

import jakarta.validation.constraints.NotNull;

public record WebSocketReadChatRoomRequest(
        @NotNull Long memberId
) {
}
