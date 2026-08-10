export async function login(usernameOrEmail, password) {
  const res = await fetch('/pages/titanball/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usernameOrEmail, password })
  });
  if (!res.ok) throw new Error('Login failed');
  const data = await res.json();
  sessionStorage.setItem('accessToken', data.accessToken);
  sessionStorage.setItem('refreshToken', data.refreshToken);
  return data;
}

export async function joinQueue(tournamentCode = '', classSelection = '') {
  const token = sessionStorage.getItem('accessToken');
  const url = `/pages/titanball/api/join?tournamentCode=${encodeURIComponent(tournamentCode)}&classSelection=${encodeURIComponent(classSelection)}`;
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