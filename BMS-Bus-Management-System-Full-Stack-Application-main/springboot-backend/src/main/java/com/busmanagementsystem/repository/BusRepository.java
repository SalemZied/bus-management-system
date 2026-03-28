package com.busmanagementsystem.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.busmanagementsystem.entity.Bus;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {
    Optional<Bus> findByDriverId(Long driverId);

    List<Bus> findByRouteIdOrderByIdAsc(Long routeId);
}
