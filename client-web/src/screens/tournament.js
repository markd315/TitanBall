// screens/tournament.js
// Provides the Tournament Code Modal, custom tournament string parsing, password support, validation, and custom queue entry.

export const PLAYERS_DISP = [
  "3v3",
  "4v4",
  "5v5 (sep goalie)",
  "Single-player",
  "1v1",
  "2v2",
  "6v6",
  "7v7",
  "8v8",
  "2v0 Coop vs AI",
  "3v0 Coop vs AI",
  "4v0 Coop vs AI",
  "5v0 Coop vs AI"
];

export const GOALIE_DISP = [
  "Goalies Enabled",
  "Goalies Disabled",
  "Permanent Goalies"
];

export const AI_DIFF_DISP = [
  "Easy (1200–1700ms)",
  "Medium (500–1200ms)",
  "Hard (200–700ms)"
];

export const DEFAULT_TOURNAMENT_CODE = "/1/0/1/10/2/9999/10/20";

let onQueueCustomMatchCallback = null;

export function parseTournamentCode(rawCode) {
  if (!rawCode || typeof rawCode !== 'string') {
    return { valid: false, error: 'Tournament code cannot be empty.' };
  }

  let code = rawCode.trim();
  let password = '';

  const rawParts = code.split('/');
  let offset = 1;

  if (rawParts.length >= 1 && rawParts[0] !== '') {
    // Format: "password/1/0/..."
    password = rawParts[0];
    offset = 1;
  } else if (rawParts.length >= 2 && rawParts[0] === '') {
    // Format: "/1/0/..." or "/password/1/0/..."
    const firstNum = parseInt(rawParts[1], 10);
    if (isNaN(firstNum)) {
      password = rawParts[1];
      offset = 2;
    } else {
      offset = 1;
    }
  }

  if (rawParts.length < offset + 8) {
    return {
      valid: false,
      error: `Incomplete tournament code. Requires 8 rule parameters (found ${Math.max(0, rawParts.length - offset)} / 8). Format: [password]/players/goalies/bestOf/playTo/winBy/hardWin/suddenDeath/tie[/aiDiff/hybrid]`
    };
  }

  const playerIndex = parseInt(rawParts[offset], 10);
  const goalieIndex = parseInt(rawParts[offset + 1], 10);
  const bestOfIndex = parseInt(rawParts[offset + 2], 10);
  const playToIndex = parseInt(rawParts[offset + 3], 10);
  const winByIndex = parseInt(rawParts[offset + 4], 10);
  const hardWinIndex = parseInt(rawParts[offset + 5], 10);
  const suddenDeathIndex = parseInt(rawParts[offset + 6], 10);
  const tieIndex = parseInt(rawParts[offset + 7], 10);

  if (
    isNaN(playerIndex) ||
    isNaN(goalieIndex) ||
    isNaN(bestOfIndex) ||
    isNaN(playToIndex) ||
    isNaN(winByIndex) ||
    isNaN(hardWinIndex) ||
    isNaN(suddenDeathIndex) ||
    isNaN(tieIndex)
  ) {
    return { valid: false, error: 'All rule parameters in tournament code must be valid integers.' };
  }

  if (playerIndex < 0 || playerIndex >= PLAYERS_DISP.length) {
    return { valid: false, error: `Invalid playerIndex: ${playerIndex}. Must be between 0 and ${PLAYERS_DISP.length - 1}.` };
  }

  if (goalieIndex < 0 || goalieIndex > 2) {
    return { valid: false, error: `Invalid goalieIndex: ${goalieIndex}. 0 = Enabled, 1 = Disabled, 2 = Permanent.` };
  }

  if (playToIndex <= 0) {
    return { valid: false, error: 'Play-to score must be greater than 0 (e.g. 10).' };
  }

  if (winByIndex <= 0) {
    return { valid: false, error: 'Win-by margin must be greater than 0 (e.g. 2).' };
  }

  if (suddenDeathIndex <= 0) {
    return { valid: false, error: 'Sudden death time must be greater than 0 minutes (or 9999 for off).' };
  }

  if (tieIndex <= 0) {
    return { valid: false, error: 'Tie/draw time must be greater than 0 minutes (or 9999 for off).' };
  }

  let aiDiffIndex = null;
  if (rawParts.length >= offset + 9 && rawParts[offset + 8] !== '') {
    aiDiffIndex = parseInt(rawParts[offset + 8], 10);
    if (isNaN(aiDiffIndex) || aiDiffIndex < 0 || aiDiffIndex > 2) {
      aiDiffIndex = 1;
    }
  }

  let isHybrid = false;
  if (rawParts.length >= offset + 10 && rawParts[offset + 9] !== '') {
    isHybrid = rawParts[offset + 9] === '1' || rawParts[offset + 9].toLowerCase() === 'true';
  }

  // Use extracted password
  const finalPassword = password.trim();

  let rulesString = `/${playerIndex}/${goalieIndex}/${bestOfIndex}/${playToIndex}/${winByIndex}/${hardWinIndex}/${suddenDeathIndex}/${tieIndex}`;
  if (aiDiffIndex !== null) {
    rulesString += `/${aiDiffIndex}`;
    if (isHybrid) {
      rulesString += `/1`;
    }
  } else if (isHybrid) {
    rulesString += `/1/1`;
  }

  // Full tournament code with password prepended if set
  const fullCode = finalPassword ? `${finalPassword}${rulesString}` : rulesString;

  const modeName = PLAYERS_DISP[playerIndex] || `Size Index ${playerIndex}`;
  const goalieRule = GOALIE_DISP[goalieIndex] || `Goalie ${goalieIndex}`;
  const bestOf = `Best of ${bestOfIndex}`;
  const softWin = `Play to ${playToIndex} (Win by ${winByIndex})`;
  const hardWin = hardWinIndex === 9999 ? 'Disabled (9999)' : `Cap at ${hardWinIndex} pts`;
  const suddenDeath = suddenDeathIndex === 9999 ? 'Disabled' : `${suddenDeathIndex}:00 mins`;
  const tie = tieIndex === 9999 ? 'Disabled' : `${tieIndex}:00 mins`;
  const aiDiff = aiDiffIndex !== null ? (AI_DIFF_DISP[aiDiffIndex] || `Tier ${aiDiffIndex}`) : 'Default';
  const isCoopAi = playerIndex >= 9 && playerIndex <= 12;

  return {
    valid: true,
    code: fullCode,
    rulesCode: rulesString,
    password: finalPassword,
    parsed: {
      password: finalPassword,
      playerIndex,
      goalieIndex,
      bestOfIndex,
      playToIndex,
      winByIndex,
      hardWinIndex,
      suddenDeathIndex,
      tieIndex,
      aiDiffIndex,
      isHybrid,
      isCoopAi,
      modeName,
      goalieRule,
      bestOf,
      softWin,
      hardWin,
      suddenDeath,
      tie,
      aiDiff
    }
  };
}

