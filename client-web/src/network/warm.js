const IDLE_LIMIT_MS = 4 * 60 * 60 * 1000; // 4 hours
let serverWarmedState = false;
let wasWarming = false;

export function recordUserActivity() {
  if (!window.warmExpired) {
    window.lastUserActivity = Date.now();
  }
}

export function isServerWarmed() {
  return serverWarmedState;
}

export function markServerWarming() {
  wasWarming = true;
  setServerWarmed(false);
}

export function setServerWarmed(warmed) {
  serverWarmedState = warmed;
  if (!warmed) {
    wasWarming = true;
  }
  console.log(`[PilotLight] Server warming state changed: ${warmed ? 'WARMED (Ready)' : 'WARMING (Wait 1-2 mins)'} (wasWarming=${wasWarming})`);
  updateBadgeUI(warmed);
}

function updateBadgeUI(warmed) {
  const badges = document.querySelectorAll('.server-status-badge');
  badges.forEach(badge => {
    if (warmed) {
      if (wasWarming) {
        badge.style.display = 'flex';
        badge.classList.remove('warming');
        badge.classList.add('warmed');
        badge.innerHTML = `
          <span class="status-checkmark">✓</span>
          <span class="status-text">servers online</span>
        `;
      } else {
        badge.style.display = 'none';
      }
    } else {
      wasWarming = true;
      badge.style.display = 'flex';
      badge.classList.remove('warmed');
      badge.classList.add('warming');
      badge.innerHTML = `
        <span class="status-spinner"></span>
        <span class="status-text">servers warming up, wait 1-2 mins</span>
      `;
    }
  });
}

export async function warmServer() {
  if (window.warmExpired) {
    return;
  }

  if (!window.lastUserActivity) {
    window.lastUserActivity = Date.now();
  }

  const idleTimeMs = Date.now() - window.lastUserActivity;
  if (idleTimeMs > IDLE_LIMIT_MS) {
    window.warmExpired = true;
    console.log("[PilotLight] Session expired after 4 hours of inactivity. Stopping server warm pings.");
    const expiredOverlay = document.getElementById('session-expired-overlay');
    if (expiredOverlay && expiredOverlay.style.display !== 'flex') {
      expiredOverlay.style.display = 'flex';
    }
    return;
  }

  try {
    const res = await fetch('/pages/titanball/api/warm', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      }
    });

    if (!res.ok) {
      console.warn(`[PilotLight] Warm ping returned status: ${res.status}`);
      setServerWarmed(false);
      if (!window.warmExpired) {
        setTimeout(warmServer, 10000);
      }
      return;
    }

    const data = await res.json();
    console.log("[PilotLight] Warm ping response:", data);
    
    // Check if server is fully ready/running
    if (data.status === 'ready' || data.status === 'running') {
      setServerWarmed(true);
    } else {
      // Retry polling if the server is still booting up and DNS is propagating
      setServerWarmed(false);
      if (!window.warmExpired) {
        setTimeout(warmServer, 5000);
      }
    }
  } catch (e) {
    console.error("[PilotLight] Failed to warm server:", e);
    setServerWarmed(false);
    // Retry on transient connection failures
    if (!window.warmExpired) {
      setTimeout(warmServer, 10000);
    }
  }
}


