package com.chaihq.webapp.config;

import com.chaihq.webapp.services.Chats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/(\\d+)$");

    // Each person's own feed of "something new in a chat", which lights the dots
    private static final String SIGNALS = "/user/queue/chats";

    private final Chats chats;

    // Only pages served from this site may open a chat connection
    @Value("${host.url:http://localhost:8080}")
    private String hostUrl;

    // Lazy: Chats sends through the broker this class configures
    public WebSocketConfig(@Lazy Chats chats) {
        this.chats = chats;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(hostUrl, "http://localhost:[*]").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }
                Principal principal = accessor.getUser();
                String destination = String.valueOf(accessor.getDestination());
                if (accessor.getCommand() == StompCommand.CONNECT && principal == null) {
                    throw new MessagingException("Sign in to use chat");
                }
                // Browsers only talk to the app, never straight to a topic (that would skip every check)
                if (accessor.getCommand() == StompCommand.SEND && !destination.startsWith("/app/")) {
                    throw new MessagingException("Not allowed to send to " + destination);
                }
                // A chat can only be followed by the people in it; everyone can follow their own signals
                if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                    if (principal != null && SIGNALS.equals(destination)) {
                        return message;
                    }
                    Matcher matcher = CHAT_TOPIC.matcher(destination);
                    Long roomId = matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
                    if (principal == null || roomId == null || !chats.canAccess(roomId, principal.getName())) {
                        throw new MessagingException("Not allowed to subscribe to " + destination);
                    }
                }
                return message;
            }
        });
    }
}
