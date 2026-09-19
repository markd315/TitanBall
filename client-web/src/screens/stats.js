import { fetchUserStats } from '../network/auth.js';

export const RANK_TIERS = [
  { name: 'Grandmaster', minElo: 1440, badge: 'res/Court/rnk/grandmaster.png', color: '#ff4757', glow: 'rgba(255, 71, 87, 0.22)', shimmerClass: 'rank-shimmer-grandmaster' },
  { name: 'Master', minElo: 1350, badge: 'res/Court/rnk/master.png', color: '#c084fc', glow: 'rgba(192, 132, 252, 0.22)', shimmerClass: 'rank-shimmer-master' },
  { name: 'Diamond', minElo: 1260, badge: 'res/Court/rnk/diamond.png', color: '#38bdf8', glow: 'rgba(56, 189, 248, 0.22)', shimmerClass: 'rank-shimmer-diamond' },
  { name: 'Gold Halo', minElo: 1175, badge: 'res/Court/rnk/gold_halo.png', color: '#facc15', glow: 'rgba(250, 204, 21, 0.22)' },
  { name: 'Gold', minElo: 1060, badge: 'res/Court/rnk/gold.png', color: '#fbbf24', glow: 'rgba(251, 191, 36, 0.22)' },
  { name: 'Silver', minElo: 900, badge: 'res/Court/rnk/silver.png', color: '#cbd5e1', glow: 'rgba(203, 213, 225, 0.22)' },
  { name: 'Bronze', minElo: 0, badge: 'res/Court/rnk/bronze.png', color: '#fb923c', glow: 'rgba(251, 146, 60, 0.22)' }
];

export const getRankTier = (elo) => RANK_TIERS.find(t => (Number(elo) || 1000) >= t.minElo) || RANK_TIERS[RANK_TIERS.length - 1];

let cachedUserStats = null;
let activeStatsTab = '4v4';
let activeClassFilter = 'ALL';

function getDefaultStats() {
  const s = { username: sessionStorage.getItem('username') || '', email: sessionStorage.getItem('email') || '', rating: 1000.0, rank: 999, rating_1v1: 1000.0, rank1v1: 999, classStats: {} };
  ['wins', 'losses', 'ties', 'goals', 'sidegoals', 'points', 'kills', 'deaths', 'killassists', 'goalassists', 'sidegoalassists', 'passes', 'turnovers', 'steals', 'blocks', 'rebounds',
   'saves', 'sidegoalsaves', 'centergoalsaves', 'sidegoalsconceded', 'goalsconceded',
   'upgradesgold', 'consumablesgold', 'manaspent',
   'blocks_g', 'passes_g', 'turnovers_g', 'rebounds_g', 'steals_g', 'kills_g', 'deaths_g', 'goalie_matches',
   'wins_1v1', 'losses_1v1', 'ties_1v1', 'goals_1v1', 'sidegoals_1v1', 'points_1v1', 'kills_1v1', 'deaths_1v1', 'killassists_1v1', 'goalassists_1v1', 'sidegoalassists_1v1', 'passes_1v1', 'turnovers_1v1', 'steals_1v1', 'blocks_1v1', 'rebounds_1v1'].forEach(k => s[k] = 0);
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

  const setTxt = (id, v) => { const el = document.getElementById(id); if (el && el.textContent !== String(v)) el.textContent = v; };
  setTxt('stat-elo-val', elo);
  setTxt('stat-wins-val', wins);
  setTxt('stat-losses-val', losses);
  if (stats.username) setTxt('player-username', stats.username);

  const rankContainer = document.getElementById('stat-rank-display');
  if (rankContainer) {
    if (totalMatches >= 10) {
      const tier = getRankTier(elo);
      const renderKey = `ranked_${tier.name}_${elo}`;
      if (rankContainer.dataset.renderKey !== renderKey) {
        rankContainer.dataset.renderKey = renderKey;
        const shimmer = tier.shimmerClass ? ` ${tier.shimmerClass}` : '';
        const colorStyle = tier.shimmerClass ? '' : `color: ${tier.color};`;
        rankContainer.innerHTML = `<div class="inline-rank-badge" style="box-shadow: 0 0 4px ${tier.glow}; border-color: ${tier.color}35;"><img src="${tier.badge}" alt="${tier.name}" class="inline-rank-img"><span class="inline-rank-name${shimmer}" style="${colorStyle}">${tier.name}</span></div>`;
        rankContainer.title = `${tier.name} Tier (${elo} ELO) - Click for advanced stats`;
      }
    } else {
      const unrankedKey = `unranked_${totalMatches}`;
      if (rankContainer.dataset.renderKey !== unrankedKey) {
        rankContainer.dataset.renderKey = unrankedKey;
        rankContainer.innerHTML = `<div class="inline-rank-badge unranked"><span class="inline-placement-icon">⏳</span><span class="inline-unranked-badge">Unranked</span><span class="inline-unranked-progress">(${totalMatches}/10)</span></div>`;
        rankContainer.title = `${totalMatches}/10 matches played - Play 10 matches to reveal rank! Click for advanced stats.`;
      }
    }
  }
}

