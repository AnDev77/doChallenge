package com.fitmeet.chat.presentation.websocket.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebSocketChatMessageRequest(
        @NotBlank @Size(max = 1000) String content
) {
}
