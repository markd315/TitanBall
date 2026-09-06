const IDLE_LIMIT_MS = 4 * 60 * 60 * 1000; // 4 hours
let serverWarmedState = false, wasWarming = false;

export function recordUserActivity() {
  if (!window.warmExpired) window.lastUserActivity = Date.now();
}

export function isServerWarmed() { return serverWarmedState; }
export function markServerWarming() { wasWarming = true; setServerWarmed(false); }

export function setServerWarmed(warmed) {
  serverWarmedState = warmed;
  if (!warmed) wasWarming = true;
  console.log(`[PilotLight] Server warming state changed: ${warmed ? 'WARMED (Ready)' : 'WARMING (Wait 1-2 mins)'} (wasWarming=${wasWarming})`);
  updateBadgeUI(warmed);
}

function updateBadgeUI(warmed) {
  document.querySelectorAll('.server-status-badge').forEach(badge => {
    if (warmed && !wasWarming) return badge.style.display = 'none';
    if (!warmed) wasWarming = true;
    badge.style.display = 'flex';
    badge.className = `server-status-badge ${warmed ? 'warmed' : 'warming'}`;
    badge.innerHTML = warmed
      ? '<span class="status-checkmark">✓</span><span class="status-text">servers online</span>'
      : '<span class="status-spinner"></span><span class="status-text">servers warming up, wait 1-2 mins</span>';
  });
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
    if (!res.ok) {
      console.warn(`[PilotLight] Warm ping returned status: ${res.status}`);
      setServerWarmed(false);
      if (!window.warmExpired) setTimeout(warmServer, 10000);
      return;
    }
    const data = await res.json();
    console.log("[PilotLight] Warm ping response:", data);
    const ready = data.status === 'ready' || data.status === 'running';
    setServerWarmed(ready);
    if (!ready && !window.warmExpired) setTimeout(warmServer, 5000);
  } catch (e) {
    console.error("[PilotLight] Failed to warm server:", e);
    setServerWarmed(false);
    if (!window.warmExpired) setTimeout(warmServer, 10000);
  }
}