export function setAdvancedStatsModal(open) {
  const modal = document.getElementById('stats-modal'), overlay = document.getElementById('mode-overlay');
  if (!modal) return;
  if (open) {
    renderAdvancedStatsPane();
    refreshUserStats().then(() => renderAdvancedStatsPane()).catch(() => {});
  }
  modal.style.display = open ? 'flex' : 'none';
  if (overlay) overlay.style.pointerEvents = open ? 'none' : 'auto';

  const backdrop = document.getElementById('modal-backdrop');
  if (backdrop) {
    if (open) {
      backdrop.classList.add('active');
      backdrop.style.display = 'block';
    } else {
      const otherModalsOpen = ['controls-modal', 'tournament-modal', 'masteries-modal'].some(id => {
        const el = document.getElementById(id);
        return el && el.style.display !== 'none' && el.style.display !== '';
      });
      if (!otherModalsOpen) {
        backdrop.classList.remove('active');
        backdrop.style.display = 'none';
      }
    }
  }
}
export const openAdvancedStatsModal = () => setAdvancedStatsModal(true);
export const closeAdvancedStatsModal = () => setAdvancedStatsModal(false);

function renderAdvancedStatsPane() {
  const stats = getCurrentUserStats(), is4v4 = activeStatsTab === '4v4';

  // Sync class filter selector UI
  const filterSelect = document.getElementById('stats-class-filter');
  if (filterSelect && filterSelect.value !== activeClassFilter) {
    filterSelect.value = activeClassFilter;
  }

  // Check if a specific outfield class is filtered
  const isFiltered = activeClassFilter !== 'ALL';
  const cStat = isFiltered && stats.classStats
    ? (stats.classStats[activeClassFilter] || stats.classStats[activeClassFilter.toLowerCase()] || stats.classStats[activeClassFilter.toUpperCase()] || null)
    : null;

  // Rating & Rank header always reflects user-level ranking
  const elo = Math.round(is4v4 ? (stats.rating != null ? stats.rating : 1000) : (stats.rating_1v1 != null ? stats.rating_1v1 : 1000));
  const tier = getRankTier(elo);
  const rankNum = is4v4 ? stats.rank : stats.rank1v1;
  const rankStr = (rankNum && rankNum < 900) ? `#${rankNum}` : 'Unranked';

  // Overall match counts (for placement badge / rating card)
  const overallWins = Number(is4v4 ? (stats.wins || 0) : (stats.wins_1v1 || 0));
  const overallLosses = Number(is4v4 ? (stats.losses || 0) : (stats.losses_1v1 || 0));
  const overallTies = Number(is4v4 ? (stats.ties || 0) : (stats.ties_1v1 || 0));
  const overallMatches = overallWins + overallLosses + overallTies;

  // Active record (overall or class-specific)
  let wins, losses, ties, totalMatches;
  if (isFiltered) {
    wins = Number(is4v4 ? (cStat?.wins || 0) : (cStat?.wins_1v1 || 0));
    losses = Number(is4v4 ? (cStat?.losses || 0) : (cStat?.losses_1v1 || 0));
    ties = Number(is4v4 ? (cStat?.ties || 0) : (cStat?.ties_1v1 || 0));
    totalMatches = wins + losses + ties;
  } else {
    wins = overallWins;
    losses = overallLosses;
    ties = overallTies;
    totalMatches = overallMatches;
  }
  const winRate = totalMatches > 0 ? ((wins / totalMatches) * 100).toFixed(1) : '0.0';

  const avatarEl = document.getElementById('stats-player-avatar');
  const userEl = document.getElementById('stats-player-username');
  const tierEl = document.getElementById('stats-player-tier');
  const eloEl = document.getElementById('stats-player-elo');
  const placeEl = document.getElementById('stats-player-placement');

  if (userEl) userEl.textContent = stats.username || sessionStorage.getItem('username') || 'Player';
  if (eloEl) eloEl.textContent = `${elo} ELO`;

  if (overallMatches >= 10) {
    const avatarKey = `${tier.name}_${tier.badge}`;
    if (avatarEl && avatarEl.dataset.avatarKey !== avatarKey) {
      avatarEl.dataset.avatarKey = avatarKey;
      avatarEl.innerHTML = `<img src="${tier.badge}" alt="${tier.name}" style="width: 32px; height: 32px; object-fit: contain; filter: drop-shadow(0 0 8px ${tier.glow}); image-rendering: pixelated;">`;
    }
    if (tierEl) {
      const tierKey = `${tier.name}_${tier.shimmerClass || ''}`;
      if (tierEl.dataset.tierKey !== tierKey) {
        tierEl.dataset.tierKey = tierKey;
        tierEl.textContent = tier.name;
        tierEl.className = `stats-header-tier-text${tier.shimmerClass ? ` ${tier.shimmerClass}` : ''}`;
        tierEl.style.color = tier.shimmerClass ? '' : tier.color;
      }
    }
    if (placeEl) placeEl.textContent = `Leaderboard: ${rankStr}`;
  } else {
    if (avatarEl && avatarEl.dataset.avatarKey !== 'unranked') {
      avatarEl.dataset.avatarKey = 'unranked';
      avatarEl.innerHTML = `<div style="font-size: 28px; filter: grayscale(0.5);">⏳</div>`;
    }
    if (tierEl && tierEl.dataset.tierKey !== 'unranked') {
      tierEl.dataset.tierKey = 'unranked';
      tierEl.textContent = 'Placement Matches';
      tierEl.className = 'stats-header-tier-text';
      tierEl.style.color = '#cbd5e1';
    }
    if (placeEl) placeEl.textContent = `${overallMatches} of 10 matches played`;
  }

  // Outfield Metrics
  let goals, sidegoals, points, kills, deaths, passes, turnovers, steals, blocks, assists, killAssists, goalAssists, sideGoalAssists, rebounds;
  if (isFiltered) {
    goals = is4v4 ? (cStat?.goals || 0) : (cStat?.goals_1v1 || 0);
    sidegoals = is4v4 ? (cStat?.sidegoals || 0) : (cStat?.sidegoals_1v1 || 0);
    const rawPoints = Number(is4v4 ? (cStat?.points || 0) : (cStat?.points_1v1 || 0));
    points = (rawPoints % 1 === 0) ? rawPoints.toString() : rawPoints.toFixed(2);
    kills = is4v4 ? (cStat?.kills || 0) : (cStat?.kills_1v1 || 0);
    deaths = is4v4 ? (cStat?.deaths || 0) : (cStat?.deaths_1v1 || 0);
    passes = is4v4 ? (cStat?.passes || 0) : (cStat?.passes_1v1 || 0);
    turnovers = is4v4 ? (cStat?.turnovers || 0) : (cStat?.turnovers_1v1 || 0);
    steals = is4v4 ? (cStat?.steals || 0) : (cStat?.steals_1v1 || 0);
    blocks = is4v4 ? (cStat?.blocks || 0) : (cStat?.blocks_1v1 || 0);
    killAssists = is4v4 ? (cStat?.killassists || 0) : (cStat?.killassists_1v1 || 0);
    goalAssists = is4v4 ? (cStat?.goalassists || 0) : (cStat?.goalassists_1v1 || 0);
    sideGoalAssists = is4v4 ? (cStat?.sidegoalassists || 0) : (cStat?.sidegoalassists_1v1 || 0);
    assists = is4v4 ? (goalAssists + sideGoalAssists) : 0;
    rebounds = is4v4 ? (cStat?.rebounds || 0) : (cStat?.rebounds_1v1 || 0);
  } else {
    goals = is4v4 ? (stats.goals || 0) : (stats.goals_1v1 || 0);
    sidegoals = is4v4 ? (stats.sidegoals || 0) : (stats.sidegoals_1v1 || 0);
    const rawPoints = Number(is4v4 ? (stats.points || 0) : (stats.points_1v1 || 0));
    points = (rawPoints % 1 === 0) ? rawPoints.toString() : rawPoints.toFixed(2);
    kills = is4v4 ? (stats.kills || 0) : (stats.kills_1v1 || 0);
    deaths = is4v4 ? (stats.deaths || 0) : (stats.deaths_1v1 || 0);
    passes = is4v4 ? (stats.passes || 0) : (stats.passes_1v1 || 0);
    turnovers = is4v4 ? (stats.turnovers || 0) : (stats.turnovers_1v1 || 0);
    steals = is4v4 ? (stats.steals || 0) : (stats.steals_1v1 || 0);
    blocks = is4v4 ? (stats.blocks || 0) : (stats.blocks_1v1 || 0);
    killAssists = is4v4 ? (stats.killassists || 0) : (stats.killassists_1v1 || 0);
    goalAssists = is4v4 ? (stats.goalassists || 0) : (stats.goalassists_1v1 || 0);
    sideGoalAssists = is4v4 ? (stats.sidegoalassists || 0) : (stats.sidegoalassists_1v1 || 0);
    assists = is4v4 ? (goalAssists + sideGoalAssists) : 0;
    rebounds = is4v4 ? (stats.rebounds || 0) : 0;
  }
  const kdRatio = deaths > 0 ? (kills / deaths).toFixed(2) : (kills > 0 ? kills.toFixed(2) : '0.00');

  // Match denominators
  const m = totalMatches > 0 ? totalMatches : 1;
  const isGoalieFiltered = activeClassFilter === 'GOALIE';
  const goalieStatObj = stats.classStats ? (stats.classStats['GOALIE'] || stats.classStats['goalie'] || null) : null;
  const goalieClassMatches = goalieStatObj ? (Number(goalieStatObj.wins || 0) + Number(goalieStatObj.losses || 0) + Number(goalieStatObj.ties || 0)) : 0;
  const goalieMatches = is4v4
    ? (isGoalieFiltered ? totalMatches : (!isFiltered ? (goalieClassMatches > 0 ? goalieClassMatches : (stats.goalie_matches != null ? Number(stats.goalie_matches) : ((stats.saves || 0) > 0 || (stats.goalsconceded || 0) > 0 ? 1 : 0))) : 0))
    : 0;
  const outfieldMatches = isGoalieFiltered ? 0 : (isFiltered ? totalMatches : Math.max(0, totalMatches - goalieMatches));
  const mOutfield = outfieldMatches > 0 ? outfieldMatches : (goalieMatches > 0 ? 0 : m);
  const mGoalie = goalieMatches > 0 ? goalieMatches : 1;

  const cpg = mOutfield > 0 ? (goals / mOutfield).toFixed(1) : '0.0';
  const spg = mOutfield > 0 ? (sidegoals / mOutfield).toFixed(1) : '0.0';
  const ppg = mOutfield > 0 ? (Number(points) / mOutfield).toFixed(1) : '0.0';
  const kpg = mOutfield > 0 ? (kills / mOutfield).toFixed(1) : '0.0';
  const dpg = mOutfield > 0 ? (deaths / mOutfield).toFixed(1) : '0.0';
  const passPg = mOutfield > 0 ? (passes / mOutfield).toFixed(1) : '0.0';
  const toPg = mOutfield > 0 ? (turnovers / mOutfield).toFixed(1) : '0.0';
  const stlPg = mOutfield > 0 ? (steals / mOutfield).toFixed(1) : '0.0';
  const blkPg = mOutfield > 0 ? (blocks / mOutfield).toFixed(1) : '0.0';
  const rebPg = mOutfield > 0 ? (rebounds / mOutfield).toFixed(1) : '0.0';
  const gastPg = mOutfield > 0 ? (goalAssists / mOutfield).toFixed(1) : '0.0';
  const sgastPg = mOutfield > 0 ? (sideGoalAssists / mOutfield).toFixed(1) : '0.0';
  const kastPg = mOutfield > 0 ? (killAssists / mOutfield).toFixed(1) : '0.0';

  // Goalie stats & resources (4v4 matches, when ALL classes view OR GOALIE class is filtered)
  const showGoalie = is4v4 && (isGoalieFiltered || !isFiltered);
  const saves = showGoalie ? (isGoalieFiltered && cStat ? (cStat.saves || 0) : (stats.saves || 0)) : 0;
  const sidegoalsaves = showGoalie ? (isGoalieFiltered && cStat ? (cStat.sidegoalsaves || 0) : (stats.sidegoalsaves || 0)) : 0;
  const centergoalsaves = showGoalie ? (isGoalieFiltered && cStat ? (cStat.centergoalsaves || 0) : (stats.centergoalsaves || 0)) : 0;
  const sidegoalsconceded = showGoalie ? (isGoalieFiltered && cStat ? (cStat.sidegoalsconceded || 0) : (stats.sidegoalsconceded || 0)) : 0;
  const goalsconceded = showGoalie ? (isGoalieFiltered && cStat ? (cStat.goalsconceded || 0) : (stats.goalsconceded || 0)) : 0;
  const totalConceded = sidegoalsconceded + goalsconceded;
  const totalShotsFaced = saves + totalConceded;
  const cgTotal = centergoalsaves + goalsconceded;
  const cgSvPct = cgTotal > 0 ? ((centergoalsaves / cgTotal) * 100).toFixed(1) : (centergoalsaves > 0 ? '100.0' : '0.0');
  const sgTotal = sidegoalsaves + sidegoalsconceded;
  const sgSvPct = sgTotal > 0 ? ((sidegoalsaves / sgTotal) * 100).toFixed(1) : (sidegoalsaves > 0 ? '100.0' : '0.0');
  const savesPg = (saves / mGoalie).toFixed(1);
  const concPg = (totalConceded / mGoalie).toFixed(1);

  // Goalie resource stats (read directly from GOALIE class stat; outfield titans do not have gold/mana spend)
  const upgradesgold = showGoalie ? (goalieStatObj ? Number(goalieStatObj.upgradesgold || 0) : Number(stats.upgradesgold || 0)) : 0;
  const consumablesgold = showGoalie ? (goalieStatObj ? Number(goalieStatObj.consumablesgold || 0) : Number(stats.consumablesgold || 0)) : 0;
  const totalGold = upgradesgold + consumablesgold;
  const manaspent = showGoalie ? (goalieStatObj ? Number(goalieStatObj.manaspent || 0) : Number(stats.manaspent || 0)) : 0;
  const goldPg = goalieMatches > 0 ? Math.round(totalGold / goalieMatches) : 0;
  const manaPg = goalieMatches > 0 ? Math.round(manaspent / goalieMatches) : 0;

  const blkG = showGoalie ? (isGoalieFiltered && cStat ? (cStat.blocks || 0) : (stats.blocks_g || 0)) : 0;
  const passG = showGoalie ? (isGoalieFiltered && cStat ? (cStat.passes || 0) : (stats.passes_g || 0)) : 0;
  const toG = showGoalie ? (isGoalieFiltered && cStat ? (cStat.turnovers || 0) : (stats.turnovers_g || 0)) : 0;
  const rebG = showGoalie ? (isGoalieFiltered && cStat ? (cStat.rebounds || 0) : (stats.rebounds_g || 0)) : 0;
  const stlG = showGoalie ? (isGoalieFiltered && cStat ? (cStat.steals || 0) : (stats.steals_g || 0)) : 0;

  // Box Plus-Minus Performance Tier helper:
  const getBpmTier = (val) => {
    if (val >= 2.0) return { name: 'MVP', color: '#facc15' };
    if (val >= 1.2) return { name: 'All-Titan', color: '#2ed573' };
    if (val >= 0.4) return { name: 'Above Avg', color: '#38bdf8' };
    if (val >= -0.4) return { name: 'Average', color: '#cbd5e1' };
    if (val >= -1.2) return { name: 'Below Avg', color: '#fb923c' };
    return { name: 'Replacement', color: '#ef4444' };
  };

  const formatBpm = (val) => {
    if (val === null || val === undefined || isNaN(val)) return '—';
    if (Math.abs(val) < 0.005) return '0.00';
    return (val > 0 ? '+' : '') + val.toFixed(2);
  };

  // Goalie Box Plus-Minus (GBPM): Recalibrated empirical baselines from 12,008 goalie matches in classstat
  let gbpm = 0, gbpmStr = '0.00', gbpmTier = getBpmTier(0);
  const hasGoalieData = showGoalie && (totalShotsFaced > 0 || (totalGold > 0 && manaspent > 0) || goalieMatches > 0);
  if (hasGoalieData) {
    const expectedCgConc = cgTotal * 0.344;
    const cgSavedAboveExp = expectedCgConc - goalsconceded;
    const expectedSgConc = sgTotal * 0.899;
    const sgSavedAboveExp = expectedSgConc - sidegoalsconceded;

    const netProtection = (cgSavedAboveExp / mGoalie) * 1.5 + (sgSavedAboveExp / mGoalie) * 0.35;
    const disruption = (blkG > 0 || rebG > 0 || passG > 0 || toG > 0 || stlG > 0)
      ? ((blkG / mGoalie - 0.005) * 0.05 + (rebG / mGoalie - 0.003) * 0.05 + (stlG / mGoalie - 0.0) * 0.08 + (passG / mGoalie - 0.0) * 0.02 - (toG / mGoalie - 0.002) * 0.06)
      : 0;
    const economy = totalGold > 0 ? ((totalGold / mGoalie - 865) * 0.002 + (manaspent / mGoalie - 662) * 0.0005) : 0;

    gbpm = netProtection + disruption + economy;
    gbpmStr = formatBpm(gbpm);
    gbpmTier = getBpmTier(gbpm);
  }

  // Field Titan Box Plus-Minus (TBPM): Empirical outfield baselines from classstat (35,452 player-matches)
  let obpm = 0, dbpm = 0, tbpm = 0, tbpmStr = '0.00', tbpmTier = getBpmTier(0);
  const hasOutfieldData = is4v4 && !isGoalieFiltered && outfieldMatches > 0;
  if (hasOutfieldData) {
    // Combat weights: ~75/25 split favoring DBPM
    obpm = (Number(cpg) - 0.41) * 1.5 + (Number(spg) - 6.00) * 0.35 + (Number(gastPg) - 0.0) * 0.8 + (Number(sgastPg) - 0.0) * 0.18 + (Number(kpg) - 0.77) * 0.04 + (Number(kastPg) - 0.37) * 0.02 + (Number(passPg) - 14.78) * 0.04 - (Number(toPg) - 6.40) * 0.12;
    dbpm = (Number(stlPg) - 2.12) * 0.22 + (Number(blkPg) - 3.33) * 0.15 + (Number(rebPg) - 23.14) * 0.03 + (Number(kpg) - 0.77) * 0.12 + (Number(kastPg) - 0.37) * 0.06 - (Number(dpg) - 1.69) * 0.20 - (Number(toPg) - 6.40) * 0.03;
    tbpm = obpm + dbpm;
    tbpmStr = formatBpm(tbpm);
    tbpmTier = getBpmTier(tbpm);
  } else if (hasGoalieData) {
    tbpm = gbpm;
    tbpmStr = gbpmStr;
    tbpmTier = gbpmTier;
  }

  const gridEl = document.getElementById('stats-tiles-grid');
  if (gridEl) {
    gridEl.innerHTML = `
      <div class="stats-metric-card highlight-elo">
        <div class="stats-split-row"><span class="metric-label">Rating</span><span class="stats-total-count">${overallMatches} matches</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val elo-blue">${elo}</span><span class="pg-unit">ELO</span></div>
          <span class="stats-total-count" style="font-weight: 700; color: #cbd5e1;">${overallMatches >= 10 ? `<span class="${tier.shimmerClass || ''}" style="${tier.shimmerClass ? '' : `color: ${tier.color};`}">${tier.name}</span>` : 'Placement'} (${rankStr})</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Record</span><span class="stats-total-count">${isFiltered ? `${activeClassFilter} Matches` : 'Total Matches'}</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#2ed573;">${wins}</span> - <span style="color:#ef4444;">${losses}</span><span style="color:#cbd5e1; font-size:13px;"> - ${ties}</span></span></div>
          <span class="stats-total-count" style="font-weight: 700; color: #cbd5e1;">${winRate}% Win Rate (${totalMatches})</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Scoring</span><span class="stats-total-count">${goals} center · ${sidegoals} side</span></div>
        <div class="stats-split-row" style="justify-content: space-between;"><span class="stats-sub-label" style="font-size: 10px; color: #cbd5e1; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px;">Center / Side</span><span class="stats-total-count">${points} pts</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#fbbf24;">${cpg}</span> / <span style="color:#cbd5e1;">${spg}</span></span><span class="pg-unit">/g</span></div>
          <span class="stats-total-count">${ppg} pts/g</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Combat</span><span class="stats-total-count">${kills} kills · ${deaths} deaths${is4v4 ? ` · ${killAssists} ast` : ''}</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#f87171;">${kpg}</span> / <span style="color:#cbd5e1;">${dpg}</span></span><span class="pg-unit">/g</span></div>
          <span class="stats-total-count">${kdRatio} K/D${is4v4 ? ` · ${kastPg} K-ast/g` : ''}</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Playmaking</span><span class="stats-total-count">${is4v4 ? `${goalAssists} CGA · ${sideGoalAssists} SGA` : `${passes} passes`}</span></div>
        <div class="stats-split-row" style="justify-content: space-between;"><span class="stats-sub-label" style="font-size: 10px; color: #cbd5e1; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px;">CGA / SGA</span><span class="stats-total-count">${passes} passes · ${turnovers} TO</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#38bdf8;">${gastPg}</span> / <span style="color:#a5f3fc;">${sgastPg}</span></span><span class="pg-unit">ast/g</span></div>
          <span class="stats-total-count">${passPg} pass/g · ${toPg} TO/g</span>
        </div>
      </div>
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Defending</span><span class="stats-total-count">${steals} stl · ${blocks} blk · ${rebounds} reb</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#34d399;">${stlPg}</span> / <span style="color:#6ee7b7;">${blkPg}</span></span><span class="pg-unit">/g</span></div>
          <span class="stats-total-count">${rebPg} reb/g</span>
        </div>
      </div>
      ${showGoalie ? `
      <div class="stats-metric-card">
        <div class="stats-split-row"><span class="metric-label">Goalie</span><span class="stats-total-count">${saves} sv · ${totalConceded} conc (${centergoalsaves} CG sv)</span></div>
        <div class="stats-split-row" style="justify-content: space-between;"><span class="stats-sub-label" style="font-size: 10px; color: #cbd5e1; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px;">Center / Side SV%</span><span class="stats-total-count">${savesPg} sv/g · ${concPg} conc/g</span></div>
        <div class="stats-split-row" style="margin-top: 2px;">
          <div class="stats-per-game"><span class="metric-val"><span style="color:#22d3ee;">${cgSvPct}%</span> / <span style="color:#38bdf8;">${sgSvPct}%</span></span></div>
          <span class="stats-total-count"><span style="color:#facc15; font-weight:700;">${goldPg}</span> gold · <span style="color:#c084fc; font-weight:700;">${manaPg}</span> mana/g</span>
        </div>
      </div>` : ''}
      ${is4v4 ? `
      <div class="stats-metric-card" style="min-width: 0;">
        <div class="stats-split-row" style="gap: 4px;">
          <span class="metric-label" style="flex-shrink: 0;">Advanced</span>
          <span class="stats-total-count" style="font-weight: 700; color: ${tbpmTier.color}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0;" title="${tbpmTier.name} (${tbpmStr} ${hasOutfieldData ? 'BPM' : 'GBPM'})">${tbpmTier.name} (${tbpmStr} ${hasOutfieldData ? 'BPM' : 'GBPM'})</span>
        </div>
        <div class="stats-split-row" style="margin-top: 2px; align-items: center; justify-content: space-between; gap: 4px; min-width: 0;">
          ${!isGoalieFiltered ? `
          <div class="stats-per-game" style="gap: 2px; flex-shrink: 1; min-width: 0; overflow: hidden;">
            <span class="metric-val" style="color: #38bdf8; font-size: 12px; font-weight: 700; cursor: help; white-space: nowrap;" title="OBPM = 1.50*(cpg - 0.41) + 0.35*(spg - 6.00) + 0.80*(gast/g - 0.0) + 0.18*(sgast/g - 0.0) + 0.04*(kpg - 0.77) + 0.02*(kast/g - 0.37) + 0.04*(pass/g - 14.78) - 0.12*(to/g - 6.40)">${hasOutfieldData ? formatBpm(obpm) : '—'}</span><span class="pg-unit" style="color: #38bdf8; font-weight: 700; font-size: 8.5px; cursor: help;" title="OBPM (Offensive Box Plus-Minus): Evaluates goal scoring, assists, combat offense, passing volume, and turnover deductions relative to 35,452-match outfield baselines.">OBPM</span>
            <span style="color: #475569; margin: 0 1px; font-size: 10px;">·</span>
            <span class="metric-val" style="color: #a7f3d0; font-size: 12px; font-weight: 700; cursor: help; white-space: nowrap;" title="DBPM = 0.22*(stl/g - 2.12) + 0.15*(blk/g - 3.33) + 0.03*(reb/g - 23.14) + 0.12*(kpg - 0.77) + 0.06*(kast/g - 0.37) - 0.20*(dpg - 1.69) - 0.03*(to/g - 6.40)">${hasOutfieldData ? formatBpm(dbpm) : '—'}</span><span class="pg-unit" style="color: #a7f3d0; font-weight: 700; font-size: 8.5px; cursor: help;" title="DBPM (Defensive Box Plus-Minus): Evaluates steals, shots contested/blocked, loose ball rebounds, kills, and death penalties relative to 35,452-match outfield baselines.">DBPM</span>
          </div>` : ''}
          ${showGoalie ? `
          <div class="stats-per-game" style="gap: 2px; flex-shrink: 0;">
            <span class="metric-val" style="color: ${gbpmTier.color}; font-size: 12px; font-weight: 700; cursor: help; white-space: nowrap;" title="GBPM = 1.50*(cgGSAx/g) + 0.35*(sgGSAx/g) + 0.002*(gold/g - 865) + 0.0005*(mana/g - 662)&#10;where cgGSAx = cgTotal*0.344 - cgConceded, sgGSAx = sgTotal*0.899 - sgConceded">${hasGoalieData ? gbpmStr : '—'}</span><span class="pg-unit" style="color: ${gbpmTier.color}; font-weight: 700; font-size: 8.5px; cursor: help;" title="GBPM (Goalie Box Plus-Minus): Goals Saved Above Expected (GSAx) evaluated across center and side shots faced plus lane resource economy pace relative to 12,008 goalie match baselines.">GBPM</span>
          </div>` : ''}
        </div>
        ${(!isFiltered && goalieMatches > 0 && outfieldMatches > 0) ? `
        <div class="stats-split-row" style="margin-top: 2px;">
          <span class="stats-total-count" style="color: #94a3b8; font-size: 10px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${goalieMatches} goalie · ${outfieldMatches} outfield match${outfieldMatches > 1 ? 'es' : ''}</span>
        </div>` : ''}
      </div>
      ` : ''}
    `;
  }
}

