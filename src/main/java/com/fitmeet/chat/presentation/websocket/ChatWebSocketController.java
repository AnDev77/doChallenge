package com.fitmeet.chat.presentation.websocket;

import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.chat.application.ChatRoomPresenceService;
import com.fitmeet.chat.application.ChatService;
import com.fitmeet.chat.application.ReadChatRoomResult;
import com.fitmeet.chat.application.SendChatMessageResult;
import com.fitmeet.chat.presentation.websocket.request.WebSocketChatMessageRequest;
import com.fitmeet.chat.presentation.websocket.response.ChatMessageCreatedEvent;
import com.fitmeet.chat.presentation.websocket.response.ChatRoomReadEvent;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final ChatRoomPresenceService chatRoomPresenceService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(
            ChatService chatService,
            ChatRoomPresenceService chatRoomPresenceService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.chatService = chatService;
        this.chatRoomPresenceService = chatRoomPresenceService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/rooms/{roomId}/enter")
    public void enterRoom(
            @Header("simpSessionId") String sessionId,
            @DestinationVariable Long roomId,
            Principal user
    ) {
        chatRoomPresenceService.enter(sessionId, roomId, memberId(user));
    }

    @MessageMapping("/chat/rooms/{roomId}/leave")
    public void leaveRoom(
            @Header("simpSessionId") String sessionId,
            @DestinationVariable Long roomId,
            Principal user
    ) {
        chatRoomPresenceService.leave(sessionId, roomId, memberId(user));
    }

    @MessageMapping("/chat/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Valid WebSocketChatMessageRequest request,
            Principal user
    ) {
        SendChatMessageResult result = chatService.sendMessage(roomId, memberId(user), request.content());
        messagingTemplate.convertAndSend(
                "/topic/chat/rooms/" + roomId,
                ChatMessageCreatedEvent.from(result)
        );
    }

    @MessageMapping("/chat/rooms/{roomId}/read")
    public void markAsRead(
            @DestinationVariable Long roomId,
            Principal user
    ) {
        ReadChatRoomResult result = chatService.markAsRead(roomId, memberId(user));
        messagingTemplate.convertAndSend(
                "/topic/chat/rooms/" + roomId + "/reads",
                ChatRoomReadEvent.from(result)
        );
    }

    private Long memberId(Principal user) {
        if (!(user instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
            throw new IllegalArgumentException("Chat authentication is required");
        }
        return member.memberId();
    }
}
