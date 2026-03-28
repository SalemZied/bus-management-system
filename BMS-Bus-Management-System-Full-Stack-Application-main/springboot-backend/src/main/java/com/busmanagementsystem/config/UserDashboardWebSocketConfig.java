package com.busmanagementsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.busmanagementsystem.service.userdashboard.UserDashboardWebSocketHandler;

@Configuration
@EnableWebSocket
public class UserDashboardWebSocketConfig implements WebSocketConfigurer {

    private final UserDashboardWebSocketHandler userDashboardWebSocketHandler;

    public UserDashboardWebSocketConfig(UserDashboardWebSocketHandler userDashboardWebSocketHandler) {
        this.userDashboardWebSocketHandler = userDashboardWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userDashboardWebSocketHandler, "/ws/user-dashboard")
                .setAllowedOrigins("http://localhost:3000");
    }
}
