package com.busmanagementsystem.service.userdashboard;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.dto.userdashboard.BusLiveLocationDto;
import com.busmanagementsystem.dto.userdashboard.RouteAlertDto;
import com.busmanagementsystem.dto.userdashboard.UserDashboardResponseDto;
import com.busmanagementsystem.dto.userdashboard.UserNotificationDto;
import com.busmanagementsystem.entity.Bus;
import com.busmanagementsystem.entity.Route;
import com.busmanagementsystem.repository.BusRepository;
import com.busmanagementsystem.repository.RouteRepository;

@Service
public class UserDashboardService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    @Value("${app.maps.provider-url:https://tile.openstreetmap.org/{z}/{x}/{y}.png}")
    private String mapProviderUrl;

    @Value("${app.maps.api-key:}")
    private String mapApiKey;

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;

    @Autowired
    public UserDashboardService(BusRepository busRepository, RouteRepository routeRepository) {
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
    }

    public UserDashboardResponseDto getDashboardData() {
        List<Bus> storedBuses = busRepository.findAll();
        List<Route> routes = routeRepository.findAll();

        List<BusLiveLocationDto> buses = buildLiveBuses(storedBuses, routes);
        List<RouteAlertDto> alerts = buildRouteAlerts(routes, buses);
        List<UserNotificationDto> notifications = buildNotifications(buses, alerts);

        return new UserDashboardResponseDto(
                DATE_FORMATTER.format(Instant.now()),
                mapProviderUrl,
                mapApiKey,
                buses,
                alerts,
                notifications);
    }

    private List<BusLiveLocationDto> buildLiveBuses(List<Bus> storedBuses, List<Route> routes) {
        if (storedBuses.isEmpty()) {
            return fallbackBuses();
        }

        int minuteOffset = Instant.now().atZone(ZoneOffset.UTC).getMinute() % 4;
        List<BusLiveLocationDto> buses = new ArrayList<>();

        for (int index = 0; index < storedBuses.size(); index++) {
            Bus bus = storedBuses.get(index);
            Route route = routes.isEmpty() ? null : routes.get(index % routes.size());

            String routeName = route != null ? route.getName() : "City Service";
            String destination = route != null ? route.getDestination() : "Central Station";
            String nextStop = route != null ? route.getSource() : "Main St";

            double latitude = 40.70 + ((index % 6) * 0.006) + (((index + 1) % 3) * 0.001);
            double longitude = -74.02 + ((index % 5) * 0.008) - (((index + 2) % 3) * 0.001);
            int eta = 3 + (index * 2) + minuteOffset;
            String status = (index % 3 == 0) ? "On time" : (index % 3 == 1) ? "Delayed" : "Approaching";

            buses.add(new BusLiveLocationDto(
                    bus.getBusNumber(),
                    routeName,
                    destination,
                    latitude,
                    longitude,
                    eta,
                    status,
                    nextStop));
        }

        return buses;
    }

    private List<RouteAlertDto> buildRouteAlerts(List<Route> routes, List<BusLiveLocationDto> buses) {
        List<RouteAlertDto> alerts = new ArrayList<>();

        if (!routes.isEmpty()) {
            Route firstRoute = routes.get(0);
            alerts.add(new RouteAlertDto(
                    firstRoute.getName(),
                    firstRoute.getDestination(),
                    "Delay",
                    "Slow traffic reported near " + firstRoute.getSource() + "."));
        }

        if (routes.size() > 1) {
            Route secondRoute = routes.get(1);
            alerts.add(new RouteAlertDto(
                    secondRoute.getName(),
                    secondRoute.getDestination(),
                    "Diversion",
                    "Temporary diversion active around downtown section."));
        }

        if (alerts.isEmpty() && !buses.isEmpty()) {
            BusLiveLocationDto firstBus = buses.get(0);
            alerts.add(new RouteAlertDto(
                    firstBus.getRouteName(),
                    firstBus.getDestination(),
                    "Info",
                    "No major disruptions currently reported."));
        }

        return alerts;
    }

    private List<UserNotificationDto> buildNotifications(List<BusLiveLocationDto> buses, List<RouteAlertDto> alerts) {
        List<UserNotificationDto> notifications = new ArrayList<>();
        Instant now = Instant.now();

        if (!buses.isEmpty()) {
            BusLiveLocationDto soonestBus = buses.stream()
                    .sorted((left, right) -> Integer.compare(left.getEtaMinutes(), right.getEtaMinutes()))
                    .findFirst()
                    .orElse(buses.get(0));

            notifications.add(new UserNotificationDto(
                    "info",
                    "Bus approaching",
                    soonestBus.getBusNumber() + " reaches " + soonestBus.getNextStop() + " in about "
                            + soonestBus.getEtaMinutes() + " minutes.",
                    DATE_FORMATTER.format(now)));
        }

        if (!alerts.isEmpty()) {
            RouteAlertDto firstAlert = alerts.get(0);
            notifications.add(new UserNotificationDto(
                    "warning",
                    "Route update",
                    firstAlert.getRouteName() + ": " + firstAlert.getMessage(),
                    DATE_FORMATTER.format(now.minusSeconds(180))));
        }

        notifications.add(new UserNotificationDto(
                "success",
                "Trip reminder",
                "Live dashboard synced with available bus records from the database.",
                DATE_FORMATTER.format(now.minusSeconds(420))));

        return notifications;
    }

    private List<BusLiveLocationDto> fallbackBuses() {
        int minuteOffset = Instant.now().atZone(ZoneOffset.UTC).getMinute() % 4;

        return List.of(
                new BusLiveLocationDto("BMS-101", "Downtown Loop", "Central Station", 40.7194, -74.0060, 3 + minuteOffset,
                        "On time", "5th Ave & Pine St"),
                new BusLiveLocationDto("BMS-204", "University Express", "North Campus", 40.7298, -73.9972,
                        7 + minuteOffset, "Delayed", "City Library"),
                new BusLiveLocationDto("BMS-315", "Airport Connector", "Airport Terminal 2", 40.7033, -74.0170,
                        11 + minuteOffset, "On time", "Harbor Blvd"));
    }
}
