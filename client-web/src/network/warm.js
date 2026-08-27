export async function warmServer() {
  if (window.warmExpired) {
    return;
  }
  try {
    const res = await fetch('/pages/titanball/api/warm', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      }
    });
    const data = await res.json();
    
    // Retry polling if the server is still booting up and DNS is propagating
    if (data.status === 'starting' || data.status === 'unknown' || !data.serverUrl) {
      setTimeout(warmServer, 5000);
    }
  } catch (e) {
    console.error("[PilotLight] Failed to warm server:", e);
    // Retry on transient connection failures
    setTimeout(warmServer, 10000);
  }
}