export function initStatsScreen() {
  document.getElementById('user-stats-card')?.addEventListener('click', openAdvancedStatsModal);
  ['stats-modal-close-btn', 'stats-modal-done-btn'].forEach(id => document.getElementById(id)?.addEventListener('click', closeAdvancedStatsModal));

  ['4v4', '1v1'].forEach(mode => {
    const tabEl = document.getElementById(`stats-tab-${mode}`) || (mode === '4v4' ? document.getElementById('stats-tab-3v3') : null);
    tabEl?.addEventListener('click', () => {
      activeStatsTab = mode;
      const tab4v4 = document.getElementById('stats-tab-4v4') || document.getElementById('stats-tab-3v3');
      const tab1v1 = document.getElementById('stats-tab-1v1');
      tab4v4?.classList.toggle('active', mode === '4v4');
      tab1v1?.classList.toggle('active', mode === '1v1');
      renderAdvancedStatsPane();
    });
  });

  const filterSelect = document.getElementById('stats-class-filter');
  filterSelect?.addEventListener('change', (e) => {
    activeClassFilter = e.target.value;
    renderAdvancedStatsPane();
  });

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && document.getElementById('stats-modal')?.style.display !== 'none') {
      closeAdvancedStatsModal();
    }
  });

  updateStatsBanner();
}
