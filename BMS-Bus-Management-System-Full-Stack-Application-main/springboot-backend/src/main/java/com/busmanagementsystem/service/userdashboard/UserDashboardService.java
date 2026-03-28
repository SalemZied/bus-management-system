package com.busmanagementsystem.service.userdashboard;

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
import com.busmanagementsystem.entity.Route;
import com.busmanagementsystem.entity.Schedule;
import com.busmanagementsystem.repository.BusRepository;
import com.busmanagementsystem.repository.BusStopRepository;
import com.busmanagementsystem.repository.DashboardNotificationRepository;
import com.busmanagementsystem.repository.RouteRepository;
import com.busmanagementsystem.repository.ScheduleRepository;

import jakarta.annotation.PostConstruct;

@Service
public class UserDashboardService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    @Value("${app.maps.provider-url:https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png}")
    private String mapProviderUrl;

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final BusStopRepository busStopRepository;
    private final ScheduleRepository scheduleRepository;
    private final DashboardNotificationRepository dashboardNotificationRepository;
    private final UserDashboardWebSocketHandler userDashboardWebSocketHandler;

    private final List<SimulatedBusState> liveBuses = new CopyOnWriteArrayList<>();
    private final List<RouteAlertDto> alerts = new CopyOnWriteArrayList<>();
    private final List<UserNotificationDto> notificationTemplates = new CopyOnWriteArrayList<>();

    private volatile UserDashboardResponseDto latestSnapshot;

    public UserDashboardService(BusRepository busRepository, RouteRepository routeRepository,
            BusStopRepository busStopRepository, ScheduleRepository scheduleRepository,
            DashboardNotificationRepository dashboardNotificationRepository,
            UserDashboardWebSocketHandler userDashboardWebSocketHandler) {
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.busStopRepository = busStopRepository;
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

        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        for (SimulatedBusState state : liveBuses) {
            state.advance(now);
        }

        latestSnapshot = buildCurrentSnapshot();
        userDashboardWebSocketHandler.broadcastDashboardUpdate(latestSnapshot);
    }

    private void loadFromDatabase() {
        List<Route> routes = routeRepository.findAll();
        List<Bus> buses = busRepository.findAll();

        Map<Long, Route> routeById = new HashMap<>();
        for (Route route : routes) {
            if (route.getId() != null) {
                routeById.put(route.getId(), route);
            }
        }

        liveBuses.clear();
        for (int i = 0; i < buses.size(); i++) {
            Bus bus = buses.get(i);
            Route route = routeById.get(bus.getRouteId());
            if (route == null) {
                continue;
            }

            List<BusStop> stops = busStopRepository.findByRouteIdOrderByStopOrderAsc(route.getId());
            if (stops.size() < 2) {
                stops = defaultStops(route);
            }

            Schedule schedule = resolveSchedule(route.getId(), bus.getId());
            liveBuses.add(new SimulatedBusState(bus, route, stops, schedule));
        }

        refreshAlerts(routes);
        refreshNotifications();
    }

    private Schedule resolveSchedule(Long routeId, Long busId) {
        List<Schedule> schedules = scheduleRepository.findByRoute_IdOrderByDepartureTimeAsc(routeId);
        for (Schedule schedule : schedules) {
            if (schedule.getBus() != null && busId.equals(schedule.getBus().getId())) {
                return schedule;
            }
        }
        return schedules.isEmpty() ? null : schedules.get(0);
    }

    private List<BusStop> defaultStops(Route route) {
        List<BusStop> stops = new ArrayList<>();
        BusStop start = new BusStop();
        start.setStopName(route.getSource());
        start.setLatitude(route.getSourceLatitude());
        start.setLongitude(route.getSourceLongitude());
        start.setStopOrder(1);

        BusStop end = new BusStop();
        end.setStopName(route.getDestination());
        end.setLatitude(route.getDestinationLatitude());
        end.setLongitude(route.getDestinationLongitude());
        end.setStopOrder(2);

        stops.add(start);
        stops.add(end);
        return stops;
    }

    private void refreshAlerts(List<Route> routes) {
        alerts.clear();
        for (int i = 0; i < routes.size() && i < 8; i++) {
            Route route = routes.get(i);
            String type = i % 2 == 0 ? "TRAFFIC" : "SERVICE";
            String message = i % 2 == 0
                    ? "Circulation dense observée sur l'axe."
                    : "Service régulier sur cette ligne.";
            alerts.add(new RouteAlertDto(route.getName(), route.getDestination(), type, message));
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

    private static final class SimulatedBusState {
        private static final double AVERAGE_SPEED_KMH = 68.0;
        private static final int TERMINAL_DWELL_MINUTES = 12;

        private final String busNumber;
        private final String routeName;
        private final String destination;
        private final List<StopPoint> points;
        private final int tripDurationMinutes;
        private final int cycleMinutes;
        private final int phaseOffsetMinutes;
        private final LocalTime departureTime;

        private double latitude;
        private double longitude;
        private int etaMinutes;
        private String status;
        private int delayMinutes;
        private String nextStop;

        private SimulatedBusState(Bus bus, Route route, List<BusStop> stops, Schedule schedule) {
            this.busNumber = bus.getBusNumber() != null ? bus.getBusNumber() : "BUS-" + bus.getId();
            this.routeName = route.getName() != null ? route.getName() : route.getSource() + " → " + route.getDestination();
            this.destination = route.getDestination();
            this.points = toPoints(stops);
            this.tripDurationMinutes = Math.max(45, (int) Math.round((totalDistanceKm(points) / AVERAGE_SPEED_KMH) * 60.0));
            this.cycleMinutes = tripDurationMinutes + TERMINAL_DWELL_MINUTES;
            this.phaseOffsetMinutes = Math.abs(this.busNumber.hashCode()) % 30;
            this.departureTime = schedule != null && schedule.getDepartureTime() != null ? schedule.getDepartureTime() : LocalTime.of(6, 0);
            this.nextStop = !points.isEmpty() ? points.get(0).name() : destination;

            StopPoint start = points.isEmpty() ? new StopPoint(destination, route.getSourceLatitude(), route.getSourceLongitude(), 0.0)
                    : points.get(0);
            this.latitude = start.latitude();
            this.longitude = start.longitude();
            this.etaMinutes = tripDurationMinutes;
            this.status = "Scheduled";
            this.delayMinutes = 0;
        }

        private void advance(LocalTime now) {
            if (points.size() < 2) {
                status = "Scheduled";
                etaMinutes = 0;
                return;
            }

            int nowMinutes = now.getHour() * 60 + now.getMinute();
            int departureMinutes = departureTime.getHour() * 60 + departureTime.getMinute();
            int elapsedSinceDeparture = nowMinutes - departureMinutes - phaseOffsetMinutes;

            if (elapsedSinceDeparture < 0) {
                moveToProgress(0.0);
                etaMinutes = Math.max(1, tripDurationMinutes - elapsedSinceDeparture);
                delayMinutes = 0;
                status = "Scheduled";
                return;
            }

            int cyclePosition = elapsedSinceDeparture % cycleMinutes;
            if (cyclePosition < tripDurationMinutes) {
                double progress = Math.min(1.0, Math.max(0.0, cyclePosition / (double) tripDurationMinutes));
                moveToProgress(progress);
                delayMinutes = computeDelay(nowMinutes);
                etaMinutes = Math.max(1, (int) Math.round((1.0 - progress) * tripDurationMinutes) + delayMinutes);
                status = delayMinutes > 0 ? "Delayed" : "On time";
            } else {
                moveToProgress(1.0);
                delayMinutes = 0;
                etaMinutes = 1;
                status = "Boarding";
            }
        }

        private int computeDelay(int minuteOfDay) {
            int value = Math.abs((busNumber + minuteOfDay).hashCode()) % 13;
            return value >= 10 ? value - 9 : 0;
        }

        private void moveToProgress(double progress) {
            double targetDistance = totalDistanceKm(points) * progress;
            StopPoint interpolated = interpolate(points, targetDistance);
            latitude = interpolated.latitude();
            longitude = interpolated.longitude();
            nextStop = resolveNextStopName(points, targetDistance);
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

        private static List<StopPoint> toPoints(List<BusStop> stops) {
            List<StopPoint> points = new ArrayList<>();
            double cumulative = 0.0;
            for (int i = 0; i < stops.size(); i++) {
                BusStop stop = stops.get(i);
                if (stop.getLatitude() == null || stop.getLongitude() == null) {
                    continue;
                }
                if (!points.isEmpty()) {
                    StopPoint previous = points.get(points.size() - 1);
                    cumulative += haversine(previous.latitude(), previous.longitude(), stop.getLatitude(), stop.getLongitude());
                }
                points.add(new StopPoint(
                        stop.getStopName() != null ? stop.getStopName() : "Stop " + (i + 1),
                        stop.getLatitude(),
                        stop.getLongitude(),
                        cumulative));
            }
            return points;
        }

        private static double totalDistanceKm(List<StopPoint> points) {
            if (points.isEmpty()) {
                return 0;
            }
            return points.get(points.size() - 1).distanceFromStartKm();
        }

        private static StopPoint interpolate(List<StopPoint> points, double targetDistanceKm) {
            if (points.size() == 1) {
                return points.get(0);
            }
            for (int i = 0; i < points.size() - 1; i++) {
                StopPoint start = points.get(i);
                StopPoint end = points.get(i + 1);
                if (targetDistanceKm <= end.distanceFromStartKm()) {
                    double segmentDistance = Math.max(0.0001, end.distanceFromStartKm() - start.distanceFromStartKm());
                    double ratio = (targetDistanceKm - start.distanceFromStartKm()) / segmentDistance;
                    ratio = Math.max(0.0, Math.min(1.0, ratio));
                    double lat = start.latitude() + ((end.latitude() - start.latitude()) * ratio);
                    double lng = start.longitude() + ((end.longitude() - start.longitude()) * ratio);
                    return new StopPoint(end.name(), lat, lng, targetDistanceKm);
                }
            }
            return points.get(points.size() - 1);
        }

        private static String resolveNextStopName(List<StopPoint> points, double targetDistanceKm) {
            for (StopPoint point : points) {
                if (point.distanceFromStartKm() >= targetDistanceKm) {
                    return point.name();
                }
            }
            return points.get(points.size() - 1).name();
        }

        private static double haversine(double lat1, double lon1, double lat2, double lon2) {
            final int earthRadiusKm = 6371;
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return earthRadiusKm * c;
        }

        private record StopPoint(String name, double latitude, double longitude, double distanceFromStartKm) {
        }
    }
}
