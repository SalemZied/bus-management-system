import React, { useEffect, useMemo, useState } from "react";
import "../styles/user-dashboard.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || "http://localhost:8080";

const fallbackData = {
  lastUpdated: new Date().toISOString(),
  mapProviderUrl: process.env.REACT_APP_MAPS_PROVIDER_URL || "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
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

const getMarkerPosition = (latitude, longitude) => {
  const latMin = 40.69;
  const latMax = 40.74;
  const lonMin = -74.03;
  const lonMax = -73.97;

  const top = ((latMax - latitude) / (latMax - latMin)) * 100;
  const left = ((longitude - lonMin) / (lonMax - lonMin)) * 100;

  return {
    top: `${Math.min(95, Math.max(5, top))}%`,
    left: `${Math.min(95, Math.max(5, left))}%`,
  };
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

  const nextArrival = useMemo(() => {
    if (!dashboardData.buses || dashboardData.buses.length === 0) {
      return "No active bus";
    }

    const nextBus = [...dashboardData.buses].sort((a, b) => a.etaMinutes - b.etaMinutes)[0];
    return `${nextBus.busNumber} • ${nextBus.etaMinutes} min`;
  }, [dashboardData.buses]);

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
          <h2>Live bus map</h2>
          <div className="map-area">
            {dashboardData.buses.map((bus) => {
              const markerPosition = getMarkerPosition(bus.latitude, bus.longitude);
              return (
                <div
                  key={bus.busNumber}
                  className="bus-marker"
                  style={markerPosition}
                  title={`${bus.busNumber} - ${bus.routeName}`}
                >
                  <span>{bus.busNumber}</span>
                </div>
              );
            })}
          </div>
          <small>
            Map provider: {dashboardData.mapProviderUrl}
            {dashboardData.mapApiKey ? " (secured with API key from env)" : ""}
          </small>
        </article>

        <article className="card">
          <h2>ETA</h2>
          <p className="kpi">{nextArrival}</p>
          <ul>
            {dashboardData.buses.map((bus) => (
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
