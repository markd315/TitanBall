const IDLE_LIMIT_MS = 4 * 60 * 60 * 1000; // 4 hours
export function recordUserActivity() {
  if (!window.warmExpired) window.lastUserActivity = Date.now();
}

export async function warmServer() {
  if (window.warmExpired) return;
  if (!window.lastUserActivity) window.lastUserActivity = Date.now();
  if (Date.now() - window.lastUserActivity > IDLE_LIMIT_MS) {
    window.warmExpired = true;
    console.log("[PilotLight] Session expired after 4 hours of inactivity. Stopping server warm pings.");
    const expiredOverlay = document.getElementById('session-expired-overlay');
    if (expiredOverlay) expiredOverlay.style.display = 'flex';
    return;
  }
  try {
    const res = await fetch('/pages/titanball/api/warm', { method: 'POST', headers: { 'Content-Type': 'application/json' } });
    const data = await res.json();
    if ((data.status === 'starting' || data.status === 'unknown' || !data.serverUrl) && !window.warmExpired) {
      setTimeout(warmServer, 5000);
    }
  } catch (e) {
    console.error("[PilotLight] Failed to warm server:", e);
    if (!window.warmExpired) setTimeout(warmServer, 10000);
  }
}

