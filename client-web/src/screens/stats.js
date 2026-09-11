import { fetchUserStats } from '../network/auth.js';

export const RANK_TIERS = [
  { name: 'Grandmaster', minElo: 2000, badge: 'res/Court/rnk/grandmaster.png', color: '#ff4757', glow: 'rgba(255, 71, 87, 0.45)' },
  { name: 'Master', minElo: 1800, badge: 'res/Court/rnk/master.png', color: '#c084fc', glow: 'rgba(192, 132, 252, 0.45)' },
  { name: 'Diamond', minElo: 1600, badge: 'res/Court/rnk/diamond.png', color: '#38bdf8', glow: 'rgba(56, 189, 248, 0.45)' },
  { name: 'Gold Halo', minElo: 1400, badge: 'res/Court/rnk/gold_halo.png', color: '#facc15', glow: 'rgba(250, 204, 21, 0.45)' },
  { name: 'Gold', minElo: 1200, badge: 'res/Court/rnk/gold.png', color: '#fbbf24', glow: 'rgba(251, 191, 36, 0.45)' },
  { name: 'Silver', minElo: 1000, badge: 'res/Court/rnk/silver.png', color: '#cbd5e1', glow: 'rgba(203, 213, 225, 0.45)' },
  { name: 'Bronze', minElo: 0, badge: 'res/Court/rnk/bronze.png', color: '#fb923c', glow: 'rgba(251, 146, 60, 0.45)' }
];

export const getRankTier = (elo) => RANK_TIERS.find(t => (Number(elo) || 1000) >= t.minElo) || RANK_TIERS[RANK_TIERS.length - 1];

let cachedUserStats = null;
let activeStatsTab = '3v3';

function getDefaultStats() {
  const s = { username: sessionStorage.getItem('username') || '', email: sessionStorage.getItem('email') || '', rating: 1000.0, rank: 999, rating_1v1: 1000.0, rank1v1: 999 };
  ['wins', 'losses', 'ties', 'goals', 'sidegoals', 'points', 'kills', 'deaths', 'killassists', 'goalassists', 'passes', 'turnovers', 'steals', 'blocks', 'rebounds',
   'wins_1v1', 'losses_1v1', 'ties_1v1', 'goals_1v1', 'sidegoals_1v1', 'points_1v1', 'kills_1v1', 'deaths_1v1', 'passes_1v1', 'turnovers_1v1', 'steals_1v1', 'blocks_1v1'].forEach(k => s[k] = 0);
  return s;
}

export function getCurrentUserStats() {
  if (!cachedUserStats) {
    try {
      const stored = sessionStorage.getItem('cachedUserStats');
      if (stored) cachedUserStats = JSON.parse(stored);
    } catch {}
    if (!cachedUserStats) cachedUserStats = getDefaultStats();
  }
  return cachedUserStats;
}

export async function refreshUserStats() {
  try {
    const data = await fetchUserStats();
    if (data) {
      cachedUserStats = { ...getDefaultStats(), ...data };
      sessionStorage.setItem('cachedUserStats', JSON.stringify(cachedUserStats));
    }
  } catch (err) {
    if (!cachedUserStats) cachedUserStats = getCurrentUserStats();
  }
  updateStatsBanner();
  return cachedUserStats;
}

export function updateStatsBanner() {
  const stats = getCurrentUserStats();
  const elo = Math.round(stats.rating != null ? stats.rating : 1000), wins = Number(stats.wins || 0), losses = Number(stats.losses || 0), ties = Number(stats.ties || 0);
  const totalMatches = wins + losses + ties;

  const setTxt = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
  setTxt('stat-elo-val', elo);
  setTxt('stat-wins-val', wins);
  setTxt('stat-losses-val', losses);
  if (stats.username) setTxt('player-username', stats.username);

  const rankContainer = document.getElementById('stat-rank-display');
  if (rankContainer) {
    if (totalMatches >= 10) {
      const tier = getRankTier(elo);
      rankContainer.innerHTML = `<div class="inline-rank-badge" style="box-shadow: 0 0 8px ${tier.glow}; border-color: ${tier.color}55;"><img src="${tier.badge}" alt="${tier.name}" class="inline-rank-img"><span class="inline-rank-name" style="color: ${tier.color};">${tier.name}</span></div>`;
      rankContainer.title = `${tier.name} Tier (${elo} ELO) - Click for advanced stats`;
    } else {
      rankContainer.innerHTML = `<div class="inline-rank-badge unranked"><span class="inline-placement-icon">⏳</span><span class="inline-unranked-badge">Unranked</span><span class="inline-unranked-progress">(${totalMatches}/10)</span></div>`;
      rankContainer.title = `${totalMatches}/10 matches played - Play 10 matches to reveal rank! Click for advanced stats.`;
    }
  }
}

