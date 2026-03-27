package com.busmanagementsystem.dto.userdashboard;

import java.util.List;

public class UserDashboardResponseDto {
    private String lastUpdated;
    private String mapProviderUrl;
    private String mapApiKey;
    private List<BusLiveLocationDto> buses;
    private List<RouteAlertDto> alerts;
    private List<UserNotificationDto> notifications;

    public UserDashboardResponseDto(String lastUpdated, String mapProviderUrl, String mapApiKey,
            List<BusLiveLocationDto> buses, List<RouteAlertDto> alerts, List<UserNotificationDto> notifications) {
        this.lastUpdated = lastUpdated;
        this.mapProviderUrl = mapProviderUrl;
        this.mapApiKey = mapApiKey;
        this.buses = buses;
        this.alerts = alerts;
        this.notifications = notifications;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public String getMapProviderUrl() {
        return mapProviderUrl;
    }

    public String getMapApiKey() {
        return mapApiKey;
    }

    public List<BusLiveLocationDto> getBuses() {
        return buses;
    }

    public List<RouteAlertDto> getAlerts() {
        return alerts;
    }

    public List<UserNotificationDto> getNotifications() {
        return notifications;
    }
}
