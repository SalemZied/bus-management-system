package com.busmanagementsystem.service.userdashboard;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
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
import com.busmanagementsystem.entity.Driver;
import com.busmanagementsystem.entity.Route;
import com.busmanagementsystem.entity.Schedule;
import com.busmanagementsystem.repository.BusRepository;
import com.busmanagementsystem.repository.BusStopRepository;
import com.busmanagementsystem.repository.DashboardNotificationRepository;
import com.busmanagementsystem.repository.DriverRepository;
import com.busmanagementsystem.repository.RouteRepository;
import com.busmanagementsystem.repository.ScheduleRepository;

import jakarta.annotation.PostConstruct;

@Service
public class UserDashboardService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
    private static final int DEFAULT_SPEED_KMH = 70;
    private static final int MIN_TRAVEL_MINUTES = 30;

    @Value("${app.maps.provider-url:https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png}")
    private String mapProviderUrl;

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final BusStopRepository busStopRepository;
    private final DriverRepository driverRepository;
    private final ScheduleRepository scheduleRepository;
    private final DashboardNotificationRepository dashboardNotificationRepository;
    private final UserDashboardWebSocketHandler userDashboardWebSocketHandler;

    private final List<SimulatedBusState> liveBuses = new CopyOnWriteArrayList<>();
    private final List<RouteAlertDto> alerts = new CopyOnWriteArrayList<>();
    private final List<UserNotificationDto> notificationTemplates = new CopyOnWriteArrayList<>();

    private volatile UserDashboardResponseDto latestSnapshot;

    public UserDashboardService(BusRepository busRepository, RouteRepository routeRepository,
            BusStopRepository busStopRepository, DriverRepository driverRepository, ScheduleRepository scheduleRepository,
            DashboardNotificationRepository dashboardNotificationRepository,
            UserDashboardWebSocketHandler userDashboardWebSocketHandler) {
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.busStopRepository = busStopRepository;
        this.driverRepository = driverRepository;
        this.scheduleRepository = scheduleRepository;
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

        Instant now = Instant.now();
        for (SimulatedBusState busState : liveBuses) {
            busState.advance(now);
        }

        latestSnapshot = buildCurrentSnapshot();
        userDashboardWebSocketHandler.broadcastDashboardUpdate(latestSnapshot);
    }

    private void loadFromDatabase() {
        List<Route> routes = routeRepository.findAll();
        List<Bus> buses = busRepository.findAll();
        List<Driver> drivers = driverRepository.findAll();

        if (routes.isEmpty()) {
            routes = createFallbackRoutes();
        }

        Map<Long, List<Bus>> busesByRouteId = new HashMap<>();
        for (Bus bus : buses) {
            if (bus.getRouteId() == null) {
                continue;
            }
            busesByRouteId.computeIfAbsent(bus.getRouteId(), key -> new ArrayList<>()).add(bus);
        }

        Map<Long, List<Schedule>> schedulesByRouteId = new HashMap<>();
        for (Route route : routes) {
            if (route.getId() != null) {
                schedulesByRouteId.put(route.getId(), scheduleRepository.findByRoute_IdOrderByDepartureTimeAsc(route.getId()));
            }
        }

        Map<Long, Driver> driverById = new HashMap<>();
        for (Driver driver : drivers) {
            if (driver.getId() != null) {
                driverById.put(driver.getId(), driver);
            }
        }

        liveBuses.clear();
        for (Route route : routes) {
            if (route.getId() == null) {
                continue;
            }

            List<BusStop> orderedStops = busStopRepository.findByRouteIdOrderByStopOrderAsc(route.getId());
            RoutePath routePath = buildRoutePath(route, orderedStops);
            if (routePath.points().size() < 2) {
                continue;
            }

            List<LocalTime> departures = resolveDepartures(route, schedulesByRouteId.getOrDefault(route.getId(), List.of()));
            List<Bus> routeBuses = busesByRouteId.getOrDefault(route.getId(), List.of());
            int targetBusCount = Math.max(1, route.getConfiguredBusCount() != null ? route.getConfiguredBusCount() : routeBuses.size());

            for (int index = 0; index < targetBusCount; index++) {
                Bus persistedBus = index < routeBuses.size() ? routeBuses.get(index) : null;
                LocalTime departure = departures.get(index % departures.size());
                String busNumber = resolveBusNumber(route, persistedBus, index);
                int delayMinutes = resolveDelayMinutes(persistedBus, index);
                String driverLabel = resolveDriverLabel(persistedBus, driverById, index);

                liveBuses.add(new SimulatedBusState(
                        busNumber,
                        route.getName(),
                        route.getDestination(),
                        routePath,
                        departure,
                        delayMinutes,
                        driverLabel));
            }
        }

        refreshAlerts(routes);
        refreshNotifications();
    }

    private String resolveBusNumber(Route route, Bus bus, int index) {
        if (bus != null && bus.getBusNumber() != null && !bus.getBusNumber().isBlank()) {
            return bus.getBusNumber();
        }
        String routeCode = route.getName() != null ? route.getName().replaceAll("[^A-Za-z0-9]", "") : "RTE";
        return routeCode + "-" + String.format("%02d", index + 1);
    }

    private String resolveDriverLabel(Bus bus, Map<Long, Driver> driverById, int index) {
        if (bus != null && bus.getDriverId() != null) {
            Driver driver = driverById.get(bus.getDriverId());
            if (driver != null && driver.getName() != null && !driver.getName().isBlank()) {
                return driver.getName();
            }
        }
        return "Driver " + (index + 1);
    }

    private int resolveDelayMinutes(Bus bus, int index) {
        if (bus != null && bus.getDelayMinutes() != null) {
            return Math.max(0, bus.getDelayMinutes());
        }
        return index % 4 == 0 ? 2 + (index % 3) : 0;
    }

    private List<LocalTime> resolveDepartures(Route route, List<Schedule> routeSchedules) {
        List<LocalTime> departures = new ArrayList<>();

        for (Schedule schedule : routeSchedules) {
            if (schedule.getDepartureTime() != null) {
                departures.add(schedule.getDepartureTime());
            }
        }

        if (departures.isEmpty() && route.getDepartureTimes() != null && !route.getDepartureTimes().isBlank()) {
            String[] values = route.getDepartureTimes().split(",");
            for (String value : values) {
                try {
                    departures.add(LocalTime.parse(value.trim()));
                } catch (RuntimeException ignored) {
                    // Ignore invalid persisted values and fallback below.
                }
            }
        }

        if (departures.isEmpty()) {
            departures.add(LocalTime.of(6, 0));
        }

        departures.sort(Comparator.naturalOrder());
        return departures;
    }

    private RoutePath buildRoutePath(Route route, List<BusStop> orderedStops) {
        List<Waypoint> points = new ArrayList<>();

        for (BusStop stop : orderedStops) {
            if (stop.getLatitude() != null && stop.getLongitude() != null) {
                points.add(new Waypoint(stop.getStopName(), stop.getLatitude(), stop.getLongitude()));
            }
        }

        if (points.size() < 2) {
            if (route.getSourceLatitude() != null && route.getSourceLongitude() != null) {
                points.add(new Waypoint(route.getSource(), route.getSourceLatitude(), route.getSourceLongitude()));
            }
            if (route.getDestinationLatitude() != null && route.getDestinationLongitude() != null) {
                points.add(new Waypoint(route.getDestination(), route.getDestinationLatitude(), route.getDestinationLongitude()));
            }
        }

        if (points.size() < 2 && route.getLatitude() != null && route.getLongitude() != null) {
            points.add(new Waypoint(route.getSource(), route.getLatitude() - 0.08, route.getLongitude() - 0.08));
            points.add(new Waypoint(route.getDestination(), route.getLatitude() + 0.08, route.getLongitude() + 0.08));
        }

        return new RoutePath(points);
    }

    private void refreshAlerts(List<Route> routes) {
        alerts.clear();
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            String type = i % 2 == 0 ? "TRAFFIC" : "SERVICE";
            String message = i % 2 == 0
                    ? "Trafic dense sur " + route.getSource() + " → " + route.getDestination() + "."
                    : "Service renforcé sur cette ligne.";
            alerts.add(new RouteAlertDto(route.getName(), route.getDestination(), type, message));
            if (alerts.size() >= 10) {
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

    private List<Route> createFallbackRoutes() {
        List<Route> routes = new ArrayList<>();
        routes.add(new Route(null, "Tunis-Sousse", "Tunis", "Sousse", 36.3100, 10.4100, 36.8065, 10.1815, 35.8256,
                10.6369));
        routes.add(new Route(null, "Tunis-Sfax", "Tunis", "Sfax", 35.7700, 10.4700, 36.8065, 10.1815, 34.7406,
                10.7603));
        routes.add(new Route(null, "Tunis-Bizerte", "Tunis", "Bizerte", 37.0500, 10.0300, 36.8065, 10.1815, 37.2744,
                9.8739));
        for (Route route : routes) {
            route.setConfiguredBusCount(2);
            route.setDepartureTimes("06:00,07:00,08:00,09:00");
        }
        return routes;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double radius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return radius * c;
    }

    private static final class SimulatedBusState {
        private final String busNumber;
        private final String routeName;
        private final String destination;
        private final RoutePath routePath;
        private final LocalTime departureTime;
        private final int delayMinutes;
        private final String driverName;

        private double latitude;
        private double longitude;
        private int etaMinutes;
        private String status;
        private String nextStop;

        private SimulatedBusState(String busNumber, String routeName, String destination, RoutePath routePath,
                LocalTime departureTime, int delayMinutes, String driverName) {
            this.busNumber = busNumber;
            this.routeName = routeName;
            this.destination = destination;
            this.routePath = routePath;
            this.departureTime = departureTime;
            this.delayMinutes = delayMinutes;
            this.driverName = driverName;
            this.latitude = routePath.points().get(0).latitude();
            this.longitude = routePath.points().get(0).longitude();
            this.etaMinutes = estimateTravelMinutes();
            this.status = "Scheduled";
            this.nextStop = routePath.points().get(0).name();
        }

        private void advance(Instant now) {
            long minutesSinceMidnight = Duration.between(now.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay(),
                    now.atOffset(ZoneOffset.UTC).toLocalDateTime()).toMinutes();
            long departureMinutes = departureTime.toSecondOfDay() / 60;
            int travelMinutes = estimateTravelMinutes();
            int cycleMinutes = travelMinutes + 15;
            long relative = minutesSinceMidnight - departureMinutes - delayMinutes;

            if (relative < 0) {
                status = "Scheduled";
                etaMinutes = (int) Math.abs(relative) + travelMinutes;
                latitude = routePath.points().get(0).latitude();
                longitude = routePath.points().get(0).longitude();
                nextStop = routePath.points().get(0).name();
                return;
            }

            long cyclePosition = relative % cycleMinutes;
            if (cyclePosition >= travelMinutes) {
                status = "At terminal";
                etaMinutes = 0;
                latitude = routePath.points().get(routePath.points().size() - 1).latitude();
                longitude = routePath.points().get(routePath.points().size() - 1).longitude();
                nextStop = routePath.points().get(routePath.points().size() - 1).name();
                return;
            }

            double progress = Math.max(0.0, Math.min(1.0, cyclePosition / (double) travelMinutes));
            GeoPoint point = routePath.interpolate(progress);
            latitude = point.latitude();
            longitude = point.longitude();
            etaMinutes = Math.max(1, travelMinutes - (int) cyclePosition);
            status = delayMinutes > 0 ? "Delayed" : "On time";
            nextStop = routePath.nextStop(progress);
        }

        private int estimateTravelMinutes() {
            double distanceKm = routePath.distanceKm();
            int estimated = (int) Math.ceil((distanceKm / DEFAULT_SPEED_KMH) * 60.0);
            return Math.max(MIN_TRAVEL_MINUTES, estimated);
        }

        private BusLiveLocationDto toDto() {
            String finalNextStop = (nextStop == null || nextStop.isBlank()) ? destination : nextStop;
            return new BusLiveLocationDto(
                    busNumber,
                    routeName,
                    destination,
                    latitude,
                    longitude,
                    etaMinutes,
                    status,
                    delayMinutes,
                    finalNextStop + " (Drv: " + driverName + ")");
        }
    }

    private record Waypoint(String name, double latitude, double longitude) {
    }

    private record GeoPoint(double latitude, double longitude) {
    }

    private static final class RoutePath {
        private final List<Waypoint> points;
        private final List<Double> segmentDistances = new ArrayList<>();
        private final double totalDistanceKm;

        private RoutePath(List<Waypoint> points) {
            this.points = List.copyOf(points);
            double total = 0;
            for (int i = 0; i < points.size() - 1; i++) {
                Waypoint from = points.get(i);
                Waypoint to = points.get(i + 1);
                double segment = haversineKm(from.latitude(), from.longitude(), to.latitude(), to.longitude());
                segmentDistances.add(segment);
                total += segment;
            }
            this.totalDistanceKm = Math.max(total, 1.0);
        }

        private List<Waypoint> points() {
            return points;
        }

        private double distanceKm() {
            return totalDistanceKm;
        }

        private GeoPoint interpolate(double progress) {
            if (points.size() == 1) {
                return new GeoPoint(points.get(0).latitude(), points.get(0).longitude());
            }

            double targetDistance = totalDistanceKm * progress;
            double accumulated = 0;
            for (int i = 0; i < segmentDistances.size(); i++) {
                double segmentDistance = segmentDistances.get(i);
                if (accumulated + segmentDistance >= targetDistance) {
                    Waypoint start = points.get(i);
                    Waypoint end = points.get(i + 1);
                    double ratio = segmentDistance == 0 ? 0 : (targetDistance - accumulated) / segmentDistance;
                    double lat = start.latitude() + ((end.latitude() - start.latitude()) * ratio);
                    double lng = start.longitude() + ((end.longitude() - start.longitude()) * ratio);
                    return new GeoPoint(lat, lng);
                }
                accumulated += segmentDistance;
            }

            Waypoint last = points.get(points.size() - 1);
            return new GeoPoint(last.latitude(), last.longitude());
        }

        private String nextStop(double progress) {
            double targetDistance = totalDistanceKm * progress;
            double accumulated = 0;
            for (int i = 0; i < segmentDistances.size(); i++) {
                accumulated += segmentDistances.get(i);
                if (accumulated >= targetDistance) {
                    return points.get(Math.min(i + 1, points.size() - 1)).name();
                }
            }
            return points.get(points.size() - 1).name();
        }
    }
}