export function openTournamentModal() {
  const modal = document.getElementById('tournament-modal');
  const modeOverlay = document.getElementById('mode-overlay');
  if (!modal) return;

  const savedCode = sessionStorage.getItem('lastCustomTournamentCode') || DEFAULT_TOURNAMENT_CODE;

  const input = document.getElementById('tournament-code-input');
  if (input) {
    input.value = savedCode;
  }

  updateLiveBreakdown();

  modal.style.display = 'flex';
  if (modeOverlay) {
    modeOverlay.style.pointerEvents = 'none';
  }
}

export function closeTournamentModal() {
  const modal = document.getElementById('tournament-modal');
  const modeOverlay = document.getElementById('mode-overlay');
  if (modal) {
    modal.style.display = 'none';
  }
  if (modeOverlay) {
    modeOverlay.style.pointerEvents = 'auto';
  }
  const errorEl = document.getElementById('tournament-modal-error');
  if (errorEl) {
    errorEl.style.display = 'none';
    errorEl.textContent = '';
  }
}

function updateLiveBreakdown() {
  const input = document.getElementById('tournament-code-input');
  const breakdownEl = document.getElementById('tournament-breakdown-card');
  const errorEl = document.getElementById('tournament-modal-error');
  const queueBtn = document.getElementById('tournament-queue-btn');

  if (!input || !breakdownEl) return;

  const rawCode = input.value.trim();
  const res = parseTournamentCode(rawCode);

  if (!res.valid) {
    breakdownEl.innerHTML = `
      <div style="color: #f87171; display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 16px;">⚠️</span>
        <span>${res.error}</span>
      </div>
    `;
    if (queueBtn) queueBtn.disabled = true;
    return;
  }

  if (queueBtn) queueBtn.disabled = false;
  if (errorEl) errorEl.style.display = 'none';

  const p = res.parsed;
  const goalieBadgeColor = p.goalieIndex === 0 ? '#2ed573' : (p.goalieIndex === 1 ? '#f87171' : '#c084fc');
  const hybridBadge = p.isHybrid
    ? `<span style="background: rgba(46, 213, 115, 0.2); color: #2ed573; border: 1px solid rgba(46, 213, 115, 0.4); padding: 1px 6px; border-radius: 4px; font-size: 10px; font-weight: 700;">HYBRID BOT ACTIVE</span>`
    : '';

  const lobbyBadge = p.password
    ? `<span style="background: rgba(255, 159, 28, 0.18); color: #ff9f1c; border: 1px solid rgba(255, 159, 28, 0.4); padding: 2px 8px; border-radius: 6px; font-size: 11px; font-weight: 700;">🔒 Private: "${p.password}"</span>`
    : `<span style="background: rgba(46, 213, 115, 0.12); color: #2ed573; border: 1px solid rgba(46, 213, 115, 0.3); padding: 2px 8px; border-radius: 6px; font-size: 11px; font-weight: 600;">🌐 Public Match</span>`;

  breakdownEl.innerHTML = `
    <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(255,255,255,0.08); padding-bottom: 8px; margin-bottom: 8px;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 16px; font-weight: 800; color: #ff9f1c;">${p.modeName}</span>
        ${hybridBadge}
      </div>
      <div style="display: flex; align-items: center; gap: 6px;">
        ${lobbyBadge}
        <span style="font-size: 11px; padding: 2px 8px; border-radius: 6px; background: rgba(0,0,0,0.4); border: 1px solid ${goalieBadgeColor}; color: ${goalieBadgeColor}; font-weight: 600;">
          ${p.goalieRule}
        </span>
      </div>
    </div>
    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 6px 16px; font-size: 11px;">
      <div><span style="color: #94a3b8;">Series:</span> <strong style="color: #ffffff;">${p.bestOf}</strong></div>
      <div><span style="color: #94a3b8;">Soft Goal:</span> <strong style="color: #38bdf8;">${p.softWin}</strong></div>
      <div><span style="color: #94a3b8;">Hard Cap:</span> <strong style="color: #ffffff;">${p.hardWin}</strong></div>
      <div><span style="color: #94a3b8;">Sudden Death:</span> <strong style="color: #fbbf24;">${p.suddenDeath}</strong></div>
      <div><span style="color: #94a3b8;">Draw Time:</span> <strong style="color: #f87171;">${p.tie}</strong></div>
      <div><span style="color: #94a3b8;">AI Difficulty:</span> <strong style="color: #a7f3d0;">${p.aiDiff}</strong></div>
    </div>
    <div style="margin-top: 8px; padding-top: 6px; border-top: 1px dashed rgba(255,255,255,0.08); font-family: monospace; font-size: 11px; color: #94a3b8; word-break: break-all;">
      <span style="color: #64748b;">Queue String:</span> <strong style="color: #cbd5e1;">${res.code}</strong>
    </div>
  `;
}

