package com.busmanagementsystem.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.entity.BusStop;
import com.busmanagementsystem.entity.Route;
import com.busmanagementsystem.repository.BusStopRepository;
import com.busmanagementsystem.repository.RouteRepository;

@Service
public class RouteService {
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

    public Optional<Route> getRouteById(Long id) {
        return routeRepository.findById(id);
    }

    public Route createRoute(Route route) {
        validateRoute(route);
        Route savedRoute = routeRepository.save(route);
        saveDefaultStops(savedRoute);
        return savedRoute;
    }

    public Route updateRoute(Route route) {
        validateRoute(route);
        Route savedRoute = routeRepository.save(route);
        saveDefaultStops(savedRoute);
        return savedRoute;
    }

    public void deleteRoute(Long id) {
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
