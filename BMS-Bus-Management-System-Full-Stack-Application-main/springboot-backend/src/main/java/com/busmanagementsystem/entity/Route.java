package com.busmanagementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "route")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Long id;

    @Column(name = "name",unique=true)
    private String name;

    @Column(name = "source")
    private String source;

    @Column(name = "destination")
    private String destination;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "source_latitude")
    private Double sourceLatitude;

    @Column(name = "source_longitude")
    private Double sourceLongitude;

    @Column(name = "destination_latitude")
    private Double destinationLatitude;

    @Column(name = "destination_longitude")
    private Double destinationLongitude;

    @Column(name = "configured_bus_count")
    private Integer configuredBusCount;

    @Column(name = "departure_times", length = 500)
    private String departureTimes;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public Double getSourceLatitude() {
		return sourceLatitude;
	}

	public void setSourceLatitude(Double sourceLatitude) {
		this.sourceLatitude = sourceLatitude;
	}

	public Double getSourceLongitude() {
		return sourceLongitude;
	}

	public void setSourceLongitude(Double sourceLongitude) {
		this.sourceLongitude = sourceLongitude;
	}

	public Double getDestinationLatitude() {
		return destinationLatitude;
	}

	public void setDestinationLatitude(Double destinationLatitude) {
		this.destinationLatitude = destinationLatitude;
	}

	public Double getDestinationLongitude() {
		return destinationLongitude;
	}

	public void setDestinationLongitude(Double destinationLongitude) {
		this.destinationLongitude = destinationLongitude;
	}

	public Route(Long id, String name, String source, String destination) {
		super();
		this.id = id;
		this.name = name;
		this.source = source;
		this.destination = destination;
	}

	public Route(Long id, String name, String source, String destination, Double latitude, Double longitude,
			Double sourceLatitude, Double sourceLongitude, Double destinationLatitude, Double destinationLongitude) {
		super();
		this.id = id;
		this.name = name;
		this.source = source;
		this.destination = destination;
		this.latitude = latitude;
		this.longitude = longitude;
		this.sourceLatitude = sourceLatitude;
		this.sourceLongitude = sourceLongitude;
		this.destinationLatitude = destinationLatitude;
		this.destinationLongitude = destinationLongitude;
	}

	public Integer getConfiguredBusCount() {
		return configuredBusCount;
	}

	public void setConfiguredBusCount(Integer configuredBusCount) {
		this.configuredBusCount = configuredBusCount;
	}

	public String getDepartureTimes() {
		return departureTimes;
	}

	public void setDepartureTimes(String departureTimes) {
		this.departureTimes = departureTimes;
	}

	public Route() {
	}

    
}
