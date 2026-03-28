package com.busmanagementsystem.service.userdashboard;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.dto.userdashboard.BusLiveLocationDto;
import com.busmanagementsystem.dto.userdashboard.RouteAlertDto;
import com.busmanagementsystem.dto.userdashboard.UserDashboardResponseDto;
import com.busmanagementsystem.dto.userdashboard.UserNotificationDto;
import com.busmanagementsystem.entity.Bus;
import com.busmanagementsystem.entity.BusStop;
import com.busmanagementsystem.entity.DashboardNotification;
import com.busmanagementsystem.entity.Route;
import com.busmanagementsystem.repository.BusRepository;
import com.busmanagementsystem.repository.BusStopRepository;
import com.busmanagementsystem.repository.DashboardNotificationRepository;
import com.busmanagementsystem.repository.RouteRepository;

import jakarta.annotation.PostConstruct;

@Service
public class UserDashboardService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
    private static final int MIN_LIVE_BUS_TARGET = 16;

    private static final Map<String, CityCenter> TUNISIA_CITY_CENTERS = Map.of(
            "Tunis", new CityCenter(36.8065, 10.1815),
            "Ariana", new CityCenter(36.8665, 10.1647),
            "Sousse", new CityCenter(35.8256, 10.63699),
            "Sfax", new CityCenter(34.7406, 10.7603),
            "Bizerte", new CityCenter(37.2744, 9.8739),
            "Monastir", new CityCenter(35.7643, 10.8113),
            "Gabès", new CityCenter(33.8815, 10.0982));

    @Value("${app.maps.provider-url:https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png}")
    private String mapProviderUrl;

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final BusStopRepository busStopRepository;
    private final DashboardNotificationRepository dashboardNotificationRepository;
    private final UserDashboardWebSocketHandler userDashboardWebSocketHandler;

    private final List<SimulatedBusState> liveBuses = new CopyOnWriteArrayList<>();
    private final List<RouteAlertDto> alerts = new CopyOnWriteArrayList<>();
    private final List<UserNotificationDto> notificationTemplates = new CopyOnWriteArrayList<>();

    private volatile UserDashboardResponseDto latestSnapshot;
    private int tick = 0;

    public UserDashboardService(BusRepository busRepository, RouteRepository routeRepository,
            BusStopRepository busStopRepository, DashboardNotificationRepository dashboardNotificationRepository,
            UserDashboardWebSocketHandler userDashboardWebSocketHandler) {
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.busStopRepository = busStopRepository;
        this.dashboardNotificationRepository = dashboardNotificationRepository;
        this.userDashboardWebSocketHandler = userDashboardWebSocketHandler;
    }

    @PostConstruct
    public void initializeSimulation() {
        loadFromDatabase();
        latestSnapshot = buildCurrentSnapshot();
    }

    public UserDashboardResponseDto getDashboardData() {
        return latestSnapshot;
    }

    @Scheduled(fixedRate = 60000)
    public void refreshBaseData() {
        loadFromDatabase();
    }

    @Scheduled(fixedRate = 3000)
    public void broadcastSimulationUpdate() {
        if (liveBuses.isEmpty()) {
            return;
        }

        tick++;
        for (int i = 0; i < liveBuses.size(); i++) {
            liveBuses.get(i).advance(tick, i);
        }

        latestSnapshot = buildCurrentSnapshot();
        userDashboardWebSocketHandler.broadcastDashboardUpdate(latestSnapshot);
    }

    private void loadFromDatabase() {
        List<Route> routes = routeRepository.findAll();
        List<Bus> buses = busRepository.findAll();

        if (routes.isEmpty()) {
            routes = createFallbackRoutes();
        }

        Map<Long, Route> routeById = new HashMap<>();
        for (Route route : routes) {
            if (route.getId() != null) {
                routeById.put(route.getId(), route);
            }
        }

        liveBuses.clear();
        for (int i = 0; i < buses.size(); i++) {
            Bus bus = buses.get(i);
            Route route = resolveRoute(bus, routes, routeById, i);
            SimulatedBusState state = buildStateFromEntity(bus, route, i);
            liveBuses.add(state);
        }

        expandLiveBuses(routes);
        refreshAlerts(routes);
        refreshNotifications();
    }

    private SimulatedBusState buildStateFromEntity(Bus bus, Route route, int index) {
        String destination = (route != null && route.getDestination() != null && !route.getDestination().isBlank())
                ? route.getDestination()
                : "Tunis";

        CityCenter center = centerForDestination(destination, index);

        double latitude = bus.getCurrentLatitude() != null ? bus.getCurrentLatitude() : center.latitude();
        double longitude = bus.getCurrentLongitude() != null ? bus.getCurrentLongitude() : center.longitude();
        int etaMinutes = bus.getEtaMinutes() != null ? Math.max(1, bus.getEtaMinutes()) : 4 + (index % 9);
        int delayMinutes = bus.getDelayMinutes() != null ? Math.max(0, bus.getDelayMinutes()) : 0;
        String status = sanitizeStatus(bus.getStatus(), delayMinutes);
        String routeName = route != null && route.getName() != null ? route.getName() : "Urban Loop";
        String nextStop = resolveNextStop(bus, route, destination);
        String busNumber = bus.getBusNumber() != null ? bus.getBusNumber() : "BUS-" + (100 + index);

        return new SimulatedBusState(
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

    private String resolveNextStop(Bus bus, Route route, String fallbackDestination) {
        if (bus.getNextStop() != null && !bus.getNextStop().isBlank()) {
            return bus.getNextStop();
        }
        if (route != null && route.getId() != null) {
            List<BusStop> stops = busStopRepository.findByRouteIdOrderByStopOrderAsc(route.getId());
            if (!stops.isEmpty()) {
                return stops.get(0).getStopName();
            }
        }
        return fallbackDestination + " Centre";
    }

    private Route resolveRoute(Bus bus, List<Route> routes, Map<Long, Route> routeById, int index) {
        if (bus.getRouteId() != null && routeById.containsKey(bus.getRouteId())) {
            return routeById.get(bus.getRouteId());
        }
        if (routes.isEmpty()) {
            return null;
        }
        return routes.get(index % routes.size());
    }

    private void expandLiveBuses(List<Route> routes) {
        if (liveBuses.size() >= MIN_LIVE_BUS_TARGET || liveBuses.isEmpty()) {
            return;
        }

        int initialSize = liveBuses.size();
        int generated = 0;
        while (liveBuses.size() < MIN_LIVE_BUS_TARGET) {
            SimulatedBusState template = liveBuses.get(generated % initialSize);
            Route route = routes.get(generated % routes.size());
            CityCenter center = centerForDestination(route.getDestination(), generated + 7);

            liveBuses.add(new SimulatedBusState(
                    template.busNumber + "-X" + (generated + 1),
                    route.getName(),
                    route.getDestination(),
                    center.latitude(),
                    center.longitude(),
                    Math.max(3, template.etaMinutes + (generated % 5)),
                    generated % 4 == 0 ? "Delayed" : "On time",
                    generated % 4 == 0 ? 3 + (generated % 6) : 0,
                    route.getDestination() + " Terminal"));
            generated++;
        }
    }

    private void refreshAlerts(List<Route> routes) {
        alerts.clear();
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            String type = i % 2 == 0 ? "TRAFFIC" : "SERVICE";
            String message = i % 2 == 0
                    ? "Circulation dense, prévoir +5 min."
                    : "Fréquence renforcée sur ce corridor.";
            alerts.add(new RouteAlertDto(route.getName(), route.getDestination(), type, message));
            if (alerts.size() >= 8) {
                break;
            }
        }
    }

    private void refreshNotifications() {
        List<DashboardNotification> dbNotifications = dashboardNotificationRepository.findByActiveTrueOrderByIdDesc();

        notificationTemplates.clear();
        for (DashboardNotification notification : dbNotifications) {
            notificationTemplates.add(new UserNotificationDto(
                    notification.getLevel() != null ? notification.getLevel() : "info",
                    notification.getTitle() != null ? notification.getTitle() : "Notice",
                    notification.getMessage() != null ? notification.getMessage() : "Mise à jour trafic en cours.",
                    DATE_FORMATTER.format(Instant.now())));
        }
    }

    private UserDashboardResponseDto buildCurrentSnapshot() {
        List<BusLiveLocationDto> buses = liveBuses.stream()
                .map(SimulatedBusState::toDto)
                .sorted(Comparator.comparing(BusLiveLocationDto::getBusNumber,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<UserNotificationDto> notifications = new ArrayList<>();
        if (!buses.isEmpty()) {
            BusLiveLocationDto nextBus = buses.stream().min(Comparator.comparingInt(BusLiveLocationDto::getEtaMinutes))
                    .orElse(buses.get(0));
            notifications.add(new UserNotificationDto(
                    "info",
                    "Bus approaching",
                    nextBus.getBusNumber() + " arrives in about " + nextBus.getEtaMinutes() + " minutes.",
                    DATE_FORMATTER.format(Instant.now())));
        }
        notifications.addAll(notificationTemplates);

        return new UserDashboardResponseDto(
                DATE_FORMATTER.format(Instant.now()),
                mapProviderUrl,
                "",
                buses,
                List.copyOf(alerts),
                notifications);
    }

    private String sanitizeStatus(String status, int delayMinutes) {
        if (status != null && !status.isBlank()) {
            return status;
        }
        return delayMinutes > 0 ? "Delayed" : "On time";
    }

    private CityCenter centerForDestination(String destination, int seed) {
        CityCenter city = TUNISIA_CITY_CENTERS.getOrDefault(destination, TUNISIA_CITY_CENTERS.get("Tunis"));
        return new CityCenter(
                city.latitude() + ((seed % 5) * 0.01),
                city.longitude() - ((seed % 4) * 0.01));
    }

    private List<Route> createFallbackRoutes() {
        List<Route> routes = new ArrayList<>();
        routes.add(new Route(null, "TUN-AR", "Tunis", "Ariana"));
        routes.add(new Route(null, "TUN-SUS", "Tunis", "Sousse"));
        routes.add(new Route(null, "TUN-SFX", "Tunis", "Sfax"));
        routes.add(new Route(null, "TUN-BIZ", "Tunis", "Bizerte"));
        routes.add(new Route(null, "TUN-MON", "Tunis", "Monastir"));
        routes.add(new Route(null, "TUN-GAB", "Tunis", "Gabès"));
        return routes;
    }

    private record CityCenter(double latitude, double longitude) {
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
            latitude = baseLatitude + (Math.sin(wave) * 0.0050);
            longitude = baseLongitude + (Math.cos(wave) * 0.0050);

            int drift = (tick + index) % 6;
            delayMinutes = drift >= 4 ? drift : Math.max(delayMinutes - 1, 0);
            status = delayMinutes > 0 ? "Delayed" : "On time";
            etaMinutes = Math.max(1, baseEtaMinutes + ((tick + index) % 5) - 2 + delayMinutes);
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
