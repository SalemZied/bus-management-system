package com.busmanagementsystem.service.userdashboard;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.dto.userdashboard.BusLiveLocationDto;
import com.busmanagementsystem.dto.userdashboard.RouteAlertDto;
import com.busmanagementsystem.dto.userdashboard.UserDashboardResponseDto;
import com.busmanagementsystem.dto.userdashboard.UserNotificationDto;

@Service
public class UserDashboardService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    @Value("${app.maps.provider-url:https://tile.openstreetmap.org/{z}/{x}/{y}.png}")
    private String mapProviderUrl;

    @Value("${app.maps.api-key:}")
    private String mapApiKey;

    public UserDashboardResponseDto getDashboardData() {
        int minuteOffset = Instant.now().atZone(ZoneOffset.UTC).getMinute() % 4;

        List<BusLiveLocationDto> buses = List.of(
                new BusLiveLocationDto("BMS-101", "Downtown Loop", "Central Station", 40.7194, -74.0060, 3 + minuteOffset,
                        "On time", "5th Ave & Pine St"),
                new BusLiveLocationDto("BMS-204", "University Express", "North Campus", 40.7298, -73.9972,
                        7 + minuteOffset, "Delayed", "City Library"),
                new BusLiveLocationDto("BMS-315", "Airport Connector", "Airport Terminal 2", 40.7033, -74.0170,
                        11 + minuteOffset, "On time", "Harbor Blvd"));

        List<RouteAlertDto> alerts = List.of(
                new RouteAlertDto("University Express", "North Campus", "Delay",
                        "Traffic congestion near Metro Bridge. Expect +6 minutes."),
                new RouteAlertDto("Airport Connector", "Airport Terminal 2", "Diversion",
                        "Temporary diversion via Harbor Road due to maintenance."));

        List<UserNotificationDto> notifications = List.of(
                new UserNotificationDto("info", "Bus approaching",
                        "BMS-101 will reach 5th Ave & Pine St in about " + (3 + minuteOffset) + " minutes.",
                        DATE_FORMATTER.format(Instant.now())),
                new UserNotificationDto("warning", "Route update",
                        "University Express has minor delays because of heavy traffic.",
                        DATE_FORMATTER.format(Instant.now().minusSeconds(180))),
                new UserNotificationDto("success", "Trip reminder",
                        "Your usual route to Central Station is running on schedule.",
                        DATE_FORMATTER.format(Instant.now().minusSeconds(480))));

        return new UserDashboardResponseDto(
                DATE_FORMATTER.format(Instant.now()),
                mapProviderUrl,
                mapApiKey,
                buses,
                alerts,
                notifications);
    }
}