export function initTournamentModal(queueCallback) {
  onQueueCustomMatchCallback = queueCallback;

  const openBtn = document.getElementById('tournament-code-btn');
  if (openBtn) {
    openBtn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      openTournamentModal();
    });
  }

  const closeBtn = document.getElementById('tournament-modal-close-btn');
  if (closeBtn) {
    closeBtn.addEventListener('click', () => closeTournamentModal());
  }

  const cancelBtn = document.getElementById('tournament-modal-cancel-btn');
  if (cancelBtn) {
    cancelBtn.addEventListener('click', () => closeTournamentModal());
  }

  const input = document.getElementById('tournament-code-input');
  if (input) {
    input.addEventListener('input', () => {
      updateLiveBreakdown();
    });
  }

  const resetBtn = document.getElementById('tournament-code-reset-btn');
  if (resetBtn) {
    resetBtn.addEventListener('click', () => {
      if (input) {
        input.value = DEFAULT_TOURNAMENT_CODE;
      }
      updateLiveBreakdown();
    });
  }

  // Queue Custom Match click
  const queueBtn = document.getElementById('tournament-queue-btn');
  if (queueBtn) {
    queueBtn.addEventListener('click', async () => {
      const errorEl = document.getElementById('tournament-modal-error');
      const rawCode = input ? input.value.trim() : '';
      const res = parseTournamentCode(rawCode);

      if (!res.valid) {
        if (errorEl) {
          errorEl.textContent = res.error;
          errorEl.style.display = 'block';
        }
        return;
      }

      // Check Goalie restriction
      const classSelect = document.getElementById('class-select');
      const selectedClass = classSelect ? classSelect.value : 'WARRIOR';
      if (selectedClass === 'GOALIE') {
        if (res.parsed.goalieIndex === 1) {
          if (errorEl) {
            errorEl.textContent = 'Error: Cannot queue as Goalie when Goalies are disabled in this match mode.';
            errorEl.style.display = 'block';
          }
          return;
        }
        if (res.parsed.playerIndex === 4) {
          if (errorEl) {
            errorEl.textContent = 'Error: Cannot queue as Goalie for 1v1 Scrimmages.';
            errorEl.style.display = 'block';
          }
          return;
        }
      }

      sessionStorage.setItem('lastCustomTournamentCode', res.code);

      const fillAiCheckbox = document.getElementById('tournament-fill-ai');
      const aiDiffSelect = document.getElementById('tournament-ai-difficulty');
      const fillAi = fillAiCheckbox ? fillAiCheckbox.checked : false;
      const aiDiff = aiDiffSelect ? parseInt(aiDiffSelect.value, 10) : (res.parsed.aiDiffIndex !== null ? res.parsed.aiDiffIndex : 1);

      if (onQueueCustomMatchCallback) {
        try {
          await onQueueCustomMatchCallback({
            tournamentCode: res.code,
            rulesCode: res.rulesCode,
            password: res.password,
            parsed: res.parsed,
            fillAi,
            aiDiff,
            selectedClass
          });
          closeTournamentModal();
        } catch (err) {
          if (errorEl) {
            errorEl.textContent = err.message || 'Failed to queue for match.';
            errorEl.style.display = 'block';
          }
        }
      }
    });
  }

  // Close on Escape key
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      const modal = document.getElementById('tournament-modal');
      if (modal && modal.style.display !== 'none') {
        closeTournamentModal();
      }
    }
  });
}

