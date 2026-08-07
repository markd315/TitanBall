import { gameState } from '../state.js';

const MASTERY_KEYS = [
  { key: 'health', name: 'Health', desc: 'Increases max health (+4% per point)' },
  { key: 'shot', name: 'Shooting', desc: 'Increases throw power (+4% per point)' },
  { key: 'damage', name: 'Damage', desc: 'Increases ability damage (+10% per point)' },
  { key: 'speed', name: 'Speed', desc: 'Increases movement speed (+4% per point)' },
  { key: 'cooldowns', name: 'Cooldowns', desc: 'Reduces ability cooldowns (+10% CD speed per point)' },
  { key: 'effectDuration', name: 'Effect Duration', desc: 'Increases status effect durations (+15% per point)' },
  { key: 'stealRadius', name: 'Steal Range', desc: 'Increases ball stealing radius (+4% per point)' },
  { key: 'abilityRange', name: 'Ability Range', desc: 'Increases ability casting range (+4% per point)' },
  { key: 'abilityLag', name: 'Cast Speed', desc: 'Reduces ability casting lag (+20% cast speed per point)' },
  { key: 'painReduction', name: 'Pain Reduction', desc: 'Reduces damage taken from zone/enemies (+25% per point)' }
];

let localMasteries = {};

export function initMasteries() {
  // Load saved masteries or set defaults (all 1 by default, sum to 10 points)
  const saved = localStorage.getItem('titanMasteries');
  if (saved) {
    try {
      localMasteries = JSON.parse(saved);
      // Ensure all keys are populated
      MASTERY_KEYS.forEach(m => {
        if (localMasteries[m.key] === undefined) localMasteries[m.key] = 1;
      });
    } catch (e) {
      resetToDefault();
    }
  } else {
    resetToDefault();
  }

  // Update game state controls to include loaded masteries
  gameState.controlsHeld.masteries = { ...localMasteries };

  // Set up listeners for the modal trigger button
  const modalBtn = document.getElementById('masteries-modal-btn');
  const modal = document.getElementById('masteries-modal');
  const modeOverlay = document.getElementById('mode-overlay');
  
  if (modalBtn && modal) {
    modalBtn.addEventListener('click', () => {
      // Create a fresh local copy of current saved masteries for editing
      localMasteries = { ...gameState.controlsHeld.masteries };
      renderMasteriesUI();
      modal.style.display = 'flex';
      if (modeOverlay) modeOverlay.style.pointerEvents = 'none'; // Lock background interaction
    });
  }

  const cancelBtn = document.getElementById('masteries-cancel-btn');
  if (cancelBtn && modal) {
    cancelBtn.addEventListener('click', () => {
      modal.style.display = 'none';
      if (modeOverlay) modeOverlay.style.pointerEvents = 'auto';
    });
  }

  const saveBtn = document.getElementById('masteries-save-btn');
  if (saveBtn && modal) {
    saveBtn.addEventListener('click', () => {
      const errEl = document.getElementById('masteries-error');
      
      // Validation check: sum must equal exactly 10 points
      const totalPoints = Object.values(localMasteries).reduce((a, b) => a + b, 0);
      if (totalPoints !== 10) {
        if (errEl) {
          errEl.textContent = `Invalid points! You must allocate exactly 10 points. (Currently: ${totalPoints})`;
          errEl.style.display = 'block';
        }
        return;
      }
      
      // Validation check: each must be between 0 and 3
      let rangeValid = true;
      for (const key in localMasteries) {
        const val = localMasteries[key];
        if (val < 0 || val > 3) {
          rangeValid = false;
          break;
        }
      }
      if (!rangeValid) {
        if (errEl) {
          errEl.textContent = `Each mastery must have between 0 and 3 points allocated.`;
          errEl.style.display = 'block';
        }
        return;
      }

      if (errEl) errEl.style.display = 'none';

      // Save to game state and localStorage
      gameState.controlsHeld.masteries = { ...localMasteries };
      localStorage.setItem('titanMasteries', JSON.stringify(localMasteries));
      
      modal.style.display = 'none';
      if (modeOverlay) modeOverlay.style.pointerEvents = 'auto';
    });
  }
}

function resetToDefault() {
  localMasteries = {};
  MASTERY_KEYS.forEach(m => {
    localMasteries[m.key] = 1;
  });
}

function renderMasteriesUI() {
  const container = document.getElementById('masteries-list');
  const pointsLeftEl = document.getElementById('masteries-points-left');
  if (!container) return;

  container.innerHTML = '';
  
  // Calculate allocated points
  const totalAllocated = Object.values(localMasteries).reduce((a, b) => a + b, 0);
  const remaining = 10 - totalAllocated;
  if (pointsLeftEl) {
    pointsLeftEl.textContent = remaining;
    // Glow green if 0, yellow otherwise
    pointsLeftEl.style.color = remaining === 0 ? '#2ed573' : '#ff9f1c';
  }

  MASTERY_KEYS.forEach(m => {
    const val = localMasteries[m.key] || 0;
    
    const row = document.createElement('div');
    row.className = 'mastery-row';
    
    row.innerHTML = `
      <div class="mastery-info">
        <span class="mastery-name">${m.name}</span>
        <span class="mastery-desc">${m.desc}</span>
      </div>
      <div class="mastery-controls">
        <button class="mastery-btn minus" data-key="${m.key}">-</button>
        <span class="mastery-val">${val}</span>
        <button class="mastery-btn plus" data-key="${m.key}">+</button>
      </div>
    `;
    
    // Wire button events
    const minusBtn = row.querySelector('.minus');
    const plusBtn = row.querySelector('.plus');
    
    minusBtn.disabled = val <= 0;
    plusBtn.disabled = val >= 3 || remaining <= 0;
    
    minusBtn.addEventListener('click', () => {
      localMasteries[m.key] = Math.max(0, val - 1);
      renderMasteriesUI();
    });
    
    plusBtn.addEventListener('click', () => {
      localMasteries[m.key] = Math.min(3, val + 1);
      renderMasteriesUI();
    });
    
    container.appendChild(row);
  });
}
