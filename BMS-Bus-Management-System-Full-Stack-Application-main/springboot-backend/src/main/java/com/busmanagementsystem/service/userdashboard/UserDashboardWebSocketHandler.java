package com.busmanagementsystem.service.userdashboard;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.busmanagementsystem.dto.userdashboard.UserDashboardResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class UserDashboardWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public UserDashboardWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcastDashboardUpdate(UserDashboardResponseDto payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(jsonPayload);
            sessions.removeIf(session -> !sendMessage(session, message));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize dashboard websocket payload", exception);
        }
    }

    private boolean sendMessage(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            return false;
        }

        try {
            session.sendMessage(message);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
