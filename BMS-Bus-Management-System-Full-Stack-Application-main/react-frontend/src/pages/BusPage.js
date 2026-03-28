import React, { useState, useEffect } from 'react';
import jsPDF from 'jspdf';
import html2canvas from 'html2canvas';
import '../styles/bus.css';
import Sidebar from '../components/Sidebar/Sidebar';
import TopNav from '../components/TopNav/TopNav';

const BusPage = () => {
  const [buses, setBuses] = useState([]);
  const [routes, setRoutes] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [busNumber, setBusNumber] = useState('');
  const [busName, setBusName] = useState('');
  const [capacity, setCapacity] = useState('');
  const [make, setMake] = useState('');
  const [model, setModel] = useState('');
  const [routeId, setRouteId] = useState('');
  const [driverId, setDriverId] = useState('');
  const [source, setSource] = useState('');
  const [destination, setDestination] = useState('');
  const [editBusId, setEditBusId] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [formError, setFormError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [busResponse, routeResponse, driverResponse] = await Promise.all([
          fetch('http://localhost:8080/api/buses'),
          fetch('http://localhost:8080/api/routes'),
          fetch('http://localhost:8080/api/drivers'),
        ]);

        if (busResponse.ok) {
          const data = await busResponse.json();
          setBuses(data);
        }

        if (routeResponse.ok) {
          const data = await routeResponse.json();
          setRoutes(data);
        }

        if (driverResponse.ok) {
          const data = await driverResponse.json();
          setDrivers(data);
        }
      } catch (error) {
        console.error('An error occurred:', error);
      }
    };

    fetchData();
  }, []);

  const selectedRoute = routes.find((route) => String(route.id) === String(routeId));
  const sourceOptions = selectedRoute ? [selectedRoute.source] : [];
  const destinationOptions = selectedRoute ? [selectedRoute.destination] : [];

  const resetForm = () => {
    setBusNumber('');
    setBusName('');
    setCapacity('');
    setMake('');
    setModel('');
    setRouteId('');
    setDriverId('');
    setSource('');
    setDestination('');
    setShowForm(false);
    setEditBusId(null);
    setFormError('');
  };

  const handleRouteChange = (value) => {
    setRouteId(value);
    const route = routes.find((entry) => String(entry.id) === String(value));
    setSource(route?.source || '');
    setDestination(route?.destination || '');
  };

  const handleFormSubmit = async (e) => {
    e.preventDefault();
    setFormError('');

    const formData = {
      busNumber,
      busName,
      capacity: Number(capacity),
      make,
      model,
      routeId: Number(routeId),
      driverId: Number(driverId),
      source,
      destination,
    };

    try {
      let response;

      if (editBusId) {
        response = await fetch(`http://localhost:8080/api/buses/${editBusId}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(formData),
        });
      } else {
        response = await fetch('http://localhost:8080/api/buses', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(formData),
        });
      }

      if (response.ok) {
        const updatedBus = await response.json();
        if (editBusId) {
          const updatedBuses = buses.map((bus) => {
            if (bus.id === updatedBus.id) {
              return updatedBus;
            }
            return bus;
          });
          setBuses(updatedBuses);
        } else {
          setBuses([...buses, updatedBus]);
        }

        resetForm();
        setSearchQuery('');
      } else {
        setFormError('Unable to save bus. Verify route, driver and selected stops.');
      }
    } catch (error) {
      setFormError('Unable to save bus. Check backend connectivity.');
      console.error('An error occurred:', error);
    }
  };

  const handleEditBus = (id) => {
    const selectedBus = buses.find((bus) => bus.id === id);
    if (selectedBus) {
      setBusNumber(selectedBus.busNumber || '');
      setBusName(selectedBus.busName || '');
      setCapacity(selectedBus.capacity || '');
      setMake(selectedBus.make || '');
      setModel(selectedBus.model || '');
      setRouteId(selectedBus.routeId || '');
      setDriverId(selectedBus.driverId || '');
      setSource(selectedBus.source || '');
      setDestination(selectedBus.destination || '');
      setEditBusId(id);
      setShowForm(true);
      setFormError('');
    }
  };

  const handleDeleteBus = async (id) => {
    try {
      const response = await fetch(`http://localhost:8080/api/buses/${id}`, {
        method: 'DELETE',
      });

      if (response.ok) {
        const updatedBuses = buses.filter((bus) => bus.id !== id);
        setBuses(updatedBuses);
      } else {
        console.error('Failed to delete bus. Please try again.');
      }
    } catch (error) {
      console.error('An error occurred:', error);
    }
  };

  const handleGeneratePDF = () => {
    const tableElement = document.querySelector('.bus-table');

    if (tableElement) {
      const actionBarElement = tableElement.querySelector('.action-bar');
      if (actionBarElement) {
        actionBarElement.style.display = 'none';
      }

      html2canvas(tableElement).then((canvas) => {
        if (actionBarElement) {
          actionBarElement.style.display = 'block';
        }

        const imgData = canvas.toDataURL('image/png');
        const pdf = new jsPDF();
        const imgProps = pdf.getImageProperties(imgData);
        const pdfWidth = pdf.internal.pageSize.getWidth();
        const pdfHeight = (imgProps.height * pdfWidth) / imgProps.width;

        pdf.addImage(imgData, 'PNG', 10, 10, pdfWidth - 20, pdfHeight - 10);
        pdf.save('bus_details.pdf');
      });
    }
  };

  const filteredBuses = buses.filter((bus) => {
    const searchFields = [bus.busNumber, bus.busName, bus.model, bus.source, bus.destination]
      .filter(Boolean)
      .map((value) => value.toLowerCase());
    return searchFields.some((field) => field.includes(searchQuery.toLowerCase()));
  });

  const getRouteName = (id) => routes.find((route) => route.id === id)?.name || '—';
  const getDriverName = (id) => drivers.find((driver) => driver.id === id)?.name || '—';

  return (
    <div className="layout">
      <Sidebar />
      <div className="main__layout">
        <TopNav />
        <div className="bus-container" style={{ marginTop: '5%' }}>
          <h1>Bus Details</h1>
          <div className="buttons-to-add">
            <button className="add-button" onClick={() => setShowForm(true)}>
              Add Bus
            </button>
            <button className="generate-pdf-button" onClick={handleGeneratePDF}>
              Generate PDF
            </button>
          </div>

          {showForm && (
            <div className="add-form">
              <form onSubmit={handleFormSubmit}>
                <ul className="form-list">
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="busNumber">Bus Number:</label>
                    <input
                      id="busNumber"
                      type="text"
                      value={busNumber}
                      onChange={(e) => setBusNumber(e.target.value)}
                      required
                    />
                  </li>
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="busName">Bus Name:</label>
                    <input
                      id="busName"
                      type="text"
                      value={busName}
                      onChange={(e) => setBusName(e.target.value)}
                      required
                    />
                  </li>
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="capacity">Capacity:</label>
                    <input
                      id="capacity"
                      type="number"
                      min="1"
                      value={capacity}
                      onChange={(e) => setCapacity(e.target.value)}
                      required
                    />
                  </li>
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="route">Associated Route:</label>
                    <select id="route" value={routeId} onChange={(e) => handleRouteChange(e.target.value)} required>
                      <option value="">Select a route</option>
                      {routes.map((route) => (
                        <option key={route.id} value={route.id}>
                          {route.name} ({route.source} → {route.destination})
                        </option>
                      ))}
                    </select>
                  </li>
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="source">Departure:</label>
                    <select id="source" value={source} onChange={(e) => setSource(e.target.value)} required>
                      <option value="">Select departure</option>
                      {sourceOptions.map((entry) => (
                        <option key={entry} value={entry}>
                          {entry}
                        </option>
                      ))}
                    </select>
                  </li>
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="destination">Destination:</label>
                    <select
                      id="destination"
                      value={destination}
                      onChange={(e) => setDestination(e.target.value)}
                      required
                    >
                      <option value="">Select destination</option>
                      {destinationOptions.map((entry) => (
                        <option key={entry} value={entry}>
                          {entry}
                        </option>
                      ))}
                    </select>
                  </li>
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="driver">Associated Driver:</label>
                    <select id="driver" value={driverId} onChange={(e) => setDriverId(e.target.value)} required>
                      <option value="">Select a driver</option>
                      {drivers.map((driver) => (
                        <option key={driver.id} value={driver.id}>
                          {driver.name} ({driver.licenseNumber})
                        </option>
                      ))}
                    </select>
                  </li>
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="make">Make:</label>
                    <input id="make" type="text" value={make} onChange={(e) => setMake(e.target.value)} />
                  </li>
                  <li className="form-item">
                    <label className="form-field-input-data" htmlFor="model">Model:</label>
                    <input id="model" type="text" value={model} onChange={(e) => setModel(e.target.value)} />
                  </li>
                </ul>
                {formError && <p className="form-error">{formError}</p>}
                <div className="form-buttons">
                  <button className="submit-button" type="submit">
                    {editBusId ? 'Update' : 'Submit'}
                  </button>
                  <button className="cancel-button" type="button" onClick={resetForm}>
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}

          <div className="search-container">
            <input
              type="text"
              placeholder="Search buses"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          {filteredBuses.length > 0 ? (
            <table className="bus-table">
              <thead>
                <tr>
                  <th>Bus Number</th>
                  <th>Bus Name</th>
                  <th>Route</th>
                  <th>Departure</th>
                  <th>Destination</th>
                  <th>Driver</th>
                  <th>Capacity</th>
                  <th>Make</th>
                  <th>Model</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredBuses.map((bus) => (
                  <tr key={bus.id}>
                    <td>{bus.busNumber}</td>
                    <td>{bus.busName}</td>
                    <td>{getRouteName(bus.routeId)}</td>
                    <td>{bus.source || '—'}</td>
                    <td>{bus.destination || '—'}</td>
                    <td>{getDriverName(bus.driverId)}</td>
                    <td>{bus.capacity}</td>
                    <td>{bus.make}</td>
                    <td>{bus.model}</td>
                    <td>
                      <button className="edit-button" onClick={() => handleEditBus(bus.id)}>
                        Edit
                      </button>
                      <button className="delete-button" onClick={() => handleDeleteBus(bus.id)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p>No buses found.</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default BusPage;
