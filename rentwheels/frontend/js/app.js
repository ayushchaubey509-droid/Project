let session = JSON.parse(sessionStorage.getItem('user') || 'null');
let ui = { tab: 'browse', q: '', type: 'All', avail: true, sort: 'asc' };
let vehicleList = [];                      // last vehicle list loaded from the server (filtered/sorted in the browser)
let worker = null, fallbackTimer = null;

const $ = s => document.querySelector(s);
const esc = s => String(s).replace(/[&<>"']/g, c => '&#' + c.charCodeAt(0) + ';');
const todayStr = () => new Date(Date.now() - new Date().getTimezoneOffset() * 6e4).toISOString().slice(0, 10);
const modal = () => bootstrap.Modal.getOrCreateInstance($('#modal'));
function openModal(html) { $('#modalBody').innerHTML = html; modal().show(); }
function toast(msg, type = 'danger') {
  const d = document.createElement('div'); d.className = `alert alert-${type} shadow-sm py-2`; d.textContent = msg;
  $('#toasts').appendChild(d); setTimeout(() => d.remove(), 3500);
}
async function loadVehicles() { vehicleList = await Api.vehicles(); return vehicleList; }
const vehicleById = id => vehicleList.find(v => v.id === +id) || null;

// [RUBRIC: Exception Handling] Every button click goes through run(): try / catch / finally.
// AppError (client checks or {"error"} from the server) -> friendly message. Anything else -> generic message.
async function run(fn, ...args) {
  try { await fn(...args); }
  catch (e) { if (e instanceof AppError) toast(e.message); else { console.error(e); toast('Something went wrong. Please try again.'); } }
  finally { pushBookings(); }
}

// ---------- Business logic (the server re-checks every rule; these checks just give instant feedback) ----------
function parseDays(start, end) {
  if (!start || !end) throw new ValidationError('Select both a start and a return date.');
  const s = new Date(start + 'T00:00'), e = new Date(end + 'T00:00'), t = new Date(todayStr() + 'T00:00');
  if (s < t) throw new InvalidDateError('Start date cannot be in the past.');
  if (e <= s) throw new InvalidDateError('Return date must be after the start date.');
  return Math.round((e - s) / 864e5);
}
async function book(id) {
  const start = $('#bs').value, end = $('#be').value;
  parseDays(start, end);
  try { await Api.book(session.id, +id, start, end); }
  catch (e) { await loadVehicles().catch(() => {}); if ($('#cards')) showCards(); throw e; }   // e.g. someone else just booked it
  modal().hide(); toast('Booking confirmed.', 'success'); await render();
}
async function cancelBooking(id) {
  await Api.cancel(id); toast('Booking cancelled.', 'success'); await render();
}
async function processReturn(id) {
  const b = await Api.processReturn(id);
  toast(`Vehicle returned. Final amount Rs ${b.finalAmount}` + (b.lateDays ? ` (incl. ${b.lateDays} late day(s))` : ''), 'success'); await render();
}
async function login() {
  const email = $('#le').value.trim(), pw = $('#lp').value;
  if (!email || !pw) throw new ValidationError('Enter your email and password.');
  const u = await Api.login(email, pw);
  session = { id: u.id, name: u.name, role: u.role }; sessionStorage.setItem('user', JSON.stringify(session));
  ui.tab = 'browse'; startReminders(); await render();
}
async function register() {
  const name = $('#rn').value.trim(), email = $('#re').value.trim().toLowerCase(), pw = $('#rp').value;
  if (!name || !/^\S+@\S+\.\S+$/.test(email) || pw.length < 4) throw new ValidationError('Enter a name, a valid email and a password of 4+ characters.');
  await Api.register(name, email, pw); toast('Account created. You can log in now.', 'success');
}
function logout() { stopReminders(); session = null; sessionStorage.removeItem('user'); $('#banner').classList.add('d-none'); render(); }
async function saveVehicle(id) {
  const type = id ? vehicleById(id).type : $('#vt').value;
  const o = { type, name: $('#vn').value.trim(), regNo: $('#vr').value.trim(), pricePerDay: +$('#vp').value };
  if (!o.name || !o.regNo || !(o.pricePerDay > 0)) throw new ValidationError('Name, registration no. and a positive price are required.');
  const x = +$('#vx').value;
  if (type === 'Car') { o.seats = x; o.fuelType = $('#vf').value.trim() || 'Petrol'; } else o.engineCC = x;
  if (id) await Api.updateVehicle(id, o); else await Api.addVehicle(o);
  modal().hide(); toast('Vehicle saved.', 'success'); await render();
}
async function deleteVehicle(id) {
  if (!confirm('Delete this vehicle?')) return;
  await Api.deleteVehicle(id); toast('Vehicle deleted.', 'success'); await render();
}
async function removeBooking(id) { await Api.removeBooking(id); await render(); }

// ---------- [RUBRIC: Thread] Reminder worker ----------
async function pushBookings() {
  if (!session) return;
  try {
    const all = await Api.bookings(session.role === 'ADMIN' ? null : session.id);
    const list = all.filter(b => b.status === 'ACTIVE').map(b => ({ id: b.id, end: b.end, name: b.vehicleName || 'Vehicle' }));
    window.lastList = list;
    if (worker) worker.postMessage(list);
  } catch (e) { /* reminders are best-effort; ignore network errors here */ }
}
function showDue(due) {
  const el = $('#banner');
  if (!due.length) return el.classList.add('d-none');
  el.innerHTML = '<b>Reminder:</b> ' + due.map(b => `${esc(b.name)} is due back on ${b.end}`).join(' | ');
  el.classList.remove('d-none');
}
function startReminders() {
  stopReminders();
  const fallback = () => { // Browsers block Workers on file:// URLs, so we poll on the main thread instead
    if (fallbackTimer) return;
    fallbackTimer = setInterval(() => showDue((window.lastList || []).filter(b => { const t = new Date(b.end + 'T12:00') - Date.now(); return t >= 0 && t <= 864e5; })), 10000);
    pushBookings();
  };
  try { worker = new Worker('js/reminder-worker.js'); worker.onmessage = e => showDue(e.data); worker.onerror = () => { worker = null; fallback(); }; }
  catch (e) { worker = null; fallback(); }
  pushBookings();
}
function stopReminders() { if (worker) worker.terminate(); worker = null; clearInterval(fallbackTimer); fallbackTimer = null; }

// ---------- [RUBRIC: GUI] Views ----------
async function render() {
  $('#navRight').innerHTML = session ? `<span class="text-white me-3">${esc(session.name)} (${session.role})</span><button class="btn btn-sm btn-outline-light" onclick="logout()">Logout</button>` : '';
  if (!session) return authView();
  try { await (session.role === 'ADMIN' ? adminView() : userView()); }
  catch (e) {
    if (e instanceof AppError) toast(e.message); else { console.error(e); toast('Something went wrong. Please try again.'); }
  }
}
function authView() {
  $('#view').innerHTML = `<div class="row g-3 justify-content-center">
  <div class="col-md-5"><div class="card p-3"><h5>Log in</h5>
    <input id="le" class="form-control mb-2" placeholder="Email"><input id="lp" type="password" class="form-control mb-2" placeholder="Password">
    <button class="btn btn-primary" onclick="run(login)">Log in</button>
    <small class="text-muted mt-2">Demo: admin@rent.com / admin123, user@rent.com / user123</small></div></div>
  <div class="col-md-5"><div class="card p-3"><h5>Create account</h5>
    <input id="rn" class="form-control mb-2" placeholder="Full name"><input id="re" class="form-control mb-2" placeholder="Email">
    <input id="rp" type="password" class="form-control mb-2" placeholder="Password (4+ characters)">
    <button class="btn btn-outline-dark" onclick="run(register)">Register</button></div></div></div>`;
}
function setTab(t) { ui.tab = t; render(); }
async function userView() {
  const tab = (k, l) => `<li class="nav-item"><a class="nav-link ${ui.tab === k ? 'active' : ''}" href="#" onclick="setTab('${k}');return false">${l}</a></li>`;
  const html = `<ul class="nav nav-tabs mb-3">${tab('browse', 'Vehicles')}${tab('history', 'My rentals')}</ul><div id="content"></div>`;
  if (ui.tab === 'browse') { await loadVehicles(); $('#view').innerHTML = html; browse(); }
  else { const mine = await Api.bookings(session.id); $('#view').innerHTML = html; $('#content').innerHTML = bookingTable(mine, false); }
}
function browse() {
  $('#content').innerHTML = `<div class="row g-2 mb-3">
    <div class="col-md-4"><input class="form-control" placeholder="Search by name" value="${esc(ui.q)}" oninput="ui.q=this.value;showCards()"></div>
    <div class="col-md-2"><select class="form-select" onchange="ui.type=this.value;showCards()">${['All', 'Car', 'Bike'].map(t => `<option ${ui.type === t ? 'selected' : ''}>${t}</option>`).join('')}</select></div>
    <div class="col-md-3"><select class="form-select" onchange="ui.sort=this.value;showCards()"><option value="asc">Price: low to high</option><option value="desc" ${ui.sort === 'desc' ? 'selected' : ''}>Price: high to low</option></select></div>
    <div class="col-md-3 pt-2"><label><input type="checkbox" ${ui.avail ? 'checked' : ''} onchange="ui.avail=this.checked;showCards()"> Available only</label></div></div>
    <div id="cards" class="row g-3"></div>`;
  showCards();
}
function showCards() {
  // [RUBRIC: Collections] Array + filter + sort + map (on the list the server sent us)
  const list = vehicleList
    .filter(v => (ui.type === 'All' || v.type === ui.type) && (!ui.avail || v.available) && v.name.toLowerCase().includes(ui.q.toLowerCase()))
    .sort((a, b) => ui.sort === 'asc' ? a.pricePerDay - b.pricePerDay : b.pricePerDay - a.pricePerDay);
  $('#cards').innerHTML = list.length ? list.map(v => `<div class="col-md-4"><div class="card p-3 h-100">
    <div class="d-flex justify-content-between"><b>${esc(v.name)}</b><span class="badge ${v.available ? 'bg-success' : 'bg-secondary'}">${v.available ? 'Available' : 'Booked'}</span></div>
    <small class="text-muted">${v.type} - ${esc(v.regNo)}</small><small>${esc(v.describe())}</small>
    <div class="price my-2">Rs ${v.pricePerDay}<small class="text-muted fs-6"> / day</small></div>
    <button class="btn btn-primary mt-auto" ${v.available ? '' : 'disabled'} onclick="openBook(${v.id})">Book</button></div></div>`).join('')
    : '<p class="text-muted">No vehicles match. Clear a filter to see more.</p>';
}
function openBook(id) {
  const v = vehicleById(id);
  openModal(`<div class="modal-header"><h5>Book ${esc(v.name)}</h5></div><div class="modal-body">
    <label>Start date</label><input id="bs" type="date" min="${todayStr()}" class="form-control mb-2" oninput="estimate(${id})">
    <label>Return date</label><input id="be" type="date" min="${todayStr()}" class="form-control mb-2" oninput="estimate(${id})">
    <div id="est" class="text-muted">Pick dates to see the price.</div></div>
    <div class="modal-footer"><button class="btn btn-primary" onclick="run(book,${id})">Confirm booking</button></div>`);
}
function estimate(id) {
  const el = $('#est');
  try {
    const s = $('#bs').value, e = $('#be').value; if (!s || !e) return;
    const days = parseDays(s, e); el.className = 'text-success fw-bold'; el.textContent = `${days} day(s): Rs ${vehicleById(id).calculateRent(days)}`;
  } catch (err) { el.className = 'text-danger'; el.textContent = err.message; }
}
function bookingTable(list, admin) {
  // The server JOINs the vehicle and user names into every booking row.
  const col = { ACTIVE: 'primary', RETURNED: 'success', CANCELLED: 'secondary' };
  if (!list.length) return '<p class="text-muted">No rentals yet. Book a vehicle to see it here.</p>';
  return `<div class="table-responsive"><table class="table align-middle mb-0"><thead><tr><th>#</th>${admin ? '<th>User</th>' : ''}<th>Vehicle</th><th>From</th><th>To</th><th>Amount</th><th>Status</th><th></th></tr></thead><tbody>` +
    list.slice().reverse().map(b => `<tr><td>${b.id}</td>${admin ? `<td>${esc(b.userName || '-')}</td>` : ''}<td>${esc(b.vehicleName || '(removed)')}</td><td>${b.start}</td><td>${b.end}</td>
    <td>Rs ${b.finalAmount ?? b.amount}</td><td><span class="badge bg-${col[b.status]}">${b.status}</span></td><td class="text-end">${
      b.status === 'ACTIVE' ? (admin ? `<button class="btn btn-sm btn-success" onclick="run(processReturn,${b.id})">Process return</button> ` : '') + `<button class="btn btn-sm btn-outline-danger" onclick="run(cancelBooking,${b.id})">Cancel</button>`
      : admin ? `<button class="btn btn-sm btn-outline-secondary" onclick="run(removeBooking,${b.id})">Remove</button>` : ''}</td></tr>`).join('') + '</tbody></table></div>';
}
async function adminView() {
  const [vehicles, allBookings] = await Promise.all([loadVehicles(), Api.bookings()]);
  const rows = vehicles.map(v => `<tr><td>${v.id}</td><td>${v.type}</td><td>${esc(v.name)}</td><td>${esc(v.regNo)}</td><td>Rs ${v.pricePerDay}</td>
    <td><span class="badge ${v.available ? 'bg-success' : 'bg-secondary'}">${v.available ? 'Available' : 'Booked'}</span></td>
    <td class="text-end"><button class="btn btn-sm btn-outline-dark" onclick="openVehicleForm(${v.id})">Edit / price</button>
    <button class="btn btn-sm btn-outline-danger" onclick="run(deleteVehicle,${v.id})">Delete</button></td></tr>`).join('');
  $('#view').innerHTML = `<div class="d-flex justify-content-between mb-2"><h5>Fleet</h5><button class="btn btn-primary btn-sm" onclick="openVehicleForm(0)">Add vehicle</button></div>
    <div class="table-responsive mb-4"><table class="table align-middle mb-0"><thead><tr><th>#</th><th>Type</th><th>Name</th><th>Reg. no</th><th>Price/day</th><th>Status</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>
    <h5>Bookings and returns</h5>${bookingTable(allBookings, true)}`;
}
function openVehicleForm(id) {
  const v = id ? vehicleById(id) : { type: 'Car', name: '', regNo: '', pricePerDay: '', seats: 4, fuelType: 'Petrol', engineCC: 150 };
  openModal(`<div class="modal-header"><h5>${id ? 'Edit' : 'Add'} vehicle</h5></div><div class="modal-body">
    <select id="vt" class="form-select mb-2" ${id ? 'disabled' : ''}><option ${v.type === 'Car' ? 'selected' : ''}>Car</option><option ${v.type === 'Bike' ? 'selected' : ''}>Bike</option></select>
    <input id="vn" class="form-control mb-2" placeholder="Name" value="${esc(v.name)}"><input id="vr" class="form-control mb-2" placeholder="Registration no." value="${esc(v.regNo)}">
    <input id="vp" type="number" min="1" class="form-control mb-2" placeholder="Price per day" value="${v.pricePerDay}">
    <input id="vx" type="number" min="1" class="form-control mb-2" placeholder="Seats (Car) or engine CC (Bike)" value="${v.type === 'Car' ? v.seats : v.engineCC}">
    <input id="vf" class="form-control" placeholder="Fuel type (Car only)" value="${esc(v.fuelType || 'Petrol')}"></div>
    <div class="modal-footer"><button class="btn btn-primary" onclick="run(saveVehicle,${id})">Save vehicle</button></div>`);
}

render().then(() => { if (session) startReminders(); });
