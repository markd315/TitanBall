import { gameState } from './state.js';
import { initCanvas, clearScreen, drawImageCam } from './render/canvas.js';
import { initMasteries, loadMasteriesForTitan, validateMasteries } from './screens/masteries.js';
import { initBuildOrderPlanner, updatePlanBuildButtonVisibility } from './screens/buildOrderPlanner.js';
import { drawCredits } from './screens/credits.js';
import { initKeyboard, setControlPreset } from './input/keyboard.js';
import { initMouse } from './input/mouse.js';
import { initMobileControls, updateMobileControls } from './input/mobile.js';
import { GamePhase } from './constants.js';
import { updateCamera } from './util/math.js';
import { initAssets, AssetManager } from './assets/sprites.js';
import { drawPlayers } from './render/players.js';
import { drawMinions } from './render/minions.js';
import { drawAimAndRangeIndicators } from './render/aim.js';
import { drawEffectIcons } from './render/effects.js';
import { drawBall, displayBallArrow } from './render/ball.js';
import { drawGoals } from './render/goals.js';
import { drawAllPseudotextures } from './render/pseudotextures.js';
import { drawHud, drawHealthBars } from './render/hud.js';
import { drawClassStatsOverlay, formatClassTooltip, CLASS_INFO, computeStatWithMastery, getActiveMasteries, getAbilityCd, loadGameConfig, TAG_COLORS, TAG_ICONS, SKILL_RANKS } from './render/classStats.js';
import { login, joinQueue, checkGame, register, startTutorial } from './network/auth.js';
import { connectGame, disconnectGame } from './network/socket.js';
import { warmServer, recordUserActivity } from './network/warm.js';

let ctx;
let lastTime = 0;
let pollingInterval = null;
let currentScreen = 'login';
let lastPhase = null;
let lastScreen = null;
let idleStart = null;
window.warmExpired = false;

function updateOverlays() {
  if (gameState.phase === lastPhase && currentScreen === lastScreen) return;
  lastPhase = gameState.phase;
  lastScreen = currentScreen;

  const loginOverlay = document.getElementById('login-overlay');
  const signupOverlay = document.getElementById('signup-overlay');
  const modeOverlay = document.getElementById('mode-overlay');
  const lobbyOverlay = document.getElementById('lobby-overlay');
  
  if (!loginOverlay || !signupOverlay || !modeOverlay || !lobbyOverlay) return;

  loginOverlay.style.display = 'none';
  signupOverlay.style.display = 'none';
  modeOverlay.style.display = 'none';
  lobbyOverlay.style.display = 'none';

  if (gameState.phase === GamePhase.CREDITS) {
    if (!sessionStorage.getItem('accessToken')) {
      if (currentScreen === 'login') {
        loginOverlay.style.display = 'flex';
      } else {
        signupOverlay.style.display = 'flex';
      }
    }
  } else if (gameState.phase === GamePhase.SHOW_GAME_MODES) {
    modeOverlay.style.display = 'flex';
    const usernameSpan = document.getElementById('player-username');
    if (usernameSpan) {
      usernameSpan.textContent = sessionStorage.getItem('username') || 'Player';
    }
  } else if (gameState.phase === GamePhase.WAIT_FOR_GAME) {
    lobbyOverlay.style.display = 'flex';
  }
}

function startQueuePolling() {
  if (pollingInterval) clearInterval(pollingInterval);
  
  pollingInterval = setInterval(async () => {
    try {
      const status = await checkGame();
      if (status && status !== 'WAITING' && status !== 'NOT QUEUED') {
        clearInterval(pollingInterval);
        pollingInterval = null;
        gameState.gameID = status;
        gameState.phase = GamePhase.COUNTDOWN;
        connectGame(status);
      }
    } catch (e) {
      console.error("Queue poll error:", e);
    }
  }, 1000);
}

function stopQueuePolling() {
  if (pollingInterval) {
    clearInterval(pollingInterval);
    pollingInterval = null;
  }
}

async function checkAndRejoinActiveGame() {
  const token = sessionStorage.getItem('accessToken');
  if (!token) return;
  try {
    const status = await checkGame();
    if (status && status !== 'NOT QUEUED') {
      if (status === 'WAITING') {
        const lastQueueSize = sessionStorage.getItem('lastQueueSize') || '4';
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = `${lastQueueSize}v${lastQueueSize}`;
        gameState.phase = GamePhase.WAIT_FOR_GAME;
        startQueuePolling();
      } else {
        gameState.gameID = status;
        gameState.phase = GamePhase.COUNTDOWN;
        connectGame(status);
      }
    }
  } catch (e) {
    console.error("Failed to check active game:", e);
  }
}

