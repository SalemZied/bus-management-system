package com.busmanagementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bus")
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bus_id")
    private Long id;

    @Column(name = "bus_number", unique = true)
    private String busNumber;

    @Column(name = "bus_name")
    private String busName;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "make")
    private int make;

    @Column(name = "model")
    private String model;

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "status")
    private String status;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(name = "next_stop")
    private String nextStop;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public void setBusNumber(String busNumber) {
        this.busNumber = busNumber;
    }

    public String getBusName() {
        return busName;
    }

    public void setBusName(String busName) {
        this.busName = busName;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public int getMake() {
        return make;
    }

    public void setMake(int make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public void setEtaMinutes(Integer etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    public String getNextStop() {
        return nextStop;
    }

    public void setNextStop(String nextStop) {
        this.nextStop = nextStop;
    }

    public Bus(Long id, String busNumber, String busName, Integer capacity, int make, String model, Long routeId,
            Double currentLatitude, Double currentLongitude, String status, Integer delayMinutes, Integer etaMinutes,
            String nextStop) {
        this.id = id;
        this.busNumber = busNumber;
        this.busName = busName;
        this.capacity = capacity;
        this.make = make;
        this.model = model;
        this.routeId = routeId;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.status = status;
        this.delayMinutes = delayMinutes;
        this.etaMinutes = etaMinutes;
        this.nextStop = nextStop;
    }

    public Bus() {
    }
}
