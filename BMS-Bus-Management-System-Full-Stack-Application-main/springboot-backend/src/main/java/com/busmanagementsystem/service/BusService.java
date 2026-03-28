package com.busmanagementsystem.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.busmanagementsystem.entity.Bus;
import com.busmanagementsystem.entity.Route;
import com.busmanagementsystem.repository.BusRepository;
import com.busmanagementsystem.repository.DriverRepository;
import com.busmanagementsystem.repository.RouteRepository;

@Service
public class BusService {
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final DriverRepository driverRepository;

    @Autowired
    public BusService(BusRepository busRepository, RouteRepository routeRepository, DriverRepository driverRepository) {
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.driverRepository = driverRepository;
    }

    public List<Bus> getAllBuses() {
        return busRepository.findAll();
    }

    public Optional<Bus> getBusById(Long id) {
        return busRepository.findById(id);
    }

    public Bus createBus(Bus bus) {
        validateBus(bus, null);
        return busRepository.save(bus);
    }

    public Bus updateBus(Bus bus) {
        validateBus(bus, bus.getId());
        return busRepository.save(bus);
    }

    public void deleteBus(Long id) {
        busRepository.deleteById(id);
    }

    private void validateBus(Bus bus, Long currentBusId) {
        if (bus.getBusNumber() == null || bus.getBusNumber().isBlank()) {
            throw new IllegalArgumentException("Bus number is required.");
        }
        if (bus.getBusName() == null || bus.getBusName().isBlank()) {
            throw new IllegalArgumentException("Bus name is required.");
        }
        if (bus.getCapacity() == null || bus.getCapacity() <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0.");
        }
        if (bus.getRouteId() == null) {
            throw new IllegalArgumentException("A route must be selected.");
        }
        Route route = routeRepository.findById(bus.getRouteId())
                .orElseThrow(() -> new IllegalArgumentException("Selected route does not exist."));

        if (bus.getDriverId() == null) {
            throw new IllegalArgumentException("A driver must be selected.");
        }
        if (!driverRepository.existsById(bus.getDriverId())) {
            throw new IllegalArgumentException("Selected driver does not exist.");
        }

        Optional<Bus> existingBusForDriver = busRepository.findByDriverId(bus.getDriverId());
        if (existingBusForDriver.isPresent()
                && (currentBusId == null || !existingBusForDriver.get().getId().equals(currentBusId))) {
            throw new IllegalArgumentException("Selected driver is already assigned to another bus.");
        }

        if (bus.getSource() == null || bus.getSource().isBlank()) {
            throw new IllegalArgumentException("Departure is required.");
        }
        if (bus.getDestination() == null || bus.getDestination().isBlank()) {
            throw new IllegalArgumentException("Destination is required.");
        }
        if (bus.getSource().equalsIgnoreCase(bus.getDestination())) {
            throw new IllegalArgumentException("Departure and destination must be different.");
        }

        if (route.getSource() != null && route.getDestination() != null
                && (!route.getSource().equalsIgnoreCase(bus.getSource())
                        || !route.getDestination().equalsIgnoreCase(bus.getDestination()))) {
            throw new IllegalArgumentException("Departure and destination must match the selected route.");
        }

        validateCoordinateRange(bus.getCurrentLatitude(), -90, 90, "Current latitude");
        validateCoordinateRange(bus.getCurrentLongitude(), -180, 180, "Current longitude");
    }

    private void validateCoordinateRange(Double value, int min, int max, String label) {
        if (value == null) {
            return;
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }
}
