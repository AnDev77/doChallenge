package com.fitmeet.chat.presentation.websocket;

import com.fitmeet.chat.application.ChatService;
import com.fitmeet.chat.application.ReadChatRoomResult;
import com.fitmeet.chat.application.SendChatMessageResult;
import com.fitmeet.chat.presentation.websocket.request.WebSocketChatMessageRequest;
import com.fitmeet.chat.presentation.websocket.request.WebSocketReadChatRoomRequest;
import com.fitmeet.chat.presentation.websocket.response.ChatMessageCreatedEvent;
import com.fitmeet.chat.presentation.websocket.response.ChatRoomReadEvent;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Valid WebSocketChatMessageRequest request
    ) {
        SendChatMessageResult result = chatService.sendMessage(roomId, request.memberId(), request.content());
        messagingTemplate.convertAndSend(
                "/topic/chat/rooms/" + roomId,
                ChatMessageCreatedEvent.from(result)
        );
    }

    @MessageMapping("/chat/rooms/{roomId}/read")
    public void markAsRead(
            @DestinationVariable Long roomId,
            @Valid WebSocketReadChatRoomRequest request
    ) {
        ReadChatRoomResult result = chatService.markAsRead(roomId, request.memberId());
        messagingTemplate.convertAndSend(
                "/topic/chat/rooms/" + roomId + "/reads",
                ChatRoomReadEvent.from(result)
        );
    }
}
