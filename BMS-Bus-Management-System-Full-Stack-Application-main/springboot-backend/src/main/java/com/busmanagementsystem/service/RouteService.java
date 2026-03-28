package com.busmanagementsystem.service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.entity.BusStop;
import com.busmanagementsystem.entity.Route;
import com.busmanagementsystem.repository.BusStopRepository;
import com.busmanagementsystem.repository.RouteRepository;

@Service
public class RouteService {
    private static final int DEFAULT_BUS_COUNT = 1;
    private static final String DEFAULT_DEPARTURE_TIMES = "06:00";

    private final RouteRepository routeRepository;
    private final BusStopRepository busStopRepository;

    @Autowired
    public RouteService(RouteRepository routeRepository, BusStopRepository busStopRepository) {
        this.routeRepository = routeRepository;
        this.busStopRepository = busStopRepository;
    }

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    public java.util.Optional<Route> getRouteById(Long id) {
        return routeRepository.findById(id);
    }

    public Route createRoute(Route route) {
        normalizeRoute(route);
        validateRoute(route);
        Route savedRoute = routeRepository.save(route);
        saveDefaultStops(savedRoute);
        return savedRoute;
    }

    public Route updateRoute(Route route) {
        normalizeRoute(route);
        validateRoute(route);
        Route savedRoute = routeRepository.save(route);
        saveDefaultStops(savedRoute);
        return savedRoute;
    }

    public void deleteRoute(Long id) {
        busStopRepository.deleteByRouteId(id);
        routeRepository.deleteById(id);
    }

    private void normalizeRoute(Route route) {
        if (route.getConfiguredBusCount() == null) {
            route.setConfiguredBusCount(DEFAULT_BUS_COUNT);
        }

        if (route.getDepartureTimes() == null || route.getDepartureTimes().isBlank()) {
            route.setDepartureTimes(DEFAULT_DEPARTURE_TIMES);
            return;
        }

        String normalizedTimes = Arrays.stream(route.getDepartureTimes().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(","));

        route.setDepartureTimes(normalizedTimes.isBlank() ? DEFAULT_DEPARTURE_TIMES : normalizedTimes);
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

        if (route.getConfiguredBusCount() <= 0) {
            throw new IllegalArgumentException("Configured bus count must be greater than 0.");
        }

        parseDepartureTimes(route.getDepartureTimes());

        validateCoordinate(route.getLatitude(), -90, 90, "Route latitude");
        validateCoordinate(route.getLongitude(), -180, 180, "Route longitude");
        validateCoordinate(route.getSourceLatitude(), -90, 90, "Departure latitude");
        validateCoordinate(route.getSourceLongitude(), -180, 180, "Departure longitude");
        validateCoordinate(route.getDestinationLatitude(), -90, 90, "Destination latitude");
        validateCoordinate(route.getDestinationLongitude(), -180, 180, "Destination longitude");
    }

    private List<LocalTime> parseDepartureTimes(String timesCsv) {
        return Arrays.stream(timesCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(time -> {
                    try {
                        return LocalTime.parse(time);
                    } catch (DateTimeParseException exception) {
                        throw new IllegalArgumentException("Invalid departure time format: " + time + " (expected HH:mm).");
                    }
                })
                .sorted()
                .toList();
    }

    private void validateCoordinate(Double value, int min, int max, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }

    private void saveDefaultStops(Route route) {
        if (route.getId() == null) {
            return;
        }

        busStopRepository.deleteByRouteId(route.getId());

        BusStop sourceStop = new BusStop();
        sourceStop.setRouteId(route.getId());
        sourceStop.setStopName(route.getSource());
        sourceStop.setCity(route.getSource());
        sourceStop.setLatitude(route.getSourceLatitude());
        sourceStop.setLongitude(route.getSourceLongitude());
        sourceStop.setStopOrder(1);

        BusStop destinationStop = new BusStop();
        destinationStop.setRouteId(route.getId());
        destinationStop.setStopName(route.getDestination());
        destinationStop.setCity(route.getDestination());
        destinationStop.setLatitude(route.getDestinationLatitude());
        destinationStop.setLongitude(route.getDestinationLongitude());
        destinationStop.setStopOrder(2);

        busStopRepository.save(sourceStop);
        busStopRepository.save(destinationStop);
    }
}
