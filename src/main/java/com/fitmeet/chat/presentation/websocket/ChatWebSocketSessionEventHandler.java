package com.fitmeet.chat.presentation.websocket;

import com.fitmeet.chat.application.ChatRoomPresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class ChatWebSocketSessionEventHandler {

    private final ChatRoomPresenceService chatRoomPresenceService;

    public ChatWebSocketSessionEventHandler(ChatRoomPresenceService chatRoomPresenceService) {
        this.chatRoomPresenceService = chatRoomPresenceService;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        chatRoomPresenceService.disconnect(event.getSessionId());
    }
}
