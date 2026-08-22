package com.fitmeet.chat.presentation.websocket.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WebSocketChatMessageRequest(
        @NotNull Long memberId,
        @NotBlank @Size(max = 1000) String content
) {
}
