import React, { useEffect, useMemo, useState } from "react";
import LeafletMiniMap from "../components/UserMap/LeafletMiniMap";
import "../styles/user-dashboard.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || "http://localhost:8080";

const fallbackData = {
  lastUpdated: new Date().toISOString(),
  mapProviderUrl: process.env.REACT_APP_MAPS_PROVIDER_URL || "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
  mapApiKey: process.env.REACT_APP_MAPS_API_KEY || "",
  buses: [],
  alerts: [],
  notifications: [],
};

const UserDashboardPage = () => {
  const [dashboardData, setDashboardData] = useState(fallbackData);
  const [isUsingMockData, setIsUsingMockData] = useState(false);

  useEffect(() => {
    let isMounted = true;

    const safeLoadDashboard = async () => {
      let response = fallbackData;
      let usingMock = false;

      try {
        const apiResponse = await fetch(`${API_BASE_URL}/api/user-dashboard`);
        if (!apiResponse.ok) {
          throw new Error("Backend unavailable");
        }
        response = await apiResponse.json();
      } catch (error) {
        usingMock = true;
      }

      if (isMounted) {
        setDashboardData(response);
        setIsUsingMockData(usingMock);
      }
    };

    safeLoadDashboard();
    const refreshInterval = setInterval(safeLoadDashboard, 30000);

    return () => {
      isMounted = false;
      clearInterval(refreshInterval);
    };
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
      <div className="user-dashboard__container">
        <header className="user-dashboard__header">
          <h1>Bus Management (User)</h1>
          <p>Suivi passager en temps réel: carte, ETA, itinéraires et notifications.</p>
          <div className="user-dashboard__meta">
            <span className="user-dashboard__updated-at">
              Updated: {new Date(dashboardData.lastUpdated).toLocaleString()}
            </span>
            {isUsingMockData && <span className="user-dashboard__mock-badge">Backend unreachable: empty fallback mode</span>}
          </div>
        </header>

        <section className="dashboard-layout">
          <article className="card card--map">
            <div className="card__title-row">
              <h2>Live bus map</h2>
              <span>{dashboardData.buses.length} bus(es) from dataset</span>
            </div>
            <div className="map-area">
              <LeafletMiniMap
                buses={dashboardData.buses}
                mapProviderUrl={dashboardData.mapProviderUrl}
                mapApiKey={dashboardData.mapApiKey}
              />
              {dashboardData.buses.length === 0 && <div className="map-empty">No bus positions available from backend.</div>}
            </div>
            <small className="map-provider">
              Map provider: {dashboardData.mapProviderUrl}
              {dashboardData.mapApiKey ? " (secured with API key from env)" : ""}
            </small>
          </article>

          <div className="dashboard-layout__secondary">
            <article className="card">
              <h2>ETA</h2>
              <p className="kpi">{nextArrival}</p>
              <ul className="compact-list">
                {dashboardData.buses.map((bus) => (
                  <li key={`${bus.busNumber}-${bus.nextStop}`}>
                    <div>
                      <strong>{bus.busNumber}</strong> → {bus.nextStop}
                    </div>
                    <span>{bus.etaMinutes} min</span>
                  </li>
                ))}
              </ul>
            </article>

            <article className="card">
              <h2>Routes & disruptions</h2>
              <ul className="alerts-list">
                {dashboardData.alerts.map((alert, index) => (
                  <li key={`${alert.routeName}-${alert.alertType}-${index}`}>
                    <strong>{alert.routeName}</strong>
                    <span>{alert.destination}</span>
                    <p>
                      {alert.alertType}: {alert.message}
                    </p>
                  </li>
                ))}
              </ul>
            </article>
          </div>

          <article className="card card--notifications">
            <h2>Notifications</h2>
            <ul>
              {dashboardData.notifications.map((notification, index) => (
                <li key={`${notification.title}-${notification.createdAt}-${index}`} className={`notification notification--${notification.level}`}>
                  <div>
                    <strong>{notification.title}</strong>
                    <p>{notification.message}</p>
                  </div>
                  <small>{new Date(notification.createdAt).toLocaleTimeString()}</small>
                </li>
              ))}
            </ul>
          </article>
        </section>
      </div>
    </div>
  );
};

export default UserDashboardPage;
