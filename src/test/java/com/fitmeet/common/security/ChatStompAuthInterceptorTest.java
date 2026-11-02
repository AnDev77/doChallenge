package com.fitmeet.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.auth.domain.TokenProvider;
import com.fitmeet.chat.application.ChatService;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ChatStompAuthInterceptorTest {

    private final TokenProvider tokens = mock(TokenProvider.class);
    private final ChatService chat = mock(ChatService.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final ChatStompAuthInterceptor interceptor = new ChatStompAuthInterceptor(tokens, chat);
    private final AuthenticatedMember member = new AuthenticatedMember(7L, "member@example.com", MemberRole.USER);

    @BeforeEach
    void setup() {
        when(tokens.parseAccessToken("valid")).thenReturn(member);
    }

    @Test
    void connectRequiresTokenAndSetsServerVerifiedPrincipal() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, false), channel))
                .isInstanceOf(IllegalArgumentException.class);
        Message<?> accepted = interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer valid", false), channel);
        var user = StompHeaderAccessor.wrap(accepted).getUser();
        assertThat(user).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((UsernamePasswordAuthenticationToken) user).getPrincipal()).isEqualTo(member);
    }

    @Test
    void subscriptionChecksMembershipAndRejectsAnonymousOrForeignDestinations() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/chat/rooms/5", null, false), channel))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/system", null, true), channel))
                .isInstanceOf(IllegalArgumentException.class);
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/chat/rooms/5/reads", null, true), channel);
        verify(chat).requireRoomAccess(5L, 7L);
    }

    @Test
    void subscriptionRejectsAuthenticatedNonMember() {
        doThrow(new BaseException(ErrorCode.CHAT_ROOM_ACCESS_DENIED))
                .when(chat).requireRoomAccess(6L, 7L);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/chat/rooms/6", null, true), channel))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void sendCannotPublishToBrokerAndChecksRoomAccess() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SEND, "/topic/chat/rooms/5", null, true), channel))
                .isInstanceOf(IllegalArgumentException.class);
        interceptor.preSend(frame(StompCommand.SEND, "/app/chat/rooms/5/messages", null, true), channel);
        verify(chat).requireRoomAccess(5L, 7L);
    }

    private Message<byte[]> frame(StompCommand command, String destination, String authorization, boolean loggedIn) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (loggedIn) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(member, null));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
