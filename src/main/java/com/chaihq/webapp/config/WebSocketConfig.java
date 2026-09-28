package com.chaihq.webapp.config;

import com.chaihq.webapp.services.ProjectAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
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

    private static final Pattern PROJECT_TOPIC = Pattern.compile("^/topic/project/(\\d+)$");

    private final ProjectAccess projectAccess;

    // Only pages served from this site may open a chat connection
    @Value("${host.url:http://localhost:8080}")
    private String hostUrl;

    public WebSocketConfig(ProjectAccess projectAccess) {
        this.projectAccess = projectAccess;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(hostUrl, "http://localhost:[*]").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
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
                if (accessor.getCommand() == StompCommand.CONNECT && principal == null) {
                    throw new MessagingException("Sign in to use chat");
                }
                // A project's chat topic can only be followed by that project's people
                if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                    Matcher matcher = PROJECT_TOPIC.matcher(String.valueOf(accessor.getDestination()));
                    Long projectId = matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
                    if (principal == null || !projectAccess.isMember(projectId, principal.getName())) {
                        throw new MessagingException("Not allowed to subscribe to " + accessor.getDestination());
                    }
                }
                return message;
            }
        });
    }
}
