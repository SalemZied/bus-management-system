package com.busmanagementsystem.service.userdashboard;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.dto.userdashboard.BusLiveLocationDto;
import com.busmanagementsystem.dto.userdashboard.RouteAlertDto;
import com.busmanagementsystem.dto.userdashboard.UserDashboardResponseDto;
import com.busmanagementsystem.dto.userdashboard.UserNotificationDto;
import com.busmanagementsystem.entity.Bus;
import com.busmanagementsystem.repository.BusRepository;

@Service
public class UserDashboardService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    @Value("${app.maps.provider-url:https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png}")
    private String mapProviderUrl;

    @Value("${app.maps.api-key:}")
    private String mapApiKey;

    private final BusRepository busRepository;

    public UserDashboardService(BusRepository busRepository) {
        this.busRepository = busRepository;
    }

    public UserDashboardResponseDto getDashboardData() {
        int minuteOffset = Instant.now().atZone(ZoneOffset.UTC).getMinute() % 4;

        List<BusLiveLocationDto> buses = buildBusesFromDataset(minuteOffset);
        List<RouteAlertDto> alerts = List.of(
                new RouteAlertDto("University Express", "North Campus", "Delay",
                        "Traffic congestion near Metro Bridge. Expect +6 minutes."),
                new RouteAlertDto("Airport Connector", "Airport Terminal 2", "Diversion",
                        "Temporary diversion via Harbor Road due to maintenance."));

        List<UserNotificationDto> notifications = List.of(
                new UserNotificationDto("info", "Bus approaching",
                        "The next bus will arrive in about " + buses.get(0).getEtaMinutes() + " minutes.",
                        DATE_FORMATTER.format(Instant.now())),
                new UserNotificationDto("warning", "Route update",
                        "University Express has minor delays because of heavy traffic.",
                        DATE_FORMATTER.format(Instant.now().minusSeconds(180))),
                new UserNotificationDto("success", "Trip reminder",
                        "Your usual route is operating normally.",
                        DATE_FORMATTER.format(Instant.now().minusSeconds(480))));

        return new UserDashboardResponseDto(
                DATE_FORMATTER.format(Instant.now()),
                mapProviderUrl,
                mapApiKey,
                buses,
                alerts,
                notifications);
    }

    private List<BusLiveLocationDto> buildBusesFromDataset(int minuteOffset) {
        List<Bus> datasetBuses = busRepository.findAll().stream()
                .sorted(Comparator.comparing(Bus::getBusNumber, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        if (datasetBuses.isEmpty()) {
            return List.of(
                    new BusLiveLocationDto("BMS-101", "Downtown Loop", "Central Station", 40.7194, -74.0060,
                            3 + minuteOffset, "On time", "5th Ave & Pine St"),
                    new BusLiveLocationDto("BMS-204", "University Express", "North Campus", 40.7298, -73.9972,
                            7 + minuteOffset, "Delayed", "City Library"),
                    new BusLiveLocationDto("BMS-315", "Airport Connector", "Airport Terminal 2", 40.7033, -74.0170,
                            11 + minuteOffset, "On time", "Harbor Blvd"));
        }

        List<BusLiveLocationDto> mappedBuses = new ArrayList<>();
        double centerLat = 40.7228;
        double centerLon = -74.0045;

        for (int i = 0; i < datasetBuses.size(); i++) {
            Bus bus = datasetBuses.get(i);
            double latitude = centerLat + (Math.sin(i * 1.7) * 0.018);
            double longitude = centerLon + (Math.cos(i * 1.7) * 0.018);
            String busNumber = bus.getBusNumber() == null || bus.getBusNumber().isBlank()
                    ? "BUS-" + bus.getId()
                    : bus.getBusNumber();
            String busName = bus.getBusName() == null || bus.getBusName().isBlank()
                    ? "City Service"
                    : bus.getBusName();

            mappedBuses.add(new BusLiveLocationDto(
                    busNumber,
                    busName,
                    "Main Terminal",
                    latitude,
                    longitude,
                    3 + minuteOffset + (i % 10),
                    (i % 4 == 0) ? "Delayed" : "On time",
                    (i % 3 == 0) ? "Central Station" : "Downtown Stop"));
        }

        return mappedBuses;
    }
}
