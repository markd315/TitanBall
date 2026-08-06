export async function login(usernameOrEmail, password) {
  const res = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usernameOrEmail, password })
  });
  if (!res.ok) throw new Error('Login failed');
  const data = await res.json();
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  return data;
}

export async function joinQueue(tournamentCode = '') {
  const token = localStorage.getItem('accessToken');
  const url = `/api/join?tournamentCode=${encodeURIComponent(tournamentCode)}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (!res.ok) throw new Error('Join queue failed');
  return res.text();
}

export async function checkGame() {
  const token = localStorage.getItem('accessToken');
  const res = await fetch('/api/gamecheck', {
    method: 'GET',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (!res.ok) throw new Error('Check game failed');
  return res.text(); // returns gameId or "WAITING"
}

export async function register(email, username, password) {
  const res = await fetch('/api/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, username, password, role: 'USER' })
  });
  if (!res.ok) throw new Error('Registration failed');
  return res.json();
}

export async function startTutorial() {
  const token = localStorage.getItem('accessToken');
  const res = await fetch('/api/tutorial', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (!res.ok) throw new Error('Start tutorial failed');
  return res.text();
}