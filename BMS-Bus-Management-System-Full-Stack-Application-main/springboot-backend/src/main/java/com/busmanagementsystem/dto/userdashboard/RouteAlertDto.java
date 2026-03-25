package com.busmanagementsystem.dto.userdashboard;

public class RouteAlertDto {
    private String routeName;
    private String destination;
    private String alertType;
    private String message;

    public RouteAlertDto(String routeName, String destination, String alertType, String message) {
        this.routeName = routeName;
        this.destination = destination;
        this.alertType = alertType;
        this.message = message;
    }

    public String getRouteName() {
        return routeName;
    }

    public String getDestination() {
        return destination;
    }

    public String getAlertType() {
        return alertType;
    }

    public String getMessage() {
        return message;
    }
}
