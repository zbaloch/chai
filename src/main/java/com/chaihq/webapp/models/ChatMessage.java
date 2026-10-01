package com.chaihq.webapp.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// What the browser sends over the WebSocket to post in a chat. The sender is never taken from here.
@NoArgsConstructor
@Getter
@Setter
public class ChatMessage {
    private String roomId;
    private String content;
}
