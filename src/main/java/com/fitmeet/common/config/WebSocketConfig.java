package com.fitmeet.common.config;

import com.fitmeet.common.security.ChatStompAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatStompAuthInterceptor chatStompAuthInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            ChatStompAuthInterceptor chatStompAuthInterceptor,
            @Value("${app.websocket.allowed-origin-patterns:http://localhost:*}") String allowedOrigins
    ) {
        this.chatStompAuthInterceptor = chatStompAuthInterceptor;
        this.allowedOriginPatterns = allowedOrigins.split(",");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatStompAuthInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
