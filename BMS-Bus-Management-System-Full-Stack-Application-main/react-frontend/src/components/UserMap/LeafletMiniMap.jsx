import { useEffect, useRef } from "react";

const LEAFLET_CSS_ID = "leaflet-css-cdn";
const LEAFLET_JS_ID = "leaflet-js-cdn";

const addLeafletCss = () => {
  if (document.getElementById(LEAFLET_CSS_ID)) {
    return;
  }

  const link = document.createElement("link");
  link.id = LEAFLET_CSS_ID;
  link.rel = "stylesheet";
  link.href = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";
  link.integrity = "sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=";
  link.crossOrigin = "";

  document.head.appendChild(link);
};

const loadLeafletScript = () => {
  if (window.L) {
    return Promise.resolve(window.L);
  }

  const existingScript = document.getElementById(LEAFLET_JS_ID);
  if (existingScript) {
    return new Promise((resolve) => {
      existingScript.addEventListener("load", () => resolve(window.L), { once: true });
    });
  }

  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.id = LEAFLET_JS_ID;
    script.src = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
    script.integrity = "sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=";
    script.crossOrigin = "";
    script.async = true;
    script.onload = () => resolve(window.L);
    script.onerror = () => reject(new Error("Leaflet failed to load"));

    document.body.appendChild(script);
  });
};

const LeafletMiniMap = ({ buses, mapProviderUrl, mapApiKey }) => {
  const containerRef = useRef(null);
  const mapRef = useRef(null);

  useEffect(() => {
    let isMounted = true;

    const initMap = async () => {
      addLeafletCss();
      const leaflet = await loadLeafletScript();

      if (!isMounted || !containerRef.current || mapRef.current) {
        return;
      }

      const center = [40.7194, -74.0060];

      const map = leaflet.map(containerRef.current, {
        zoomControl: true,
        attributionControl: true,
      }).setView(center, 12);

      const tileUrl = mapApiKey
        ? `${mapProviderUrl}?key=${mapApiKey}`
        : mapProviderUrl;

      leaflet
        .tileLayer(tileUrl, {
          maxZoom: 19,
          attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        })
        .addTo(map);

      mapRef.current = map;
    };

    initMap();

    return () => {
      isMounted = false;
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
    };
  }, [mapApiKey, mapProviderUrl]);

  useEffect(() => {
    if (!mapRef.current || !window.L) {
      return;
    }

    const map = mapRef.current;

    map.eachLayer((layer) => {
      if (layer instanceof window.L.Marker) {
        map.removeLayer(layer);
      }
    });

    const markers = buses.map((bus) => {
      const marker = window.L.marker([bus.latitude, bus.longitude]).addTo(map);
      marker.bindPopup(
        `<strong>${bus.busNumber}</strong><br/>${bus.routeName}<br/>Destination: ${bus.destination}<br/>ETA: ${bus.etaMinutes} min`
      );
      return marker;
    });

    if (markers.length === 1) {
      map.setView(markers[0].getLatLng(), 14);
    }

    if (markers.length > 1) {
      const bounds = window.L.latLngBounds(markers.map((marker) => marker.getLatLng()));
      map.fitBounds(bounds.pad(0.2));
    }
  }, [buses]);

  return <div ref={containerRef} className="leaflet-mini-map" />;
};

export default LeafletMiniMap;
