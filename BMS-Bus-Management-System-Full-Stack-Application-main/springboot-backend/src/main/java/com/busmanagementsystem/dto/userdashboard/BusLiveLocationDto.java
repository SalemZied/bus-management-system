package com.busmanagementsystem.dto.userdashboard;

public class BusLiveLocationDto {
    private String busNumber;
    private String routeName;
    private String destination;
    private double latitude;
    private double longitude;
    private int etaMinutes;
    private String status;
    private int delayMinutes;
    private String nextStop;

    public BusLiveLocationDto(String busNumber, String routeName, String destination, double latitude, double longitude,
            int etaMinutes, String status, int delayMinutes, String nextStop) {
        this.busNumber = busNumber;
        this.routeName = routeName;
        this.destination = destination;
        this.latitude = latitude;
        this.longitude = longitude;
        this.etaMinutes = etaMinutes;
        this.status = status;
        this.delayMinutes = delayMinutes;
        this.nextStop = nextStop;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public String getRouteName() {
        return routeName;
    }

    public String getDestination() {
        return destination;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getEtaMinutes() {
        return etaMinutes;
    }

    public String getStatus() {
        return status;
    }

    public int getDelayMinutes() {
        return delayMinutes;
    }

    public String getNextStop() {
        return nextStop;
    }
}
