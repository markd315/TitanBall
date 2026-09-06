import { setServerWarmed } from './warm.js';

export async function login(usernameOrEmail, password) {
  let res;
  try {
    res = await fetch('/pages/titanball/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usernameOrEmail, password })
    });
  } catch (netErr) {
    console.error("[Auth Observability] Login network error (server offline/unreachable):", netErr);
    setServerWarmed(false);
    const err = new Error('Server is offline or unreachable');
    err.code = 'SERVER_DOWN';
    err.cause = netErr;
    throw err;
  }

  if (!res.ok) {
    console.warn(`[Auth Observability] Login HTTP status: ${res.status}`);
    if (res.status === 401 || res.status === 400 || res.status === 403) {
      const err = new Error('Invalid username/email or password');
      err.code = 'INVALID_CREDENTIALS';
      err.status = res.status;
      throw err;
    } else {
      setServerWarmed(false);
      const err = new Error(`Server returned error status ${res.status}`);
      err.code = 'SERVER_DOWN';
      err.status = res.status;
      throw err;
    }
  }

  const data = await res.json();
  sessionStorage.setItem('accessToken', data.accessToken);
  sessionStorage.setItem('refreshToken', data.refreshToken);
  setServerWarmed(true);
  return data;
}

export async function joinQueue(tournamentCode = '', classSelection = '', partners = '', preferredLane = '') {
  const token = sessionStorage.getItem('accessToken');
  const laneParam = preferredLane || sessionStorage.getItem('preferredLane') || 'TOP';
  const url = `/pages/titanball/api/join?tournamentCode=${encodeURIComponent(tournamentCode)}&classSelection=${encodeURIComponent(classSelection)}&preferredLane=${encodeURIComponent(laneParam)}&partners=${encodeURIComponent(partners)}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (!res.ok) throw new Error('Join queue failed');
  return res.text();
}

export async function checkGame() {
  const token = sessionStorage.getItem('accessToken');
  const res = await fetch('/pages/titanball/api/gamecheck', {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (!res.ok) throw new Error('Check game failed');
  return res.text(); // returns gameId or "WAITING"
}

export async function register(email, username, password) {
  const res = await fetch('/pages/titanball/api/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, username, password, role: 'USER' })
  });
  if (!res.ok) throw new Error('Registration failed');
  return res.json();
}

export async function startTutorial() {
  const token = sessionStorage.getItem('accessToken');
  const res = await fetch('/pages/titanball/api/tutorial', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (!res.ok) throw new Error('Start tutorial failed');
  return res.text();
}