function initUIListeners() {
  // Session expired refresh button
  const sessionRefreshBtn = document.getElementById('session-refresh-btn');
  if (sessionRefreshBtn) {
    sessionRefreshBtn.addEventListener('click', () => {
      window.location.reload();
    });
  }

  // Login click
  const loginBtn = document.getElementById('login-btn');
  if (loginBtn) {
    loginBtn.addEventListener('click', async () => {
      const email = document.getElementById('login-email').value || 'markd315@gmail.com';
      const pass = document.getElementById('login-pass').value || 'pass';
      const errorDiv = document.getElementById('login-error');
      
      try {
        if (errorDiv) errorDiv.style.display = 'none';
        const data = await login(email, pass);
        sessionStorage.setItem('username', email.split('@')[0]);
        gameState.phase = GamePhase.SHOW_GAME_MODES;
        checkAndRejoinActiveGame();
      } catch (err) {
        console.error(err);
        if (errorDiv) {
          errorDiv.textContent = 'Invalid credentials or server offline.';
          errorDiv.style.display = 'block';
        }
      }
    });
  }

  // Toggle Sign Up overlay
  const signupToggleBtn = document.getElementById('signup-toggle-btn');
  if (signupToggleBtn) {
    signupToggleBtn.addEventListener('click', () => {
      currentScreen = 'signup';
      const errorDiv = document.getElementById('signup-error');
      const successDiv = document.getElementById('signup-success');
      if (errorDiv) errorDiv.style.display = 'none';
      if (successDiv) successDiv.style.display = 'none';
      updateOverlays();
    });
  }

  // Toggle back to Login overlay
  const signupBackBtn = document.getElementById('signup-back-btn');
  if (signupBackBtn) {
    signupBackBtn.addEventListener('click', () => {
      currentScreen = 'login';
      const errorDiv = document.getElementById('login-error');
      if (errorDiv) errorDiv.style.display = 'none';
      updateOverlays();
    });
  }

  // Sign Up click
  const signupBtn = document.getElementById('signup-btn');
  if (signupBtn) {
    signupBtn.addEventListener('click', async () => {
      const username = document.getElementById('signup-username').value;
      const email = document.getElementById('signup-email').value;
      const pass = document.getElementById('signup-pass').value;
      const errorDiv = document.getElementById('signup-error');
      const successDiv = document.getElementById('signup-success');

      if (!username || !email || !pass) {
        if (errorDiv) {
          errorDiv.textContent = 'Please fill out all fields.';
          errorDiv.style.display = 'block';
        }
        return;
      }

      try {
        if (errorDiv) errorDiv.style.display = 'none';
        if (successDiv) successDiv.style.display = 'none';
        await register(email, username, pass);
        
        if (successDiv) {
          successDiv.textContent = 'Account created! Logging in...';
          successDiv.style.display = 'block';
        }

        // Auto login on success
        setTimeout(async () => {
          try {
            const data = await login(email, pass);
            sessionStorage.setItem('username', username);
            gameState.phase = GamePhase.SHOW_GAME_MODES;
            checkAndRejoinActiveGame();
          } catch (loginErr) {
            console.error(loginErr);
            currentScreen = 'login';
            updateOverlays();
          }
        }, 1200);

      } catch (err) {
        console.error(err);
        if (errorDiv) {
          errorDiv.textContent = 'Registration failed. Email/username might be taken.';
          errorDiv.style.display = 'block';
        }
      }
    });
  }

  // Selected Match Size state
  let selectedMatchSize = parseInt(sessionStorage.getItem('lastQueueSize') || '4');
  
  const sizeDisplay = document.getElementById('match-size-display');
  const sizeDownBtn = document.getElementById('size-down-btn');
  const sizeUpBtn = document.getElementById('size-up-btn');

  if (sizeDisplay) {
    sizeDisplay.textContent = `${selectedMatchSize}v${selectedMatchSize}`;
  }
  
  if (sizeDownBtn && sizeUpBtn && sizeDisplay) {
    sizeDownBtn.addEventListener('click', () => {
      if (selectedMatchSize > 2) {
        selectedMatchSize--;
        sizeDisplay.textContent = `${selectedMatchSize}v${selectedMatchSize}`;
        sessionStorage.setItem('lastQueueSize', selectedMatchSize);
      }
    });
    sizeUpBtn.addEventListener('click', () => {
      if (selectedMatchSize < 8) {
        selectedMatchSize++;
        sizeDisplay.textContent = `${selectedMatchSize}v${selectedMatchSize}`;
        sessionStorage.setItem('lastQueueSize', selectedMatchSize);
      }
    });
  }

  // Partner List UI management
  const partnerInput = document.getElementById('partner-input');
  const addPartnerBtn = document.getElementById('add-partner-btn');
  const partnerListContainer = document.getElementById('partner-list-container');
  
  let partners = [];
  try {
    partners = JSON.parse(sessionStorage.getItem('partners') || '[]');
  } catch (e) {
    partners = [];
  }
  
  function renderPartners() {
    if (!partnerListContainer) return;
    partnerListContainer.innerHTML = '';
    if (partners.length === 0) {
      partnerListContainer.innerHTML = '<span style="color: #888; font-style: italic;">No partners added</span>';
      return;
    }
    partners.forEach((p, idx) => {
      const item = document.createElement('div');
      item.style.display = 'flex';
      item.style.justifyContent = 'space-between';
      item.style.alignItems = 'center';
      item.style.padding = '4px 8px';
      item.style.background = 'rgba(255,255,255,0.05)';
      item.style.borderRadius = '4px';
      item.style.color = '#fff';
      
      const name = document.createElement('span');
      name.textContent = p;
      
      const delBtn = document.createElement('button');
      delBtn.textContent = '✕';
      delBtn.style.background = 'none';
      delBtn.style.border = 'none';
      delBtn.style.color = '#f87171';
      delBtn.style.cursor = 'pointer';
      delBtn.style.fontSize = '12px';
      delBtn.style.padding = '0';
      delBtn.style.margin = '0';
      
      delBtn.addEventListener('click', () => {
        partners.splice(idx, 1);
        sessionStorage.setItem('partners', JSON.stringify(partners));
        renderPartners();
      });
      
      item.appendChild(name);
      item.appendChild(delBtn);
      partnerListContainer.appendChild(item);
    });
  }
  
  if (addPartnerBtn && partnerInput) {
    addPartnerBtn.addEventListener('click', () => {
      const val = partnerInput.value.trim();
      if (val && !partners.includes(val)) {
        partners.push(val);
        sessionStorage.setItem('partners', JSON.stringify(partners));
        partnerInput.value = '';
        renderPartners();
      }
    });
    partnerInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        addPartnerBtn.click();
      }
    });
  }
  
  renderPartners();

  function getPlayerIndexForSize(size) {
    if (size === 3) return 0;
    if (size === 4) return 1;
    if (size === 5) return 2;
    if (size === 1) return 4;
    if (size === 2) return 5;
    if (size === 6) return 6;
    if (size === 7) return 7;
    if (size === 8) return 8;
    return 1; // fallback
  }

  // Join Team Match click
  const queueTeamBtn = document.getElementById('queue-team-btn');
  if (queueTeamBtn) {
    queueTeamBtn.addEventListener('click', async () => {
      try {
        const classSelect = document.getElementById('class-select');
        const classSel = classSelect ? classSelect.value : 'WARRIOR';
        const classError = document.getElementById('class-select-error');

        const activeMasteries = gameState.controlsHeld.masteries;
        if (!validateMasteries(activeMasteries)) {
          if (classError) {
            classError.textContent = `Error: Invalid masteries for ${classSel}! You must allocate exactly 10 points before queuing.`;
            classError.style.display = 'block';
          }
          return;
        }

        if (classError) classError.style.display = 'none';
        
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = `${selectedMatchSize}v${selectedMatchSize}`;
        const lobbyTitle = document.querySelector('#lobby-overlay h2');
        if (lobbyTitle) lobbyTitle.textContent = 'Searching Match';
        const lobbyStatus = document.querySelector('#lobby-overlay .stat-value[style*="pulse"]');
        if (lobbyStatus) lobbyStatus.textContent = 'FINDING PLAYERS...';
        
        const playerIndex = getPlayerIndexForSize(selectedMatchSize);
        const code = `/${playerIndex}/0/1/10/2/9999/10/12`;
        const partnersCsv = partners.join(',');
        await joinQueue(code, classSel, partnersCsv);
        gameState.is3v3 = true;
        gameState.phase = GamePhase.WAIT_FOR_GAME;
        startQueuePolling();
      } catch (err) {
        console.error(err);
      }
    });
  }

  // Join 1v1 click
  const queue1v1Btn = document.getElementById('queue-1v1-btn');
  if (queue1v1Btn) {
    queue1v1Btn.addEventListener('click', async () => {
      try {
        const classSelect = document.getElementById('class-select');
        const classError = document.getElementById('class-select-error');
        const classSel = classSelect ? classSelect.value : 'WARRIOR';

        if (classSelect && classSelect.value === 'GOALIE') {
          if (classError) {
            classError.textContent = "Error: Cannot queue for 1v1 Scrimmage as a Goalie. Please select a Titan class.";
            classError.style.display = 'block';
          }
          return;
        }

        const activeMasteries = gameState.controlsHeld.masteries;
        if (!validateMasteries(activeMasteries)) {
          if (classError) {
            classError.textContent = `Error: Invalid masteries for ${classSel}! You must allocate exactly 10 points before queuing.`;
            classError.style.display = 'block';
          }
          return;
        }

        if (classError) classError.style.display = 'none';
        
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = 'Scrimmage';
        const lobbyTitle = document.querySelector('#lobby-overlay h2');
        if (lobbyTitle) lobbyTitle.textContent = 'Searching Match';
        const lobbyStatus = document.querySelector('#lobby-overlay .stat-value[style*="pulse"]');
        if (lobbyStatus) lobbyStatus.textContent = 'FINDING PLAYERS...';
        
        await joinQueue('/4/1/1/5/2/9999/10/12', classSel, ''); // index 4 is 1v1, goalieIndex 1 is off
        gameState.is3v3 = false;
        gameState.phase = GamePhase.WAIT_FOR_GAME;
        startQueuePolling();
      } catch (err) {
        console.error(err);
      }
    });
  }

  // Leave queue click
  const leaveBtn = document.getElementById('leave-queue-btn');
  if (leaveBtn) {
    leaveBtn.addEventListener('click', async () => {
      try {
        stopQueuePolling();
        const token = sessionStorage.getItem('accessToken');
        await fetch('/pages/titanball/api/leave', {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${token}` }
        });
        gameState.phase = GamePhase.SHOW_GAME_MODES;
      } catch (err) {
        console.error(err);
      }
    });
  }

  // Play Tutorial click
  const tutorialBtn = document.getElementById('tutorial-btn');
  if (tutorialBtn) {
    tutorialBtn.addEventListener('click', async () => {
      try {
        gameState.is3v3 = false;

        // Pre-unlock narration audios to avoid browser autoplay policy blocks
        for (let i = 0; i <= 4; i++) {
          const aud = AssetManager.audio['tut' + i];
          if (aud) {
            const oldVol = aud.volume;
            aud.volume = 0;
            aud.play().then(() => {
              aud.pause();
              aud.currentTime = 0;
              aud.volume = oldVol;
            }).catch(e => console.warn("Audio pre-unlock skipped:", e));
          }
        }

        // Set search overlay indicators for Tutorial
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = 'Tutorial';
        const lobbyTitle = document.querySelector('#lobby-overlay h2');
        if (lobbyTitle) lobbyTitle.textContent = 'Preparing Tutorial';
        const lobbyStatus = document.querySelector('#lobby-overlay .stat-value[style*="pulse"]');
        if (lobbyStatus) lobbyStatus.textContent = 'LOADING RESOURCE...';

        const gameId = await startTutorial();
        gameState.phase = GamePhase.WAIT_FOR_GAME;
        startQueuePolling();
      } catch (err) {
        console.error(err);
      }
    });
  }

  // Logout click
  const logoutBtn = document.getElementById('logout-btn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      sessionStorage.removeItem('accessToken');
      sessionStorage.removeItem('refreshToken');
      sessionStorage.removeItem('username');
      sessionStorage.removeItem('classSelection');
      sessionStorage.removeItem('titanMasteries');
      sessionStorage.removeItem('partners');
      sessionStorage.removeItem('controlPreset');
      sessionStorage.removeItem('goalieBuildOrder');
      sessionStorage.removeItem('lastQueueSize');
      gameState.phase = GamePhase.CREDITS;
      currentScreen = 'login';
      updateOverlays();
    });
  }

  // Class Select dropdown change
  const classSelect = document.getElementById('class-select');
  const classDesc = document.getElementById('class-select-desc');
  if (classSelect) {
    const savedClass = sessionStorage.getItem('classSelection') || 'WARRIOR';
    
    // Cache all master options from HTML
    const allClassOptions = Array.from(classSelect.options).map(opt => ({
      value: opt.value,
      text: opt.textContent,
      title: formatClassTooltip(opt.value),
      tags: (CLASS_INFO[opt.value.toUpperCase()] && CLASS_INFO[opt.value.toUpperCase()].tags) || []
    }));

    classSelect.value = savedClass;
    gameState.controlsHeld.classSelection = savedClass;
    loadMasteriesForTitan(savedClass);
    
    const classError = document.getElementById('class-select-error');

    function _updateClassInfoDisplay(classKey) {
      if (!classKey) return;
      const info = CLASS_INFO[classKey.toUpperCase()];
      if (!info) return;

      const tooltipText = formatClassTooltip(classKey);
      classSelect.title = tooltipText;

      if (classDesc) {
        const masteries = getActiveMasteries();
        const hp = computeStatWithMastery('hp', info.rawHp, masteries.health);
        const spd = computeStatWithMastery('speed', info.rawSpeed, masteries.speed);
        const thr = computeStatWithMastery('throwPower', info.rawThrow, masteries.shot);
        const stl = computeStatWithMastery('stealRad', info.rawSteal, masteries.stealRadius);

        const tagBadgesHtml = (info.tags || []).map(t => {
          const color = TAG_COLORS[t] || '#4deeea';
          const icon = TAG_ICONS[t] || '';
          return `<span class="tag-badge" style="background:rgba(255,255,255,0.08);border-color:${color};color:${color};">${icon} ${t}</span>`;
        }).join(' ');

        const abText = info.abilities.map(ab => {
          const cdInfo = getAbilityCd(ab, masteries);
          const cdDisplay = cdInfo ? (cdInfo.isModified ? `${cdInfo.display} (${cdInfo.bonusStr})` : cdInfo.display) : null;
          const cdColor = cdInfo && cdInfo.isModified ? '#60a5fa' : '#67e8f9';
          return `<div style="margin-bottom:4px;"><span style="color:#ffd700;font-weight:bold;">${ab.label} (${ab.name}):</span> ${cdDisplay ? `<span style="color:${cdColor};font-size:11px;font-weight:bold;margin-left:4px;">[${cdDisplay}]</span> ` : ''}<span style="color:#d0e5dd;">${ab.desc}</span></div>`;
        }).join('');
        
        function _renderBarHtml(label, statObj, unit = '', decimals = 0) {
          const baseStr = decimals > 0 ? statObj.baseVal.toFixed(decimals) : statObj.baseVal;
          const bonusStr = statObj.bonusVal > 0.001 
            ? ` <span style="color:#60a5fa;">(+${decimals > 0 ? statObj.bonusVal.toFixed(decimals) : statObj.bonusVal.toFixed(1)})</span>` 
            : '';
          const bonusPct = statObj.totalPct > statObj.basePct 
            ? (statObj.totalPct - statObj.basePct) 
            : (statObj.bonusVal > 0.001 ? 2 : 0);

          return `
            <div>
              <div style="display:flex;justify-content:space-between;color:#94c2b5;margin-bottom:2px;">
                <span>${label}</span>
                <span style="color:#fff;">${baseStr}${bonusStr}${unit} <span style="color:#8abcb0;font-size:10px;">[${statObj.basePct}%ile]</span></span>
              </div>
              <div style="background:rgba(0,0,0,0.6);height:7px;border-radius:3px;overflow:hidden;border:1px solid rgba(255,255,255,0.15);display:flex;">
                <div style="background:${statObj.baseColor};height:100%;width:${statObj.basePct}%;"></div>
                ${bonusPct > 0 ? `<div style="background:#3b82f6;height:100%;width:${bonusPct}%;"></div>` : ''}
              </div>
            </div>
          `;
        }

        classDesc.innerHTML = `
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px;flex-wrap:wrap;gap:4px;">
            <div style="color:#ffd700;font-weight:bold;font-size:14px;">${info.name} <span style="color:#4deeea;font-size:11px;font-weight:normal;">[${info.role}]</span></div>
            <div style="display:flex;flex-wrap:wrap;">${tagBadgesHtml}</div>
          </div>
          <div style="margin-bottom:8px;color:#e0f0ec;font-size:12px;">${info.overview}</div>
          <div style="margin-bottom:8px;font-size:12px;">${abText}</div>
          
          <div style="border-top:1px solid rgba(255,215,0,0.2);padding-top:6px;margin-top:6px;">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px;">
              <span style="font-size:11px;font-weight:bold;color:#ffd700;">PERCENTILE STATS</span>
              <span style="font-size:10px;color:#8abcb0;">🔴 &lt;33% &nbsp;🟡 33-66% &nbsp;🟢 ≥66% &nbsp;|&nbsp; <span style="color:#60a5fa;">🔵 Mastery</span></span>
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:6px 12px;font-size:11px;">
              ${_renderBarHtml('Health', hp, ' HP', 0)}
              ${_renderBarHtml('Speed', spd, '', 2)}
              ${_renderBarHtml('Throw Range', thr, '', 2)}
              ${_renderBarHtml('Steal Radius', stl, 'px', 0)}
            </div>
          </div>
        `;
      }
    }

    // Populate native mouseover tooltips on all option elements
    Array.from(classSelect.options).forEach(opt => {
      opt.title = formatClassTooltip(opt.value);
    });

    _updateClassInfoDisplay(savedClass);

    // Dynamic DDL filtering by category tag
    function applyClassFilter(tag) {
      const currentSelected = classSelect.value;
      classSelect.innerHTML = '';
      
      let filteredOptions = allClassOptions.filter(opt => {
        if (!tag || tag === 'ALL') return true;
        return opt.tags && opt.tags.includes(tag);
      });

      if (tag && tag !== 'ALL' && SKILL_RANKS[tag]) {
        const rankList = SKILL_RANKS[tag];
        filteredOptions.sort((a, b) => {
          const idxA = rankList.indexOf(a.value.toUpperCase());
          const idxB = rankList.indexOf(b.value.toUpperCase());
          const posA = idxA === -1 ? 999 : idxA;
          const posB = idxB === -1 ? 999 : idxB;
          return posA - posB;
        });
      }

      filteredOptions.forEach(opt => {
        const optEl = document.createElement('option');
        optEl.value = opt.value;
        optEl.textContent = opt.text;
        optEl.title = opt.title;
        classSelect.appendChild(optEl);
      });

      // Keep current selection if still visible, otherwise select first match
      const stillVisible = filteredOptions.some(opt => opt.value === currentSelected);
      if (stillVisible) {
        classSelect.value = currentSelected;
      } else if (filteredOptions.length > 0) {
        classSelect.value = filteredOptions[0].value;
        gameState.controlsHeld.classSelection = filteredOptions[0].value;
        sessionStorage.setItem('classSelection', filteredOptions[0].value);
        updatePlanBuildButtonVisibility();
      }

      _updateClassInfoDisplay(classSelect.value);
    }

    const filterChips = document.querySelectorAll('#class-filter-bar .filter-chip');
    filterChips.forEach(chip => {
      chip.addEventListener('click', () => {
        filterChips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        const tag = chip.getAttribute('data-tag');
        applyClassFilter(tag);
      });
    });

    classSelect.addEventListener('change', (e) => {
      if (classError) {
        classError.style.display = 'none';
      }
      gameState.controlsHeld.classSelection = e.target.value;
      sessionStorage.setItem('classSelection', e.target.value);
      loadMasteriesForTitan(e.target.value);
      _updateClassInfoDisplay(e.target.value);
      updatePlanBuildButtonVisibility();
    });

    // Re-render class info preview card & option tooltips immediately when masteries are saved
    window.addEventListener('masteriesUpdated', () => {
      _updateClassInfoDisplay(classSelect.value);
      allClassOptions.forEach(opt => {
        opt.title = formatClassTooltip(opt.value);
      });
      Array.from(classSelect.options).forEach(opt => {
        opt.title = formatClassTooltip(opt.value);
      });
    });

    // Set initial visibility of Plan Build Order button
    updatePlanBuildButtonVisibility();
  }

  // Controls Layout dropdown change
  const controlsSelect = document.getElementById('controls-select');
  if (controlsSelect) {
    controlsSelect.addEventListener('change', async (e) => {
      await setControlPreset(e.target.value);
    });
  }
}

let lastNarrationPhase = -1;

function handleTutorialNarration(game) {
  if (!game || !game.gameId || !game.gameId.startsWith('tutorial-')) return;
  const nPhase = game.narrationPhase || 0;
  if (nPhase !== lastNarrationPhase) {
    // Stop all narration audios
    for (let i = 0; i <= 4; i++) {
      const aud = AssetManager.audio['tut' + i];
      if (aud) {
        aud.pause();
        aud.currentTime = 0;
      }
    }
    // Play new narration audio if valid
    if (nPhase > 0 && nPhase <= 5) {
      const newAud = AssetManager.audio['tut' + (nPhase - 1)];
      if (newAud) {
        newAud.play().catch(e => console.warn("Audio play blocked by browser autoplay policy:", e));
      }
    }
    lastNarrationPhase = nPhase;
  }
}

function drawIngame(ctx, dt) {
  const game = gameState.game;
  if (!game) return;
  
  handleTutorialNarration(game);
  updateCamera(game, gameState);
  const { camX, camY } = gameState;
  
  const isGoalie = game.underControl && game.underControl.type === 'GOALIE';
  if (isGoalie) {
    ctx.save();
    ctx.scale(0.9375, 1.0);
  }

  // Draw Field
  if (AssetManager.images['field']) {
    ctx.drawImage(AssetManager.images['field'], Math.floor(1 - camX), Math.floor(1 - camY));
  } else {
    ctx.fillStyle = '#0f3d0f';
    ctx.fillRect(0, 0, 1920, 960);
  }

  // Draw thin blue boundary lines for attacking/defensive thirds (680 and 1368)
  const x1 = 680;
  const x2 = 1368;
  const yMin = 139;
  const yMax = 1079;

  ctx.save();
  ctx.strokeStyle = 'rgba(59, 130, 246, 0.5)';
  ctx.lineWidth = 2;
  // Left line (680)
  ctx.beginPath();
  ctx.moveTo(Math.floor(x1 - camX), Math.floor(yMin - camY));
  ctx.lineTo(Math.floor(x1 - camX), Math.floor(yMax - camY));
  ctx.stroke();
  // Right line (1368)
  ctx.beginPath();
  ctx.moveTo(Math.floor(x2 - camX), Math.floor(yMin - camY));
  ctx.lineTo(Math.floor(x2 - camX), Math.floor(yMax - camY));
  ctx.stroke();
  ctx.restore();
  
  drawGoals(ctx, game, camX, camY);
  drawAllPseudotextures(ctx, game, camX, camY);
  drawPlayers(ctx, game, camX, camY);
  drawMinions(ctx, game, camX, camY);
  drawAimAndRangeIndicators(ctx, game, gameState.controlsHeld, camX, camY);
  drawEffectIcons(ctx, game, camX, camY);
  drawHealthBars(ctx, game, camX, camY);
  drawBall(ctx, game, camX, camY);
  displayBallArrow(ctx, game, camX, camY);

  if (isGoalie) {
    ctx.restore();
  }

  drawHud(ctx, game, gameState);

  // Draw Goal Scored Asset Overlay (local detection via score changes)
  if (gameState.localGoalVisible) {
    if (Date.now() - gameState.localGoalTime > 3000) {
      gameState.localGoalVisible = false;
    } else {
      const img = AssetManager.images['goal'];
      let gy = 960 / 2 - 100;
      if (img && img.width > 0) {
        const gx = 1920 / 2 - img.width / 2;
        gy = 960 / 2 - img.height / 2 - 100;
        ctx.drawImage(img, gx, gy);
      }
      
      // Draw Text Banner (glowing text)
      ctx.save();
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.font = 'italic bold 70px Impact, Arial, sans-serif';
      
      let txt = "GOAL!";
      let fillStyle = '#3b82f6'; // Vibrant Blue for normal goal
      
      if (gameState.goalComboType === 'DOUBLE') {
        txt = "DOUBLE GOAL!";
        fillStyle = '#ff7f11'; // Bright Orange
      } else if (gameState.goalComboType === 'TRIPLE') {
        txt = "TRIPLE GOAL!";
        fillStyle = '#9d4edd'; // Bright Purple
      } else if (gameState.goalComboType === 'QUAD') {
        txt = "QUAD GOAL!";
        fillStyle = '#ffd700'; // Gold for Quad
      }
      
      const textY = gy + (img && img.height > 0 ? img.height : 100) + 60;
      
      ctx.strokeStyle = 'black';
      ctx.lineWidth = 8;
      ctx.strokeText(txt, 1920 / 2, textY);
      
      ctx.fillStyle = fillStyle;
      ctx.fillText(txt, 1920 / 2, textY);
      ctx.restore();
    }
  }

  // Draw Tutorial Objective Banner Overlay
  if (game.gameId && game.gameId.startsWith('tutorial-')) {
    ctx.save();
    ctx.fillStyle = 'rgba(10, 26, 20, 0.85)';
    ctx.strokeStyle = '#ff7f11';
    ctx.lineWidth = 2;
    ctx.beginPath();
    // roundRect fallback
    if (ctx.roundRect) {
      ctx.roundRect(1920/2 - 250, 40, 500, 80, 12);
    } else {
      ctx.rect(1920/2 - 250, 40, 500, 80);
    }
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = '#ff9f1c';
    ctx.font = 'bold 22px Outfit, Arial, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('TUTORIAL OBJECTIVE', 1920/2, 70);

    ctx.fillStyle = 'white';
    ctx.font = '16px Outfit, Arial, sans-serif';
    let objText = '';
    const tPhase = game.tutorialPhase || 1;
    if (tPhase === 1) objText = 'Catch the bouncing ball to perform a Rebound!';
    else if (tPhase === 2) objText = 'Steal the ball from the enemy Golem!';
    else if (tPhase === 3) objText = 'Use abilities (Q/W/E) to defeat the Golem!';
    else if (tPhase === 4) objText = 'Shoot the ball into the Side Goal!';
    else if (tPhase === 5) objText = 'Score a Goal in the Main Hoop!';
    ctx.fillText(objText, 1920/2, 103);
    ctx.restore();
  }
}

function gameLoop(timestamp) {
  const dt = timestamp - lastTime;
  lastTime = timestamp;

  // Track active gameplay / user interaction to reset the 4-hour idle timer
  const isGameplay = gameState.phase === GamePhase.INGAME || 
                     gameState.phase === GamePhase.COUNTDOWN || 
                     gameState.phase === GamePhase.SCORE_FREEZE ||
                     gameState.phase === GamePhase.WAIT_FOR_GAME ||
                     gameState.phase === GamePhase.TUTORIAL ||
                     gameState.phase === GamePhase.TUTORIAL_START;

  if (isGameplay) {
    recordUserActivity();
  }

  const IDLE_LIMIT_MS = 4 * 60 * 60 * 1000; // 4 hours
  if (Date.now() - (window.lastUserActivity || Date.now()) > IDLE_LIMIT_MS) {
    window.warmExpired = true;
  }

  if (window.warmExpired) {
    const expiredOverlay = document.getElementById('session-expired-overlay');
    if (expiredOverlay && expiredOverlay.style.display !== 'flex') {
      expiredOverlay.style.display = 'flex';
    }
    requestAnimationFrame(gameLoop);
    return;
  }

  clearScreen(ctx);
  updateOverlays();
  updateMobileControls(gameState.game);

  // If already logged in, skip login screen
  if (gameState.phase === GamePhase.CREDITS && sessionStorage.getItem('accessToken')) {
    gameState.phase = GamePhase.SHOW_GAME_MODES;
  }

  switch (gameState.phase) {
    case GamePhase.CREDITS:
      drawCredits(ctx);
      break;
    case GamePhase.CONTROLS:
      ctx.fillStyle = 'white';
      ctx.font = '50px Arial';
      ctx.fillText('Controls - Press Space to Start Match Selection', 200, 200);
      break;
    case GamePhase.SHOW_GAME_MODES:
      // Rendered via HTML overlay
      break;
    case GamePhase.WAIT_FOR_GAME:
      // Rendered via HTML overlay
      break;
    case GamePhase.COUNTDOWN:
      if (gameState.is3v3) {
        drawDraftShowcase(ctx);
        
        // Draw top banner for draft showcase
        const game = gameState.game;
        if (game && game.underControl) {
          const myTitan = game.underControl;
          ctx.save();
          ctx.fillStyle = 'rgba(10, 26, 20, 0.85)';
          ctx.fillRect(1920 / 2 - 350, 20, 700, 80);
          ctx.strokeStyle = '#ffd700';
          ctx.lineWidth = 2;
          ctx.strokeRect(1920 / 2 - 350, 20, 700, 80);
          
          ctx.fillStyle = '#ffd700';
          ctx.font = 'bold 22px Outfit, Arial, sans-serif';
          ctx.textAlign = 'center';
          ctx.fillText('YOUR DRAFT DETAILS', 1920 / 2, 50);
          
          ctx.fillStyle = 'white';
          ctx.font = '16px Outfit, Arial, sans-serif';
          const isGoalie = myTitan.type === 'GOALIE';
          const posDesc = isGoalie ? "Goal Box" : "Field Player";
          ctx.fillText(`TEAM: ${myTitan.team}  |  CLASS: ${myTitan.type}  |  ROLE: ${posDesc}`, 1920 / 2, 83);
          ctx.restore();
        }
      } else {
        // Draw the field and players
        drawIngame(ctx, dt);
        
        // Highlight own Titan
        const game = gameState.game;
        if (game) {
          const myTitan = game.underControl;
          const { camX, camY } = gameState;
          if (myTitan) {
            const rx = myTitan.X + myTitan.width / 2 - camX;
            const ry = myTitan.Y + myTitan.height / 2 - camY;
            ctx.save();
            ctx.strokeStyle = '#ffd700'; // Gold ring
            ctx.lineWidth = 4;
            ctx.beginPath();
            ctx.arc(rx, ry, Math.max(myTitan.width, myTitan.height) * 0.8, 0, Math.PI * 2);
            ctx.stroke();
            
            ctx.fillStyle = '#ffd700';
            ctx.font = 'bold 16px Arial';
            ctx.textAlign = 'center';
            ctx.fillText('YOU', rx, ry - myTitan.height / 2 - 12);
            ctx.restore();
          }
          
          // Draw top banner showing side, position, class
          ctx.save();
          ctx.fillStyle = 'rgba(10, 26, 20, 0.85)';
          ctx.fillRect(1920 / 2 - 350, 40, 700, 80);
          ctx.strokeStyle = '#ffd700';
          ctx.lineWidth = 2;
          ctx.strokeRect(1920 / 2 - 350, 40, 700, 80);
          
          ctx.fillStyle = '#ffd700';
          ctx.font = 'bold 22px Outfit, Arial, sans-serif';
          ctx.textAlign = 'center';
          ctx.fillText('MATCH DETAILS', 1920 / 2, 70);
          
          ctx.fillStyle = 'white';
          ctx.font = '16px Outfit, Arial, sans-serif';
          if (myTitan) {
            const isGoalie = myTitan.type === 'GOALIE';
            const posDesc = isGoalie ? "Goal Box" : `Coordinates (${Math.round(myTitan.X)}, ${Math.round(myTitan.Y)})`;
            ctx.fillText(`TEAM: ${myTitan.team}  |  CLASS: ${myTitan.type}  |  POSITION: ${posDesc}`, 1920 / 2, 103);
          } else {
            ctx.fillText('SPECTATING', 1920 / 2, 103);
          }
          ctx.restore();
        }
        
        // Draw large countdown overlay
        ctx.save();
        ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
        ctx.fillRect(0, 0, 1920, 960);
        
        ctx.fillStyle = '#00ff00';
        ctx.font = 'bold 120px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        const sec = game && game.secondsToStart !== undefined ? Math.max(0, Math.ceil(game.secondsToStart)) : 5;
        ctx.fillText(sec > 0 ? sec.toString() : "GO!", 1920 / 2, 960 / 2);
        ctx.restore();

        // Display class stats & abilities guide during countdown
        drawClassStatsOverlay(ctx, gameState.game, true);
      }
      break;
    case GamePhase.INGAME:
    case GamePhase.SCORE_FREEZE:
      drawIngame(ctx, dt);
      break;
    case GamePhase.ENDED:
      drawGameEnded(ctx);
      break;
    default:
      ctx.fillStyle = 'white';
      ctx.font = '30px Arial';
      ctx.fillText('Phase: ' + gameState.phase, 100, 100);
      break;
  }

  // Draw full Class Abilities & Stats Guide overlay whenever [TAB] is held
  if (gameState.controlsHeld && gameState.controlsHeld.TAB && gameState.game) {
    drawClassStatsOverlay(ctx, gameState.game, false);
  }

  requestAnimationFrame(gameLoop);
}

function jwtDecodeEmail(token) {
  if (!token) return '';
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
    const parsed = JSON.parse(jsonPayload);
    return parsed.sub || '';
  } catch (e) {
    return '';
  }
}

function drawGameEnded(ctx) {
  const game = gameState.game;
  if (!game) {
    ctx.fillStyle = 'white';
    ctx.font = '50px Arial';
    ctx.textAlign = 'center';
    ctx.fillText('Game Over', 1920 / 2, 540);
    return;
  }

  // Determine which team this player was on
  let myTeam = 'HOME';
  if (game.underControl) {
    myTeam = game.underControl.team;
  } else if (game.clients && gameState.token) {
    const email = jwtDecodeEmail(gameState.token);
    const clientDivider = game.clients.find(c => c.email === email);
    if (clientDivider) {
      const selIndex = clientDivider.selection - 1;
      if (game.players && game.players[selIndex]) {
        myTeam = game.players[selIndex].team;
      }
    }
  }

  const team = myTeam === 'HOME' ? game.home : game.away;
  const enemy = myTeam === 'HOME' ? game.away : game.home;
  
  const hasScores = team && enemy;
  const isMatchEnded = game.phase === 'ENDED' || game.ended || (hasScores && (team.score !== enemy.score || team.score > 0 || enemy.score > 0));
  const isDisconnect = !isMatchEnded;
  
  let resultKey = 'tie';
  if (hasScores) {
    if (team.score > enemy.score) resultKey = 'victory';
    else if (team.score < enemy.score) resultKey = 'defeat';
  }

  if (isDisconnect) {
    ctx.fillStyle = '#ef4444';
    ctx.font = 'bold 70px Arial';
    ctx.textAlign = 'center';
    ctx.fillText('CONNECTION LOST', 1920 / 2, 220);
    
    ctx.fillStyle = '#e2e8f0';
    ctx.font = '24px Arial';
    ctx.fillText('Unexpectedly disconnected from the game server.', 1920 / 2, 300);
  } else {
    // Draw Result Image (victory/defeat/tie)
    const img = AssetManager.images[resultKey];
    if (img) {
      const rx = 1920 / 2 - img.width / 2;
      const ry = 120;
      ctx.drawImage(img, rx, ry);
    } else {
      ctx.fillStyle = resultKey === 'victory' ? '#22c55e' : (resultKey === 'defeat' ? '#ef4444' : 'white');
      ctx.font = 'bold 70px Arial';
      ctx.textAlign = 'center';
      ctx.fillText(resultKey.toUpperCase(), 1920 / 2, 220);
    }
  }

  // Draw Subtitle / Final Score
  ctx.save();
  ctx.textAlign = 'center';
  ctx.font = 'bold 36px Arial';
  ctx.fillStyle = '#ff9f1c';
  const homeScore = game.home ? game.home.score : 0;
  const awayScore = game.away ? game.away.score : 0;
  const scoreStr = `Final Score: HOME ${homeScore} - AWAY ${awayScore}`;
  ctx.fillText(scoreStr, 1920 / 2, 380);
  ctx.restore();

  // Draw Stats Table
  const email = jwtDecodeEmail(gameState.token || sessionStorage.getItem('accessToken'));
  if (email && game.stats && game.stats.gamestats) {
    let isGoalie = false;
    if (game.underControl && (game.underControl.type === 'GOALIE' || (game.underControl.type && String(game.underControl.type).toUpperCase() === 'GOALIE'))) {
      isGoalie = true;
    } else if (game.clients && gameState.token) {
      const emailDivider = game.clients.find(c => c.email === email);
      if (emailDivider && game.players) {
        const p = game.players[emailDivider.selection - 1];
        if (p && p.type && String(p.type).toUpperCase() === 'GOALIE') {
          isGoalie = true;
        }
      }
    }

    ctx.save();
    const panelH = isGoalie ? 520 : 460;
    const panelY = isGoalie ? 395 : 420;
    ctx.fillStyle = 'rgba(10, 26, 20, 0.85)';
    ctx.fillRect(1920 / 2 - 320, panelY, 640, panelH);
    ctx.strokeStyle = '#ff7f11';
    ctx.lineWidth = 3;
    ctx.strokeRect(1920 / 2 - 320, panelY, 640, panelH);

    ctx.font = 'bold 26px Arial';
    ctx.fillStyle = '#00ff00';
    ctx.textAlign = 'center';
    ctx.fillText('MATCH STATISTICS', 1920 / 2, panelY + 35);

    let statEntries = [];
    if (isGoalie) {
      statEntries = [
        { label: 'CENTERGOAL SAVES', statIndex: 18, isFloat: false },
        { label: 'CENTERGOALS CONCEDED', statIndex: 20, isFloat: false },
        { label: 'CGSV%', customType: 'CGSV' },
        { label: 'SIDEGOAL SAVES', statIndex: 17, isFloat: false },
        { label: 'SIDEGOALS CONCEDED', statIndex: 19, isFloat: false },
        { label: 'SGSV%', customType: 'SGSV' },
        { label: 'BLOCKS', statIndex: 4, isFloat: false },
        { label: 'LASTHITS', statIndex: 13, isFloat: false },
        { label: 'MINION DAMAGE', statIndex: 14, isFloat: true },
        { label: 'UPGRADES GOLD', statIndex: 15, isFloat: false },
        { label: 'CONSUMABLES GOLD', statIndex: 16, isFloat: false },
        { label: 'MANA SPENT', statIndex: 21, isFloat: false },
        { label: 'STEALS', statIndex: 3, isFloat: false },
        { label: 'REBOUNDS', statIndex: 11, isFloat: false },
        { label: 'PASSES', statIndex: 5, isFloat: false },
        { label: 'TURNOVERS', statIndex: 8, isFloat: false },
        { label: 'GOALS', statIndex: 0, isFloat: false }
      ];
    } else {
      statEntries = [
        { label: 'GOALS', statIndex: 0, isFloat: false },
        { label: 'SIDEGOALS', statIndex: 1, isFloat: false },
        { label: 'POINTS', statIndex: 2, isFloat: true },
        { label: 'STEALS', statIndex: 3, isFloat: false },
        { label: 'BLOCKS', statIndex: 4, isFloat: false },
        { label: 'PASSES', statIndex: 5, isFloat: false },
        { label: 'KILLS', statIndex: 6, isFloat: false },
        { label: 'DEATHS', statIndex: 7, isFloat: false },
        { label: 'TURNOVERS', statIndex: 8, isFloat: false },
        { label: 'KILLASSISTS', statIndex: 9, isFloat: false },
        { label: 'GOALASSISTS', statIndex: 10, isFloat: false },
        { label: 'REBOUND', statIndex: 11, isFloat: false }
      ];
    }

    ctx.font = isGoalie ? '18px Courier New' : '22px Courier New';
    let sy = panelY + 60;
    const lineHeight = isGoalie ? 24 : 30;
    
    statEntries.forEach((entry) => {
      let valStr = '';
      if (entry.customType === 'CGSV') {
        const cgSavesMap = game.stats.gamestats[18];
        const cgConcMap = game.stats.gamestats[20];
        const cgSaves = (cgSavesMap && cgSavesMap[email] !== undefined) ? cgSavesMap[email] : 0;
        const cgConc = (cgConcMap && cgConcMap[email] !== undefined) ? cgConcMap[email] : 0;
        const total = cgSaves + cgConc;
        const pct = total > 0 ? (cgSaves / total) * 100.0 : 100.0;
        valStr = `${pct.toFixed(1)}%`;
      } else if (entry.customType === 'SGSV') {
        const sgSavesMap = game.stats.gamestats[17];
        const sgConcMap = game.stats.gamestats[19];
        const sgSaves = (sgSavesMap && sgSavesMap[email] !== undefined) ? sgSavesMap[email] : 0;
        const sgConc = (sgConcMap && sgConcMap[email] !== undefined) ? sgConcMap[email] : 0;
        const total = sgSaves + sgConc;
        const pct = total > 0 ? (sgSaves / total) * 100.0 : 100.0;
        valStr = `${pct.toFixed(1)}%`;
      } else {
        let val = 0;
        const statMap = game.stats.gamestats[entry.statIndex];
        if (statMap && statMap[email] !== undefined) {
          val = statMap[email];
        }
        if (val === 0 && entry.altIndex !== undefined) {
          const altMap = game.stats.gamestats[entry.altIndex];
          if (altMap && altMap[email] !== undefined) {
            val = altMap[email];
          }
        }
        valStr = entry.isFloat ? val.toFixed(1) : Math.floor(val).toString();
      }
      
      ctx.fillStyle = '#ffffff';
      ctx.textAlign = 'left';
      ctx.fillText(entry.label.padEnd(22, '.'), 1920 / 2 - 220, sy);
      
      ctx.fillStyle = (entry.customType === 'CGSV' || entry.customType === 'SGSV') ? '#4deeea' : '#ff9f1c';
      ctx.textAlign = 'right';
      ctx.fillText(valStr, 1920 / 2 + 220, sy);
      
      sy += lineHeight;
    });

    ctx.restore();
  }

  // Draw return info
  ctx.save();
  ctx.font = 'bold 24px Arial';
  ctx.fillStyle = '#888888';
  ctx.textAlign = 'center';
  const isMobileDev = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) || ('ontouchstart' in window) || (navigator.maxTouchPoints > 0);
  const returnMsg = isMobileDev ? 'Press SPACE or Double-Tap screen to return to lobby menu' : 'Press SPACE to return to lobby menu';
  ctx.fillText(returnMsg, 1920 / 2, 940);
  ctx.restore();
}

function drawDraftShowcase(ctx) {
  const game = gameState.game;
  if (!game || !game.players) {
    ctx.fillStyle = 'white';
    ctx.font = '80px Arial';
    ctx.textAlign = 'center';
    ctx.fillText("PREPARING MATCH...", 1920 / 2, 960 / 2);
    return;
  }

  const homePlayers = game.players.filter(p => p.team === 'HOME');
  const awayPlayers = game.players.filter(p => p.team === 'AWAY');

  // Background panel
  ctx.fillStyle = 'rgba(10, 26, 20, 0.9)';
  ctx.fillRect(0, 0, 1920, 960);
  
  // Draw Center VS
  ctx.save();
  ctx.fillStyle = '#ff7f11';
  ctx.font = 'italic bold 90px Impact, Arial';
  ctx.textAlign = 'center';
  ctx.fillText('VS', 1920 / 2, 960 / 2 - 50);
  ctx.restore();

  const myEmail = jwtDecodeEmail(gameState.token || sessionStorage.getItem('accessToken'));

  // Roster Columns
  const drawColumn = (players, isHome, startX) => {
    ctx.save();
    ctx.fillStyle = isHome ? 'rgba(59, 130, 246, 0.15)' : 'rgba(200, 200, 200, 0.15)';
    ctx.fillRect(startX, 150, 700, 700);
    ctx.strokeStyle = isHome ? '#3b82f6' : '#444444';
    ctx.lineWidth = 4;
    ctx.strokeRect(startX, 150, 700, 700);

    ctx.fillStyle = isHome ? '#3b82f6' : '#ffffff';
    ctx.font = 'bold 40px Arial';
    ctx.textAlign = 'center';
    ctx.fillText(isHome ? 'HOME TEAM DRAFT' : 'AWAY TEAM DRAFT', startX + 350, 210);

    let py = 250;
    
    // Calculate dynamic sizes for cards when player counts are high to prevent overflow
    let cardHeight = 110;
    let spacing = 140;
    let fontSizeText = 24;
    let fontSizeClass = 20;
    let spriteSize = 100;
    let spriteOffset = 5;
    let textOffsetY1 = 45;
    let textOffsetY2 = 80;

    if (players.length > 4) {
      const maxContainerHeight = 550;
      spacing = Math.floor(maxContainerHeight / players.length);
      cardHeight = Math.max(50, Math.floor((maxContainerHeight - 40) / players.length));
      fontSizeText = Math.max(12, Math.floor(cardHeight * 0.22));
      fontSizeClass = Math.max(10, Math.floor(cardHeight * 0.18));
      spriteSize = Math.max(40, cardHeight - 8);
      spriteOffset = Math.floor((cardHeight - spriteSize) / 2);
      textOffsetY1 = Math.floor(cardHeight * 0.4);
      textOffsetY2 = Math.floor(cardHeight * 0.78);
    }

    players.forEach((p, idx) => {
      const origIdx = game.players.indexOf(p);
      const client = game.clients ? game.clients.find(c => c.selection === origIdx + 1) : null;
      const displayName = client && client.email ? client.email.split('@')[0] : `Slot ${idx + 1}: Player`;
      const isLocalUser = Boolean(
        (game.underControl && (p === game.underControl || p.id === game.underControl.id)) ||
        (client && client.email && myEmail && client.email.toLowerCase() === myEmail.toLowerCase())
      );

      // Draw card background
      ctx.fillStyle = 'rgba(0, 0, 0, 0.55)';
      ctx.fillRect(startX + 50, py, 600, cardHeight);
      ctx.strokeStyle = isLocalUser ? 'rgba(255, 255, 0, 0.8)' : (isHome ? 'rgba(59, 130, 246, 0.3)' : 'rgba(68, 68, 68, 0.3)');
      ctx.lineWidth = isLocalUser ? 2 : 1;
      ctx.strokeRect(startX + 50, py, 600, cardHeight);

      // Text details
      const textX = startX + 80;
      const textY = py + textOffsetY1;
      ctx.save();
      ctx.textAlign = 'left';
      ctx.font = `bold ${fontSizeText}px Arial`;
      if (isLocalUser) {
        ctx.fillStyle = '#ffff00';
        ctx.fillText(displayName, textX, textY);
        const textMetrics = ctx.measureText(displayName);
        const underlineY = textY + Math.max(3, Math.round(fontSizeText * 0.15));
        ctx.strokeStyle = '#ffff00';
        ctx.lineWidth = Math.max(2, Math.round(fontSizeText * 0.08));
        ctx.beginPath();
        ctx.moveTo(textX, underlineY);
        ctx.lineTo(textX + textMetrics.width, underlineY);
        ctx.stroke();
      } else {
        ctx.fillStyle = '#ffffff';
        ctx.fillText(displayName, textX, textY);
      }

      ctx.fillStyle = '#ff9f1c';
      ctx.font = `bold ${fontSizeClass}px Courier New`;
      ctx.fillText(`Class: ${p.type}`, textX, py + textOffsetY2);
      ctx.restore();

      // Small stand sprite preview
      const standImg = AssetManager.images[`${p.type}_standR`] || AssetManager.images[`${p.type}_standL`];
      if (standImg) {
        ctx.drawImage(standImg, startX + 490, py + spriteOffset, spriteSize, spriteSize);
      }

      py += spacing;
    });
    ctx.restore();
  };

  drawColumn(homePlayers, true, 150);
  drawColumn(awayPlayers, false, 1070);

  // Banned Overlay
  if (game.bans && game.bans.length > 0) {
    ctx.save();
    ctx.fillStyle = '#ef4444';
    ctx.font = 'bold 24px Courier New';
    ctx.textAlign = 'center';
    const banText = `TACTICAL BANS: ${game.bans.join(' | ')}`;
    ctx.fillText(banText, 1920 / 2, 870);
    ctx.restore();
  }

  // Match Start timer
  ctx.save();
  ctx.fillStyle = '#00ff00';
  ctx.font = 'bold 36px Arial';
  ctx.textAlign = 'center';
  const sec = game.secondsToStart !== undefined ? Math.max(0, Math.ceil(game.secondsToStart)) : 5;
  ctx.fillText(`MATCH STARTING IN: ${sec}s`, 1920 / 2, 920);
  ctx.restore();
}

export function start() {
  ctx = initCanvas();
  initAssets();
  initMasteries();
  initBuildOrderPlanner();
  initKeyboard();
  initMouse();
  initMobileControls();
  initUIListeners();
  
  // Track user interaction to reset 4-hour idle timer
  ['mousemove', 'mousedown', 'keydown', 'touchstart', 'click'].forEach(evt => {
    window.addEventListener(evt, recordUserActivity, { passive: true });
  });

  // Warm the pilot-light server immediately on startup and keep warm every 10 minutes
  warmServer();
  setInterval(warmServer, 600000);

  if (sessionStorage.getItem('accessToken')) {
    checkAndRejoinActiveGame();
  }

  requestAnimationFrame(gameLoop);
}

start();