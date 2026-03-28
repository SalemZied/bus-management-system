import React, { useState, useEffect } from 'react';
import { CircleMarker, MapContainer, Popup, TileLayer, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import '../styles/route.css';
import jsPDF from 'jspdf';
import html2canvas from 'html2canvas';
import Sidebar from '../components/Sidebar/Sidebar';
import TopNav from '../components/TopNav/TopNav';

const TUNISIA_CENTER = [34.2, 9.6];

const MapClickHandler = ({ activePicker, onPick }) => {
  useMapEvents({
    click(event) {
      if (!activePicker) {
        return;
      }
      onPick(activePicker, event.latlng);
    },
  });

  return null;
};

const RoutePage = () => {
  const [routes, setRoutes] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [source, setSource] = useState('');
  const [destination, setDestination] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [sourceLatitude, setSourceLatitude] = useState('');
  const [sourceLongitude, setSourceLongitude] = useState('');
  const [destinationLatitude, setDestinationLatitude] = useState('');
  const [destinationLongitude, setDestinationLongitude] = useState('');
  const [configuredBusCount, setConfiguredBusCount] = useState('1');
  const [departureTimes, setDepartureTimes] = useState('06:00,07:00,08:00');
  const [activePicker, setActivePicker] = useState('source');
  const [editRouteId, setEditRouteId] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [formError, setFormError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/routes');
        if (response.ok) {
          const data = await response.json();
          setRoutes(data);
        } else {
          console.error('Failed to fetch route data. Please try again.');
        }
      } catch (error) {
        console.error('An error occurred:', error);
      }
    };

    fetchData();
  }, []);

  const syncRouteCenter = (srcLat, srcLng, dstLat, dstLng) => {
    if (srcLat && srcLng && dstLat && dstLng) {
      const avgLat = (Number(srcLat) + Number(dstLat)) / 2;
      const avgLng = (Number(srcLng) + Number(dstLng)) / 2;
      setLatitude(avgLat.toFixed(6));
      setLongitude(avgLng.toFixed(6));
    }
  };

  const resetForm = () => {
    setName('');
    setSource('');
    setDestination('');
    setLatitude('');
    setLongitude('');
    setSourceLatitude('');
    setSourceLongitude('');
    setDestinationLatitude('');
    setDestinationLongitude('');
    setConfiguredBusCount('1');
    setDepartureTimes('06:00,07:00,08:00');
    setActivePicker('source');
    setShowForm(false);
    setEditRouteId(null);
    setFormError('');
  };

  const handleMapPick = (picker, latlng) => {
    const pickedLat = latlng.lat.toFixed(6);
    const pickedLng = latlng.lng.toFixed(6);

    if (picker === 'source') {
      setSourceLatitude(pickedLat);
      setSourceLongitude(pickedLng);
      if (!source) {
        setSource(`Stop ${pickedLat}, ${pickedLng}`);
      }
      syncRouteCenter(pickedLat, pickedLng, destinationLatitude, destinationLongitude);
      return;
    }

    setDestinationLatitude(pickedLat);
    setDestinationLongitude(pickedLng);
    if (!destination) {
      setDestination(`Stop ${pickedLat}, ${pickedLng}`);
    }
    syncRouteCenter(sourceLatitude, sourceLongitude, pickedLat, pickedLng);
  };

  const handleFormSubmit = async (e) => {
    e.preventDefault();
    setFormError('');

    const formData = {
      name,
      source,
      destination,
      latitude: Number(latitude),
      longitude: Number(longitude),
      sourceLatitude: Number(sourceLatitude),
      sourceLongitude: Number(sourceLongitude),
      destinationLatitude: Number(destinationLatitude),
      destinationLongitude: Number(destinationLongitude),
      configuredBusCount: Number(configuredBusCount),
      departureTimes,
    };

    try {
      let response;

      if (editRouteId) {
        response = await fetch(`http://localhost:8080/api/routes/${editRouteId}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(formData),
        });
      } else {
        response = await fetch('http://localhost:8080/api/routes', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(formData),
        });
      }

      if (response.ok) {
        const updatedRoute = await response.json();
        if (editRouteId) {
          const updatedRoutes = routes.map((route) => (route.id === updatedRoute.id ? updatedRoute : route));
          setRoutes(updatedRoutes);
        } else {
          setRoutes([...routes, updatedRoute]);
        }

        resetForm();
      } else {
        setFormError('Unable to save route. Verify stop names and selected map coordinates.');
      }
    } catch (error) {
      console.error('An error occurred:', error);
      setFormError('Unable to save route. Check backend connectivity.');
    }
  };

  const handleEditRoute = (id) => {
    const selectedRoute = routes.find((route) => route.id === id);
    if (selectedRoute) {
      setName(selectedRoute.name || '');
      setSource(selectedRoute.source || '');
      setDestination(selectedRoute.destination || '');
      setLatitude(selectedRoute.latitude ?? '');
      setLongitude(selectedRoute.longitude ?? '');
      setSourceLatitude(selectedRoute.sourceLatitude ?? '');
      setSourceLongitude(selectedRoute.sourceLongitude ?? '');
      setDestinationLatitude(selectedRoute.destinationLatitude ?? '');
      setDestinationLongitude(selectedRoute.destinationLongitude ?? '');
      setConfiguredBusCount(String(selectedRoute.configuredBusCount ?? 1));
      setDepartureTimes(selectedRoute.departureTimes || '06:00,07:00,08:00');
      setEditRouteId(id);
      setShowForm(true);
      setFormError('');
    }
  };

  const handleDeleteRoute = async (id) => {
    try {
      const response = await fetch(`http://localhost:8080/api/routes/${id}`, {
        method: 'DELETE',
      });

      if (response.ok) {
        const updatedRoutes = routes.filter((route) => route.id !== id);
        setRoutes(updatedRoutes);
      } else {
        console.error('Failed to delete route. Please try again.');
      }
    } catch (error) {
      console.error('An error occurred:', error);
    }
  };

  const handleGeneratePDF = () => {
    const tableElement = document.querySelector('.route-table');

    if (tableElement) {
      html2canvas(tableElement).then((canvas) => {
        const imgData = canvas.toDataURL('image/png');
        const pdf = new jsPDF();
        const imgProps = pdf.getImageProperties(imgData);
        const pdfWidth = pdf.internal.pageSize.getWidth();
        const pdfHeight = (imgProps.height * pdfWidth) / imgProps.width;

        pdf.addImage(imgData, 'PNG', 10, 10, pdfWidth - 20, pdfHeight - 10);
        pdf.save('route_details.pdf');
      });
    }
  };

  const filteredRoutes = routes.filter((route) => {
    const query = searchQuery.toLowerCase();
    return [route.name, route.source, route.destination]
      .filter(Boolean)
      .some((field) => field.toLowerCase().includes(query));
  });

  return (
    <div className="layout">
      <Sidebar />
      <div className="main__layout">
        <TopNav />
        <div className="route-container" style={{ marginTop: '5%' }}>
          <h1>Route Details</h1>
          <div className="buttons-to-add">
            <button className="add-button" onClick={() => setShowForm(true)}>
              Add Route
            </button>
            <button className="generate-pdf-button" onClick={handleGeneratePDF}>
              Generate PDF
            </button>
          </div>
          {showForm && (
            <form className="add-form" onSubmit={handleFormSubmit}>
              <label className="form-field-input-data" htmlFor="routeName">
                Route Name:
              </label>
              <input type="text" id="routeName" value={name} onChange={(e) => setName(e.target.value)} required />

              <label className="form-field-input-data" htmlFor="source">
                Departure stop name:
              </label>
              <input type="text" id="source" value={source} onChange={(e) => setSource(e.target.value)} required />

              <label className="form-field-input-data" htmlFor="destination">
                Destination stop name:
              </label>
              <input
                type="text"
                id="destination"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                required
              />

              <div className="map-picker-controls">
                <button type="button" className={activePicker === 'source' ? 'map-mode active' : 'map-mode'} onClick={() => setActivePicker('source')}>
                  Pick departure on map
                </button>
                <button
                  type="button"
                  className={activePicker === 'destination' ? 'map-mode active' : 'map-mode'}
                  onClick={() => setActivePicker('destination')}
                >
                  Pick destination on map
                </button>
              </div>

              <p className="map-helper-text">Active mode: {activePicker}. Click on the map to capture real coordinates.</p>

              <div className="route-map-wrapper">
                <MapContainer center={TUNISIA_CENTER} zoom={7} scrollWheelZoom className="route-leaflet-map">
                  <TileLayer
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; CARTO'
                    url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
                  />
                  <MapClickHandler activePicker={activePicker} onPick={handleMapPick} />

                  {sourceLatitude && sourceLongitude && (
                    <CircleMarker center={[Number(sourceLatitude), Number(sourceLongitude)]} radius={8} pathOptions={{ color: '#16a34a' }}>
                      <Popup>Departure: {source}</Popup>
                    </CircleMarker>
                  )}

                  {destinationLatitude && destinationLongitude && (
                    <CircleMarker
                      center={[Number(destinationLatitude), Number(destinationLongitude)]}
                      radius={8}
                      pathOptions={{ color: '#dc2626' }}
                    >
                      <Popup>Destination: {destination}</Popup>
                    </CircleMarker>
                  )}
                </MapContainer>
              </div>

              <div className="coordinates-grid">
                <label>
                  Number of buses on this route:
                  <input
                    type="number"
                    min="1"
                    value={configuredBusCount}
                    onChange={(e) => setConfiguredBusCount(e.target.value)}
                    required
                  />
                </label>
                <label className="wide-field">
                  Departure times (HH:mm, comma separated):
                  <input
                    type="text"
                    value={departureTimes}
                    onChange={(e) => setDepartureTimes(e.target.value)}
                    placeholder="06:00,07:00,08:00"
                    required
                  />
                </label>
                <label>
                  Route Latitude:
                  <input type="number" step="any" value={latitude} onChange={(e) => setLatitude(e.target.value)} required />
                </label>
                <label>
                  Route Longitude:
                  <input type="number" step="any" value={longitude} onChange={(e) => setLongitude(e.target.value)} required />
                </label>
                <label>
                  Departure Latitude:
                  <input
                    type="number"
                    step="any"
                    value={sourceLatitude}
                    onChange={(e) => setSourceLatitude(e.target.value)}
                    required
                  />
                </label>
                <label>
                  Departure Longitude:
                  <input
                    type="number"
                    step="any"
                    value={sourceLongitude}
                    onChange={(e) => setSourceLongitude(e.target.value)}
                    required
                  />
                </label>
                <label>
                  Destination Latitude:
                  <input
                    type="number"
                    step="any"
                    value={destinationLatitude}
                    onChange={(e) => setDestinationLatitude(e.target.value)}
                    required
                  />
                </label>
                <label>
                  Destination Longitude:
                  <input
                    type="number"
                    step="any"
                    value={destinationLongitude}
                    onChange={(e) => setDestinationLongitude(e.target.value)}
                    required
                  />
                </label>
              </div>

              {formError && <p className="form-error">{formError}</p>}

              <div className="form-buttons">
                <button className="submit-button" type="submit">
                  {editRouteId ? 'Update' : 'Add'}
                </button>
                <button className="cancel-button" type="button" onClick={resetForm}>
                  Cancel
                </button>
              </div>
            </form>
          )}
          <div className="search-bar">
            <input
              type="text"
              placeholder="Search routes"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <div className="table-container">
            <table className="route-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Source</th>
                  <th>Destination</th>
                  <th>Route Coordinates</th>
                  <th>Bus Count</th>
                  <th>Departures</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredRoutes.map((route) => (
                  <tr key={route.id}>
                    <td>{route.name}</td>
                    <td>{route.source}</td>
                    <td>{route.destination}</td>
                    <td>
                      {route.latitude ?? '—'}, {route.longitude ?? '—'}
                    </td>
                    <td>{route.configuredBusCount ?? '—'}</td>
                    <td>{route.departureTimes || '—'}</td>
                    <td>
                      <button className="edit-button" onClick={() => handleEditRoute(route.id)}>
                        Edit
                      </button>
                      <button className="delete-button" onClick={() => handleDeleteRoute(route.id)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RoutePage;
