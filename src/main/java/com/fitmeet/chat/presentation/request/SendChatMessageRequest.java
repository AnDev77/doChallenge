package com.fitmeet.chat.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotBlank @Size(max = 1000) String content
) {
}
