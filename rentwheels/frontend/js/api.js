// Replaces dao.js: instead of localStorage, every operation is an HTTP call to the Java/JDBC backend.
// Errors from the server ({"error": "..."}) are turned into AppError so run() shows them as friendly toasts.
const Api = {
  async call(method, url, body) {
    let res;
    try {
      res = await fetch('/api' + url, { method, headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
    } catch (e) { throw new AppError('Cannot reach the server. Is the backend running at this address?'); }
    let data = null;
    try { data = await res.json(); } catch (e) { /* empty / non-JSON body */ }
    if (!res.ok) throw new AppError((data && data.error) || 'Request failed (' + res.status + ').');
    return data;
  },
  // users
  login: (email, password) => Api.call('POST', '/login', { email, password }),
  register: (name, email, password) => Api.call('POST', '/register', { name, email, password }),
  // vehicles (rebuilt into Car / Bike objects so calculateRent() and describe() work)
  async vehicles() { return (await Api.call('GET', '/vehicles')).map(Vehicle.fromObject); },
  addVehicle: o => Api.call('POST', '/vehicles', o),
  updateVehicle: (id, o) => Api.call('PUT', '/vehicles/' + id, o),
  deleteVehicle: id => Api.call('DELETE', '/vehicles/' + id),
  // bookings
  bookings: userId => Api.call('GET', '/bookings' + (userId ? '?userId=' + userId : '')),
  book: (userId, vehicleId, start, end) => Api.call('POST', '/bookings', { userId, vehicleId, start, end }),
  cancel: id => Api.call('POST', '/bookings/' + id + '/cancel'),
  processReturn: id => Api.call('POST', '/bookings/' + id + '/return'),
  removeBooking: id => Api.call('DELETE', '/bookings/' + id)
};
