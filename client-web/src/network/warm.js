export async function warmServer() {
  if (window.warmExpired) {
    console.log("[PilotLight] Warm server ping skipped: Session Expired.");
    return;
  }
  try {
    console.log("[PilotLight] Sending server warming ping...");
    const res = await fetch('/pages/titanball/api/warm', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      }
    });
    const data = await res.json();
    console.log("[PilotLight] Warm response:", data);
    
    // Retry polling if the server is still booting up and DNS is propagating
    if (data.status === 'starting' || data.status === 'unknown' || !data.serverUrl) {
      console.log("[PilotLight] Server is starting/warming. Retrying IP/DNS resolution in 5 seconds...");
      setTimeout(warmServer, 5000);
    }
  } catch (e) {
    console.error("[PilotLight] Failed to warm server:", e);
    // Retry on transient connection failures
    setTimeout(warmServer, 10000);
  }
}
