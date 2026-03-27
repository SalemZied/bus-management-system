import React, { useEffect, useMemo, useState } from "react";
import { CircleMarker, MapContainer, Popup, TileLayer, Tooltip, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import "../styles/user-dashboard.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || "http://localhost:8080";

const fallbackData = {
  lastUpdated: new Date().toISOString(),
  mapProviderUrl:
    process.env.REACT_APP_MAPS_PROVIDER_URL ||
    "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
  mapApiKey: process.env.REACT_APP_MAPS_API_KEY || "",
  buses: [
    {
      busNumber: "BMS-101",
      routeName: "Downtown Loop",
      destination: "Central Station",
      latitude: 40.7194,
      longitude: -74.006,
      etaMinutes: 4,
      status: "On time",
      nextStop: "5th Ave & Pine St",
    },
    {
      busNumber: "BMS-204",
      routeName: "University Express",
      destination: "North Campus",
      latitude: 40.7298,
      longitude: -73.9972,
      etaMinutes: 8,
      status: "Delayed",
      nextStop: "City Library",
    },
  ],
  alerts: [
    {
      routeName: "University Express",
      destination: "North Campus",
      alertType: "Delay",
      message: "Traffic congestion near Metro Bridge. Expect +6 minutes.",
    },
  ],
  notifications: [
    {
      level: "info",
      title: "Bus approaching",
      message: "BMS-101 arrives in about 4 minutes.",
      createdAt: new Date().toISOString(),
    },
  ],
};

const FitMapToBuses = ({ buses }) => {
  const map = useMap();

  useEffect(() => {
    if (!buses.length) {
      return;
    }

    const latLngs = buses.map((bus) => [bus.latitude, bus.longitude]);
    map.fitBounds(latLngs, { padding: [36, 36], maxZoom: 14 });
  }, [buses, map]);

  return null;
};

const UserDashboardPage = () => {
  const [dashboardData, setDashboardData] = useState(fallbackData);
  const [isUsingMockData, setIsUsingMockData] = useState(false);

  const loadDashboard = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/user-dashboard`);
      if (!response.ok) {
        throw new Error("Backend unavailable");
      }
      const data = await response.json();
      setDashboardData(data);
      setIsUsingMockData(false);
    } catch (error) {
      setDashboardData(fallbackData);
      setIsUsingMockData(true);
    }
  };

  useEffect(() => {
    loadDashboard();
    const refreshInterval = setInterval(loadDashboard, 30000);
    return () => clearInterval(refreshInterval);
  }, []);

  const buses = dashboardData.buses || [];

  const nextArrival = useMemo(() => {
    if (buses.length === 0) {
      return "No active bus";
    }

    const nextBus = [...buses].sort((a, b) => a.etaMinutes - b.etaMinutes)[0];
    return `${nextBus.busNumber} • ${nextBus.etaMinutes} min`;
  }, [buses]);

  const mapCenter = useMemo(() => {
    if (buses.length === 0) {
      return [40.7228, -74.0045];
    }

    const totalLat = buses.reduce((sum, bus) => sum + bus.latitude, 0);
    const totalLon = buses.reduce((sum, bus) => sum + bus.longitude, 0);
    return [totalLat / buses.length, totalLon / buses.length];
  }, [buses]);

  return (
    <div className="user-dashboard">
      <header className="user-dashboard__header">
        <h1>Bus Management (User)</h1>
        <p>Suivi passager en temps réel: carte, ETA, itinéraires et notifications.</p>
        <span className="user-dashboard__updated-at">
          Updated: {new Date(dashboardData.lastUpdated).toLocaleString()}
        </span>
        {isUsingMockData && <span className="user-dashboard__mock-badge">Mock data enabled</span>}
      </header>

      <section className="user-dashboard__grid">
        <article className="card card--map">
          <h2>Live bus map (Leaflet)</h2>
          <div className="map-area">
            <MapContainer center={mapCenter} zoom={12} scrollWheelZoom className="leaflet-map">
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; CARTO'
                url={dashboardData.mapProviderUrl}
              />
              <FitMapToBuses buses={buses} />
              {buses.map((bus) => (
                <CircleMarker
                  key={bus.busNumber}
                  center={[bus.latitude, bus.longitude]}
                  radius={10}
                  pathOptions={{
                    color: bus.status === "Delayed" ? "#d97706" : "#1f6feb",
                    fillColor: bus.status === "Delayed" ? "#f59e0b" : "#2563eb",
                    fillOpacity: 0.9,
                    weight: 2,
                  }}
                >
                  <Tooltip direction="top" offset={[0, -8]} opacity={1}>
                    {bus.busNumber}
                  </Tooltip>
                  <Popup>
                    <strong>{bus.busNumber}</strong>
                    <br />
                    {bus.routeName} → {bus.destination}
                    <br />
                    Next stop: {bus.nextStop}
                    <br />
                    ETA: {bus.etaMinutes} min
                  </Popup>
                </CircleMarker>
              ))}
            </MapContainer>
          </div>
        </article>

        <article className="card">
          <h2>ETA</h2>
          <p className="kpi">{nextArrival}</p>
          <ul>
            {buses.map((bus) => (
              <li key={bus.busNumber}>
                {bus.busNumber} → {bus.nextStop} in <strong>{bus.etaMinutes} min</strong>
              </li>
            ))}
          </ul>
        </article>

        <article className="card">
          <h2>Routes & disruptions</h2>
          <ul>
            {dashboardData.alerts.map((alert, index) => (
              <li key={`${alert.routeName}-${index}`}>
                <strong>{alert.routeName}</strong> ({alert.destination}) — {alert.alertType}: {alert.message}
              </li>
            ))}
          </ul>
        </article>

        <article className="card">
          <h2>Notifications</h2>
          <ul>
            {dashboardData.notifications.map((notification, index) => (
              <li key={`${notification.title}-${index}`} className={`notification notification--${notification.level}`}>
                <strong>{notification.title}</strong>
                <p>{notification.message}</p>
                <small>{new Date(notification.createdAt).toLocaleTimeString()}</small>
              </li>
            ))}
          </ul>
        </article>
      </section>
    </div>
  );
};

export default UserDashboardPage;
