package com.busmanagementsystem.service.userdashboard;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.dto.userdashboard.BusLiveLocationDto;
import com.busmanagementsystem.dto.userdashboard.RouteAlertDto;
import com.busmanagementsystem.dto.userdashboard.UserDashboardResponseDto;
import com.busmanagementsystem.dto.userdashboard.UserNotificationDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class UserDashboardService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    @Value("${app.maps.provider-url:https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png}")
    private String defaultMapProviderUrl;

    @Value("classpath:bus-simulation.json")
    private Resource simulationResource;

    private final ObjectMapper objectMapper;
    private final UserDashboardWebSocketHandler userDashboardWebSocketHandler;

    private final List<SimulatedBusState> simulatedBuses = new CopyOnWriteArrayList<>();
    private final List<RouteAlertDto> alerts = new CopyOnWriteArrayList<>();
    private final List<UserNotificationDto> notificationTemplates = new CopyOnWriteArrayList<>();

    private String mapProviderUrl;
    private volatile UserDashboardResponseDto latestSnapshot;
    private int tick = 0;

    public UserDashboardService(ObjectMapper objectMapper, UserDashboardWebSocketHandler userDashboardWebSocketHandler) {
        this.objectMapper = objectMapper;
        this.userDashboardWebSocketHandler = userDashboardWebSocketHandler;
    }

    @PostConstruct
    public void initializeSimulation() {
        loadSimulationFile();
        latestSnapshot = buildCurrentSnapshot();
    }

    public UserDashboardResponseDto getDashboardData() {
        return latestSnapshot;
    }

    @Scheduled(fixedRate = 3000)
    public void broadcastSimulationUpdate() {
        if (simulatedBuses.isEmpty()) {
            return;
        }

        tick++;
        for (int i = 0; i < simulatedBuses.size(); i++) {
            simulatedBuses.get(i).advance(tick, i);
        }

        latestSnapshot = buildCurrentSnapshot();
        userDashboardWebSocketHandler.broadcastDashboardUpdate(latestSnapshot);
    }

    private UserDashboardResponseDto buildCurrentSnapshot() {
        List<BusLiveLocationDto> buses = simulatedBuses.stream()
                .map(SimulatedBusState::toDto)
                .sorted(Comparator.comparing(BusLiveLocationDto::getBusNumber, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<UserNotificationDto> notifications = new ArrayList<>();
        if (!buses.isEmpty()) {
            BusLiveLocationDto nextBus = buses.stream().min(Comparator.comparingInt(BusLiveLocationDto::getEtaMinutes)).orElse(buses.get(0));
            notifications.add(new UserNotificationDto(
                    "info",
                    "Bus approaching",
                    nextBus.getBusNumber() + " arrives in about " + nextBus.getEtaMinutes() + " minutes.",
                    DATE_FORMATTER.format(Instant.now())));
        }

        notifications.addAll(notificationTemplates.stream()
                .map(notification -> new UserNotificationDto(
                        notification.getLevel(),
                        notification.getTitle(),
                        notification.getMessage(),
                        DATE_FORMATTER.format(Instant.now().minusSeconds(60))))
                .toList());

        return new UserDashboardResponseDto(
                DATE_FORMATTER.format(Instant.now()),
                mapProviderUrl,
                "",
                buses,
                List.copyOf(alerts),
                notifications);
    }

    private void loadSimulationFile() {
        try (InputStream stream = simulationResource.getInputStream()) {
            JsonNode root = objectMapper.readTree(stream);
            mapProviderUrl = root.path("mapProviderUrl").asText(defaultMapProviderUrl);

            simulatedBuses.clear();
            for (JsonNode busNode : root.path("buses")) {
                SimulatedBusState busState = new SimulatedBusState(
                        busNode.path("busNumber").asText(),
                        busNode.path("routeName").asText(),
                        busNode.path("destination").asText(),
                        busNode.path("latitude").asDouble(),
                        busNode.path("longitude").asDouble(),
                        busNode.path("etaMinutes").asInt(5),
                        busNode.path("status").asText("On time"),
                        busNode.path("delayMinutes").asInt(0),
                        busNode.path("nextStop").asText("Main Stop"));
                simulatedBuses.add(busState);
            }

            alerts.clear();
            for (JsonNode alertNode : root.path("alerts")) {
                alerts.add(new RouteAlertDto(
                        alertNode.path("routeName").asText(),
                        alertNode.path("destination").asText(),
                        alertNode.path("alertType").asText(),
                        alertNode.path("message").asText()));
            }

            notificationTemplates.clear();
            for (JsonNode notificationNode : root.path("notifications")) {
                notificationTemplates.add(new UserNotificationDto(
                        notificationNode.path("level").asText("info"),
                        notificationNode.path("title").asText("Notice"),
                        notificationNode.path("message").asText(),
                        DATE_FORMATTER.format(Instant.now())));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read bus-simulation.json", exception);
        }
    }

    private static final class SimulatedBusState {
        private final String busNumber;
        private final String routeName;
        private final String destination;
        private final double baseLatitude;
        private final double baseLongitude;
        private final int baseEtaMinutes;
        private final String nextStop;

        private double latitude;
        private double longitude;
        private int etaMinutes;
        private String status;
        private int delayMinutes;

        private SimulatedBusState(String busNumber, String routeName, String destination,
                double latitude, double longitude, int etaMinutes, String status, int delayMinutes, String nextStop) {
            this.busNumber = busNumber;
            this.routeName = routeName;
            this.destination = destination;
            this.baseLatitude = latitude;
            this.baseLongitude = longitude;
            this.baseEtaMinutes = etaMinutes;
            this.nextStop = nextStop;
            this.latitude = latitude;
            this.longitude = longitude;
            this.etaMinutes = etaMinutes;
            this.status = status;
            this.delayMinutes = delayMinutes;
        }

        private void advance(int tick, int index) {
            double wave = tick + (index * 0.7);
            latitude = baseLatitude + (Math.sin(wave) * 0.0014);
            longitude = baseLongitude + (Math.cos(wave) * 0.0014);

            int drift = (tick + index) % 5;
            delayMinutes = drift >= 3 ? drift : 0;
            status = delayMinutes > 0 ? "Delayed" : "On time";
            etaMinutes = Math.max(1, baseEtaMinutes + ((tick + index) % 4) - 1 + delayMinutes);
        }

        private BusLiveLocationDto toDto() {
            return new BusLiveLocationDto(
                    busNumber,
                    routeName,
                    destination,
                    latitude,
                    longitude,
                    etaMinutes,
                    status,
                    delayMinutes,
                    nextStop);
        }
    }
}
