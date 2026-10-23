// [RUBRIC: Thread] Runs on a SEPARATE thread (Web Worker). It cannot touch the DOM or localStorage,
// so the main thread sends it the active bookings and it posts back those ending within 24 hours.
let bookings = [];
function check() {
  const now = Date.now();
  const due = bookings.filter(b => { const t = new Date(b.end + 'T12:00').getTime() - now; return t >= 0 && t <= 24 * 3600 * 1000; });
  postMessage(due);
}
onmessage = e => { bookings = e.data; check(); };   // main thread -> worker
setInterval(check, 10000);                           // periodic check every 10 seconds
