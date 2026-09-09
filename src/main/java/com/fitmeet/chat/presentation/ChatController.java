package com.fitmeet.chat.presentation;

import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.chat.application.ChatMessageResult;
import com.fitmeet.chat.application.ChatService;
import com.fitmeet.chat.application.ReadChatRoomResult;
import com.fitmeet.chat.application.SendChatMessageResult;
import com.fitmeet.chat.presentation.request.SendChatMessageRequest;
import com.fitmeet.chat.presentation.response.ChatMessageResponse;
import com.fitmeet.chat.presentation.response.ReadChatRoomResponse;
import com.fitmeet.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{roomId}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long roomId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        SendChatMessageResult result = chatService.sendMessage(roomId, member.memberId(), request.content());
        return ApiResponse.success(ChatMessageResponse.from(result));
    }

    @GetMapping("/{roomId}/messages")
    public ApiResponse<List<ChatMessageResponse>> getMessages(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long cursorId
    ) {
        List<ChatMessageResult> results = chatService.getMessages(roomId, member.memberId(), cursorId);
        return ApiResponse.success(results.stream()
                .map(ChatMessageResponse::from)
                .toList());
    }

    @PatchMapping("/{roomId}/read")
    public ApiResponse<ReadChatRoomResponse> markAsRead(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long roomId
    ) {
        ReadChatRoomResult result = chatService.markAsRead(roomId, member.memberId());
        return ApiResponse.success(ReadChatRoomResponse.from(result));
    }
}
