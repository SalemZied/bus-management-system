import React, { useEffect, useMemo, useState } from "react";
import { CircleMarker, MapContainer, Popup, TileLayer, Tooltip, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import "../styles/user-dashboard.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || "http://localhost:8080";
const WS_BASE_URL =
  process.env.REACT_APP_WS_BASE_URL ||
  API_BASE_URL.replace(/^http/i, "ws").replace(/\/$/, "");

const fallbackData = {
  lastUpdated: new Date().toISOString(),
  mapProviderUrl:
    process.env.REACT_APP_MAPS_PROVIDER_URL ||
    "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
  mapApiKey: process.env.REACT_APP_MAPS_API_KEY || "",
  buses: [],
  alerts: [],
  notifications: [],
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
  const [selectedLine, setSelectedLine] = useState("ALL");
  const [selectedStatus, setSelectedStatus] = useState("ALL");
  const [selectedDelay, setSelectedDelay] = useState("ALL");

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

    const socket = new WebSocket(`${WS_BASE_URL}/ws/user-dashboard`);

    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        setDashboardData(payload);
        setIsUsingMockData(false);
      } catch (error) {
        // Ignore malformed websocket events.
      }
    };

    socket.onerror = () => {
      setIsUsingMockData(true);
    };

    return () => {
      socket.close();
    };
  }, []);

  const buses = dashboardData.buses || [];

  const lineOptions = useMemo(
    () => ["ALL", ...new Set(buses.map((bus) => bus.routeName).filter(Boolean))],
    [buses],
  );

  const statusOptions = useMemo(
    () => ["ALL", ...new Set(buses.map((bus) => bus.status).filter(Boolean))],
    [buses],
  );

  const filteredBuses = useMemo(() => {
    return buses.filter((bus) => {
      if (selectedLine !== "ALL" && bus.routeName !== selectedLine) {
        return false;
      }
      if (selectedStatus !== "ALL" && bus.status !== selectedStatus) {
        return false;
      }
      if (selectedDelay === "DELAYED_ONLY" && (bus.delayMinutes || 0) <= 0) {
        return false;
      }
      if (selectedDelay === "NO_DELAY" && (bus.delayMinutes || 0) > 0) {
        return false;
      }
      return true;
    });
  }, [buses, selectedDelay, selectedLine, selectedStatus]);

  const nextArrival = useMemo(() => {
    if (filteredBuses.length === 0) {
      return "No active bus";
    }

    const nextBus = [...filteredBuses].sort((a, b) => a.etaMinutes - b.etaMinutes)[0];
    return `${nextBus.busNumber} • ${nextBus.etaMinutes} min`;
  }, [filteredBuses]);

  const mapCenter = useMemo(() => {
    if (filteredBuses.length === 0) {
      return [40.7228, -74.0045];
    }

    const totalLat = filteredBuses.reduce((sum, bus) => sum + bus.latitude, 0);
    const totalLon = filteredBuses.reduce((sum, bus) => sum + bus.longitude, 0);
    return [totalLat / filteredBuses.length, totalLon / filteredBuses.length];
  }, [filteredBuses]);

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

      <section className="user-dashboard__filters card">
        <h2>Filtres</h2>
        <div className="filters-row">
          <label>
            Ligne
            <select value={selectedLine} onChange={(event) => setSelectedLine(event.target.value)}>
              {lineOptions.map((line) => (
                <option key={line} value={line}>
                  {line}
                </option>
              ))}
            </select>
          </label>

          <label>
            Statut
            <select value={selectedStatus} onChange={(event) => setSelectedStatus(event.target.value)}>
              {statusOptions.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </label>

          <label>
            Retard
            <select value={selectedDelay} onChange={(event) => setSelectedDelay(event.target.value)}>
              <option value="ALL">ALL</option>
              <option value="DELAYED_ONLY">DELAYED_ONLY</option>
              <option value="NO_DELAY">NO_DELAY</option>
            </select>
          </label>
        </div>
      </section>

      <section className="user-dashboard__grid">
        <article className="card card--map">
          <h2>Live bus map (Leaflet)</h2>
          <div className="map-area">
            <MapContainer center={mapCenter} zoom={12} scrollWheelZoom className="leaflet-map">
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; CARTO'
                url={dashboardData.mapProviderUrl}
              />
              <FitMapToBuses buses={filteredBuses} />
              {filteredBuses.map((bus) => (
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
                    <br />
                    Delay: {bus.delayMinutes || 0} min
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
            {filteredBuses.map((bus) => (
              <li key={bus.busNumber}>
                {bus.busNumber} → {bus.nextStop} in <strong>{bus.etaMinutes} min</strong> ({bus.status}, retard {bus.delayMinutes || 0} min)
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
