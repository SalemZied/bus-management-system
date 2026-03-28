package com.busmanagementsystem.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.entity.Bus;
import com.busmanagementsystem.entity.BusStop;
import com.busmanagementsystem.entity.Driver;
import com.busmanagementsystem.entity.Route;
import com.busmanagementsystem.entity.Schedule;
import com.busmanagementsystem.repository.BusRepository;
import com.busmanagementsystem.repository.BusStopRepository;
import com.busmanagementsystem.repository.DriverRepository;
import com.busmanagementsystem.repository.RouteRepository;
import com.busmanagementsystem.repository.ScheduleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RouteService {
    private static final int DEFAULT_CAPACITY = 52;
    private static final int DEFAULT_ROUTE_DURATION_MINUTES = 120;

    private final RouteRepository routeRepository;
    private final BusStopRepository busStopRepository;
    private final BusRepository busRepository;
    private final ScheduleRepository scheduleRepository;
    private final DriverRepository driverRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public RouteService(RouteRepository routeRepository, BusStopRepository busStopRepository, BusRepository busRepository,
            ScheduleRepository scheduleRepository, DriverRepository driverRepository, ObjectMapper objectMapper) {
        this.routeRepository = routeRepository;
        this.busStopRepository = busStopRepository;
        this.busRepository = busRepository;
        this.scheduleRepository = scheduleRepository;
        this.driverRepository = driverRepository;
        this.objectMapper = objectMapper;
    }

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    public Optional<Route> getRouteById(Long id) {
        return routeRepository.findById(id);
    }

    public Route createRoute(Route route) {
        validateRoute(route);
        Route savedRoute = routeRepository.save(route);
        saveStopsFromRoute(savedRoute);
        synchronizeFleetForRoute(savedRoute);
        return savedRoute;
    }

    public Route updateRoute(Route route) {
        validateRoute(route);
        Route savedRoute = routeRepository.save(route);
        saveStopsFromRoute(savedRoute);
        synchronizeFleetForRoute(savedRoute);
        return savedRoute;
    }

    public void deleteRoute(Long id) {
        List<Schedule> schedules = scheduleRepository.findByRoute_IdOrderByDepartureTimeAsc(id);
        for (Schedule schedule : schedules) {
            scheduleRepository.deleteById(schedule.getId());
        }

        List<Bus> buses = busRepository.findByRouteIdOrderByIdAsc(id);
        for (Bus bus : buses) {
            busRepository.deleteById(bus.getId());
        }

        busStopRepository.deleteByRouteId(id);
        routeRepository.deleteById(id);
    }

    private void validateRoute(Route route) {
        if (route.getName() == null || route.getName().isBlank()) {
            throw new IllegalArgumentException("Route name is required.");
        }
        if (route.getSource() == null || route.getSource().isBlank()) {
            throw new IllegalArgumentException("Departure stop name is required.");
        }
        if (route.getDestination() == null || route.getDestination().isBlank()) {
            throw new IllegalArgumentException("Destination stop name is required.");
        }
        if (route.getSource().equalsIgnoreCase(route.getDestination())) {
            throw new IllegalArgumentException("Departure and destination must be different.");
        }

        validateCoordinate(route.getLatitude(), -90, 90, "Route latitude");
        validateCoordinate(route.getLongitude(), -180, 180, "Route longitude");
        validateCoordinate(route.getSourceLatitude(), -90, 90, "Departure latitude");
        validateCoordinate(route.getSourceLongitude(), -180, 180, "Departure longitude");
        validateCoordinate(route.getDestinationLatitude(), -90, 90, "Destination latitude");
        validateCoordinate(route.getDestinationLongitude(), -180, 180, "Destination longitude");

        int busCount = route.getConfiguredBusCount() == null ? 1 : route.getConfiguredBusCount();
        if (busCount <= 0 || busCount > 100) {
            throw new IllegalArgumentException("Configured bus count must be between 1 and 100.");
        }

        parseDepartureTimes(route.getDepartureTimes());
        parseWaypoints(route.getWaypointsJson());
    }

    private void validateCoordinate(Double value, int min, int max, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }

    private void saveStopsFromRoute(Route route) {
        if (route.getId() == null) {
            return;
        }

        busStopRepository.deleteByRouteId(route.getId());

        List<WaypointInput> waypoints = parseWaypoints(route.getWaypointsJson());
        List<BusStop> stops = new ArrayList<>();

        stops.add(createStop(route.getId(), route.getSource(), route.getSourceLatitude(), route.getSourceLongitude(), 1));

        for (int i = 0; i < waypoints.size(); i++) {
            WaypointInput waypoint = waypoints.get(i);
            stops.add(createStop(route.getId(),
                    waypoint.name() != null && !waypoint.name().isBlank() ? waypoint.name() : "Waypoint " + (i + 1),
                    waypoint.latitude(),
                    waypoint.longitude(),
                    i + 2));
        }

        stops.add(createStop(route.getId(), route.getDestination(), route.getDestinationLatitude(), route.getDestinationLongitude(),
                waypoints.size() + 2));

        for (BusStop stop : stops) {
            busStopRepository.save(stop);
        }
    }

    private BusStop createStop(Long routeId, String name, Double latitude, Double longitude, Integer stopOrder) {
        BusStop stop = new BusStop();
        stop.setRouteId(routeId);
        stop.setStopName(name);
        stop.setCity(name);
        stop.setLatitude(latitude);
        stop.setLongitude(longitude);
        stop.setStopOrder(stopOrder);
        return stop;
    }

    private void synchronizeFleetForRoute(Route route) {
        if (route.getId() == null) {
            return;
        }

        List<Bus> existingBuses = new ArrayList<>(busRepository.findByRouteIdOrderByIdAsc(route.getId()));
        int targetBusCount = route.getConfiguredBusCount() == null ? Math.max(1, existingBuses.size()) : route.getConfiguredBusCount();
        List<Bus> synchronizedBuses = new ArrayList<>();

        List<Long> availableDriverIds = findAvailableDriverIds(existingBuses);

        for (int index = 0; index < targetBusCount; index++) {
            Bus bus = index < existingBuses.size() ? existingBuses.get(index) : new Bus();
            String busNumber = normalizeBusNumber(bus.getBusNumber(), route, index);

            bus.setBusNumber(busNumber);
            if (bus.getBusName() == null || bus.getBusName().isBlank()) {
                bus.setBusName(route.getName() + " Bus " + (index + 1));
            }
            if (bus.getCapacity() == null || bus.getCapacity() <= 0) {
                bus.setCapacity(DEFAULT_CAPACITY);
            }
            bus.setRouteId(route.getId());
            bus.setSource(route.getSource());
            bus.setDestination(route.getDestination());
            bus.setCurrentLatitude(route.getSourceLatitude());
            bus.setCurrentLongitude(route.getSourceLongitude());
            if (bus.getStatus() == null || bus.getStatus().isBlank()) {
                bus.setStatus("Scheduled");
            }
            if (bus.getDriverId() == null && index < availableDriverIds.size()) {
                bus.setDriverId(availableDriverIds.get(index));
            }

            synchronizedBuses.add(busRepository.save(bus));
        }

        for (int index = targetBusCount; index < existingBuses.size(); index++) {
            Bus busToDelete = existingBuses.get(index);
            deleteSchedulesByBusId(busToDelete.getId());
            busRepository.deleteById(busToDelete.getId());
        }

        synchronizeSchedules(route, synchronizedBuses);
    }

    private List<Long> findAvailableDriverIds(List<Bus> routeBuses) {
        Set<Long> currentRouteDriverIds = new HashSet<>();
        for (Bus bus : routeBuses) {
            if (bus.getDriverId() != null) {
                currentRouteDriverIds.add(bus.getDriverId());
            }
        }

        Set<Long> unavailableDriverIds = new HashSet<>();
        for (Bus bus : busRepository.findAll()) {
            if (bus.getDriverId() != null && !currentRouteDriverIds.contains(bus.getDriverId())) {
                unavailableDriverIds.add(bus.getDriverId());
            }
        }

        List<Long> availableDriverIds = new ArrayList<>();
        for (Driver driver : driverRepository.findAll()) {
            if (!unavailableDriverIds.contains(driver.getId())) {
                availableDriverIds.add(driver.getId());
            }
        }
        return availableDriverIds;
    }

    private String normalizeBusNumber(String currentBusNumber, Route route, int index) {
        if (currentBusNumber != null && !currentBusNumber.isBlank()) {
            return currentBusNumber;
        }
        String routeToken = route.getName() == null ? "ROUTE" : route.getName().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (routeToken.length() > 8) {
            routeToken = routeToken.substring(0, 8);
        }
        return routeToken + "-" + String.format("%02d", index + 1);
    }

    private void synchronizeSchedules(Route route, List<Bus> routeBuses) {
        List<LocalTime> departures = parseDepartureTimes(route.getDepartureTimes());
        if (departures.isEmpty()) {
            departures.add(LocalTime.of(6, 0));
        }

        int durationMinutes = estimateDurationMinutes(route.getId());
        List<Schedule> schedules = scheduleRepository.findByRoute_IdOrderByDepartureTimeAsc(route.getId());
        Map<Long, Schedule> scheduleByBusId = new HashMap<>();
        for (Schedule schedule : schedules) {
            if (schedule.getBus() != null && schedule.getBus().getId() != null) {
                scheduleByBusId.put(schedule.getBus().getId(), schedule);
            }
        }

        for (int i = 0; i < routeBuses.size(); i++) {
            Bus bus = routeBuses.get(i);
            LocalTime departure = departures.get(i % departures.size()).plusMinutes((i / departures.size()) * 10L);
            LocalTime arrival = departure.plusMinutes(durationMinutes);

            Schedule schedule = scheduleByBusId.getOrDefault(bus.getId(), new Schedule());
            schedule.setBus(bus);
            schedule.setRoute(route);
            schedule.setDriver(resolveDriver(bus.getDriverId()));
            schedule.setDepartureTime(departure);
            schedule.setArrivalTime(arrival);

            scheduleRepository.save(schedule);
            scheduleByBusId.remove(bus.getId());
        }

        for (Schedule stale : scheduleByBusId.values()) {
            scheduleRepository.deleteById(stale.getId());
        }
    }

    private Driver resolveDriver(Long driverId) {
        if (driverId == null) {
            return null;
        }
        return driverRepository.findById(driverId).orElse(null);
    }

    private void deleteSchedulesByBusId(Long busId) {
        if (busId == null) {
            return;
        }
        for (Schedule schedule : scheduleRepository.findAll()) {
            if (schedule.getBus() != null && busId.equals(schedule.getBus().getId())) {
                scheduleRepository.deleteById(schedule.getId());
            }
        }
    }

    private int estimateDurationMinutes(Long routeId) {
        List<BusStop> stops = busStopRepository.findByRouteIdOrderByStopOrderAsc(routeId);
        if (stops.size() < 2) {
            return DEFAULT_ROUTE_DURATION_MINUTES;
        }

        double distanceKm = 0.0;
        for (int i = 0; i < stops.size() - 1; i++) {
            BusStop start = stops.get(i);
            BusStop end = stops.get(i + 1);
            distanceKm += haversine(start.getLatitude(), start.getLongitude(), end.getLatitude(), end.getLongitude());
        }

        int travelMinutes = (int) Math.round((distanceKm / 70.0) * 60.0);
        return Math.max(45, travelMinutes);
    }

    private double haversine(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0;
        }
        final int earthRadiusKm = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private List<LocalTime> parseDepartureTimes(String csvOrListText) {
        List<LocalTime> parsed = new ArrayList<>();
        if (csvOrListText == null || csvOrListText.isBlank()) {
            return parsed;
        }

        String[] tokens = csvOrListText.split(",");
        Set<LocalTime> unique = new HashSet<>();
        for (String raw : tokens) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                LocalTime time = LocalTime.parse(raw.trim());
                if (unique.add(time)) {
                    parsed.add(time);
                }
            } catch (Exception exception) {
                throw new IllegalArgumentException("Departure times must use HH:mm format separated by commas.");
            }
        }

        parsed.sort(Comparator.naturalOrder());
        return parsed;
    }

    private List<WaypointInput> parseWaypoints(String waypointsJson) {
        if (waypointsJson == null || waypointsJson.isBlank()) {
            return List.of();
        }

        try {
            List<WaypointInput> waypoints = objectMapper.readValue(waypointsJson, new TypeReference<List<WaypointInput>>() {
            });
            for (WaypointInput waypoint : waypoints) {
                validateCoordinate(waypoint.latitude(), -90, 90, "Waypoint latitude");
                validateCoordinate(waypoint.longitude(), -180, 180, "Waypoint longitude");
            }
            return waypoints;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Waypoints are invalid. Please select route points from the map.");
        }
    }

    private record WaypointInput(String name, Double latitude, Double longitude) {
    }
}