export function setAdvancedStatsModal(open) {
  const modal = document.getElementById('stats-modal'), overlay = document.getElementById('mode-overlay');
  if (!modal) return;
  if (open) renderAdvancedStatsPane();
  modal.style.display = open ? 'flex' : 'none';
  if (overlay) overlay.style.pointerEvents = open ? 'none' : 'auto';
}
export const openAdvancedStatsModal = () => setAdvancedStatsModal(true);
export const closeAdvancedStatsModal = () => setAdvancedStatsModal(false);

function renderAdvancedStatsPane() {
  const stats = getCurrentUserStats(), is3v3 = activeStatsTab === '3v3';
  const elo = Math.round(is3v3 ? (stats.rating != null ? stats.rating : 1000) : (stats.rating_1v1 != null ? stats.rating_1v1 : 1000));
  const wins = Number(is3v3 ? (stats.wins || 0) : (stats.wins_1v1 || 0));
  const losses = Number(is3v3 ? (stats.losses || 0) : (stats.losses_1v1 || 0));
  const ties = Number(is3v3 ? (stats.ties || 0) : (stats.ties_1v1 || 0));
  const totalMatches = wins + losses + ties;
  const winRate = totalMatches > 0 ? ((wins / totalMatches) * 100).toFixed(1) : '0.0';
  const tier = getRankTier(elo);
  const rankNum = is3v3 ? stats.rank : stats.rank1v1;
  const rankStr = (rankNum && rankNum < 900) ? `#${rankNum}` : 'Unranked';

  const avatarEl = document.getElementById('stats-player-avatar');
  const userEl = document.getElementById('stats-player-username');
  const tierEl = document.getElementById('stats-player-tier');
  const eloEl = document.getElementById('stats-player-elo');
  const placeEl = document.getElementById('stats-player-placement');

  if (userEl) userEl.textContent = stats.username || sessionStorage.getItem('username') || 'Player';
  if (eloEl) eloEl.textContent = `${elo} ELO`;

  if (totalMatches >= 10) {
    if (avatarEl) avatarEl.innerHTML = `<img src="${tier.badge}" alt="${tier.name}" style="width: 32px; height: 32px; object-fit: contain; filter: drop-shadow(0 0 8px ${tier.glow}); image-rendering: pixelated;">`;
    if (tierEl) { tierEl.textContent = tier.name; tierEl.style.color = tier.color; }
    if (placeEl) placeEl.textContent = `Leaderboard: ${rankStr}`;
  } else {
    if (avatarEl) avatarEl.innerHTML = `<div style="font-size: 28px; filter: grayscale(0.5);">⏳</div>`;
    if (tierEl) { tierEl.textContent = 'Placement Matches'; tierEl.style.color = '#94a3b8'; }
    if (placeEl) placeEl.textContent = `${totalMatches} of 10 matches played`;
  }

  const goals = is3v3 ? (stats.goals || 0) : (stats.goals_1v1 || 0);
  const sidegoals = is3v3 ? (stats.sidegoals || 0) : (stats.sidegoals_1v1 || 0);
  const points = Number(is3v3 ? (stats.points || 0) : (stats.points_1v1 || 0)).toFixed(1);
  const kills = is3v3 ? (stats.kills || 0) : (stats.kills_1v1 || 0);
  const deaths = is3v3 ? (stats.deaths || 0) : (stats.deaths_1v1 || 0);
  const kdRatio = deaths > 0 ? (kills / deaths).toFixed(2) : (kills > 0 ? kills.toFixed(2) : '0.00');
  const passes = is3v3 ? (stats.passes || 0) : (stats.passes_1v1 || 0);
  const turnovers = is3v3 ? (stats.turnovers || 0) : (stats.turnovers_1v1 || 0);
  const steals = is3v3 ? (stats.steals || 0) : (stats.steals_1v1 || 0);
  const blocks = is3v3 ? (stats.blocks || 0) : (stats.blocks_1v1 || 0);
  const assists = is3v3 ? ((stats.killassists || 0) + (stats.goalassists || 0)) : 0;
  const rebounds = is3v3 ? (stats.rebounds || 0) : 0;

  const m = totalMatches > 0 ? totalMatches : 1;
  const cpg = (goals / m).toFixed(1), spg = (sidegoals / m).toFixed(1), ppg = (Number(points) / m).toFixed(1);
  const kpg = (kills / m).toFixed(1), dpg = (deaths / m).toFixed(1);
  const passPg = (passes / m).toFixed(1), toPg = (turnovers / m).toFixed(1);
  const stlPg = (steals / m).toFixed(1), blkPg = (blocks / m).toFixed(1), rebPg = (rebounds / m).toFixed(1);

  const gridEl = document.getElementById('stats-tiles-grid');
  if (gridEl) {
    gridEl.innerHTML = `
      <div class="stats-metric-card highlight-elo">
        <div class="stats-split-row"><span class="metric-label">Rating</span><span class="stats-total-count">${totalMatches} matches</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val elo-blue">${elo}</span><span class="pg-unit">ELO</span></div>
          <span class="stats-total-count" style="font-weight: 700; color: #cbd5e1;">${totalMatches >= 10 ? tier.name : 'Placement'} (${rankStr})</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Record</span><span class="stats-total-count">Total Matches</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#2ed573;">${wins}</span> - <span style="color:#ef4444;">${losses}</span><span style="color:#94a3b8; font-size:13px;"> - ${ties}</span></span></div>
          <span class="stats-total-count" style="font-weight: 700; color: #cbd5e1;">${winRate}% Win Rate (${totalMatches})</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Scoring</span><span class="stats-total-count">${goals} center · ${sidegoals} side</span></div>
        <div class="stats-split-row" style="justify-content: space-between;"><span class="stats-sub-label" style="font-size: 10px; color: #64748b; font-weight: 600; text-transform: uppercase;">Center / Side</span><span class="stats-total-count">${points} pts</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#fbbf24;">${cpg}</span> / <span style="color:#cbd5e1;">${spg}</span></span><span class="pg-unit">/g</span></div>
          <span class="stats-total-count">${ppg} pts/g</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Combat</span><span class="stats-total-count">${kills} kills · ${deaths} deaths</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#f87171;">${kpg}</span> / <span style="color:#64748b;">${dpg}</span></span><span class="pg-unit">/g</span></div>
          <span class="stats-total-count">${kdRatio} K/D ${is3v3 ? `· ${assists} ast` : ''}</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Ball Handling</span><span class="stats-total-count">${passes} passes · ${turnovers} to</span></div>
        <div class="stats-split-row" style="justify-content: space-between;"><span class="stats-sub-label" style="font-size: 10px; color: #64748b; font-weight: 600; text-transform: uppercase;">Pass / Turnover</span><span class="stats-total-count">${rebounds} reb (${rebPg}/g)</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#38bdf8;">${passPg}</span> / <span style="color:#f59e0b;">${toPg}</span></span><span class="pg-unit">/g</span></div>
          <span class="stats-total-count" style="color: #64748b;">pass / to ratio ${turnovers > 0 ? (passes / turnovers).toFixed(1) : passes}</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Defensive Plays</span><span class="stats-total-count">${steals} steals · ${blocks} blocks</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#a7f3d0;">${stlPg}</span> / <span style="color:#a855f7;">${blkPg}</span></span><span class="pg-unit">/g</span></div>
        </div>
      </div>
    `;
  }
}

export function initStatsScreen() {
  document.getElementById('user-stats-card')?.addEventListener('click', openAdvancedStatsModal);
  ['stats-modal-close-btn', 'stats-modal-done-btn'].forEach(id => document.getElementById(id)?.addEventListener('click', closeAdvancedStatsModal));

  ['3v3', '1v1'].forEach(mode => {
    document.getElementById(`stats-tab-${mode}`)?.addEventListener('click', () => {
      activeStatsTab = mode;
      document.getElementById('stats-tab-3v3')?.classList.toggle('active', mode === '3v3');
      document.getElementById('stats-tab-1v1')?.classList.toggle('active', mode === '1v1');
      renderAdvancedStatsPane();
    });
  });

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && document.getElementById('stats-modal')?.style.display !== 'none') {
      closeAdvancedStatsModal();
    }
  });

  updateStatsBanner();
}
