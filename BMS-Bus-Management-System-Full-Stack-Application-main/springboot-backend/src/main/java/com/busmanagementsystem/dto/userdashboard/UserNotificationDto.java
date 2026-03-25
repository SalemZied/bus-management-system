package com.busmanagementsystem.dto.userdashboard;

public class UserNotificationDto {
    private String level;
    private String title;
    private String message;
    private String createdAt;

    public UserNotificationDto(String level, String title, String message, String createdAt) {
        this.level = level;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
    }

    public String getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
