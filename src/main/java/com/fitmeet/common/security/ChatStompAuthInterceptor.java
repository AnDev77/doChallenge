package com.fitmeet.common.security;

import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.auth.domain.TokenProvider;
import com.fitmeet.chat.application.ChatService;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class ChatStompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern SEND_DESTINATION = Pattern.compile(
            "^/app/chat/rooms/([1-9][0-9]*)/(enter|leave|messages|read)$");
    private static final Pattern SUBSCRIBE_DESTINATION = Pattern.compile(
            "^/topic/chat/rooms/([1-9][0-9]*)(?:/reads)?$");

    private final TokenProvider tokenProvider;
    private final ChatService chatService;

    public ChatStompAuthInterceptor(TokenProvider tokenProvider, ChatService chatService) {
        this.tokenProvider = tokenProvider;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            throw new IllegalArgumentException("Invalid STOMP message");
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
                throw new IllegalArgumentException("A Bearer token is required for chat");
            }
            AuthenticatedMember member = tokenProvider.parseAccessToken(
                    authorization.substring(BEARER_PREFIX.length()));
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    member, null, List.of(new SimpleGrantedAuthority("ROLE_" + member.role().name()))));
            return message;
        }
        if (command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) {
            Principal user = accessor.getUser();
            if (!(user instanceof UsernamePasswordAuthenticationToken authentication)
                    || !(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
                throw new IllegalArgumentException("Chat authentication is required");
            }
            String destination = accessor.getDestination();
            Matcher matcher = (command == StompCommand.SEND
                    ? SEND_DESTINATION : SUBSCRIBE_DESTINATION).matcher(
                    destination == null ? "" : destination);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Chat destination is not allowed");
            }
            chatService.requireRoomAccess(Long.parseLong(matcher.group(1)), member.memberId());
        }
        return message;
    }
}
