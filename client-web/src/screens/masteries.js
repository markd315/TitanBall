import { gameState } from '../state.js';

const STORAGE_KEY = 'titanMasteryPages';

const MASTERY_KEYS = [
  { key: 'health', name: 'Health', desc: 'Increases max health (+8% per point)' },
  { key: 'shot', name: 'Shooting', desc: 'Increases throw power (+4% per point)' },
  { key: 'damage', name: 'Damage', desc: 'Increases ability damage (+10% per point)' },
  { key: 'speed', name: 'Speed', desc: 'Increases movement speed (+3% per point)' },
  { key: 'cooldowns', name: 'Cooldowns', desc: 'Reduces ability cooldowns (+10% CD speed per point)' },
  { key: 'effectDuration', name: 'Effect Duration', desc: 'Increases status effect durations (+15% per point)' },
  { key: 'stealRadius', name: 'Steal Range', desc: 'Increases ball stealing radius (+1px per point)' },
  { key: 'abilityRange', name: 'Ability Range', desc: 'Increases ability casting range (+4% per point)' },
  { key: 'abilityLag', name: 'Cast Speed', desc: 'Reduces ability casting lag (+20% cast speed per point)' },
  { key: 'painReduction', name: 'Pain Reduction', desc: 'Reduces damage taken from enemy goal zones (+25% per point)' },
  { key: 'boost', name: 'Boost', desc: 'Increases boost regen and capacity (+35% per point)' }
];

let currentEditingTitan = 'WARRIOR';
let editingPageIndex = 0;
let editingPages = [];
let localMasteries = {};

export function getSelectedTitan() {
  const fromState = gameState?.controlsHeld?.classSelection;
  const fromSession = sessionStorage.getItem('classSelection');
  return (fromState || fromSession || 'WARRIOR').toUpperCase();
}

function getDefaultMasteries() {
  const res = {};
  MASTERY_KEYS.forEach((m, idx) => {
    res[m.key] = idx < 10 ? 1 : 0;
  });
  return res;
}

export function validateMasteries(allocations) {
  if (!allocations || typeof allocations !== 'object') return false;
  let sum = 0;
  for (const m of MASTERY_KEYS) {
    const val = Number(allocations[m.key]);
    if (isNaN(val) || val < 0 || val > 3) return false;
    sum += val;
  }
  return sum === 10;
}

export function sanitizeMasteries(allocations) {
  const result = getDefaultMasteries();
  if (!allocations || typeof allocations !== 'object') return result;

  MASTERY_KEYS.forEach((m, idx) => {
    const val = Number(allocations[m.key]);
    result[m.key] = (!isNaN(val) && val >= 0 && val <= 3) ? Math.floor(val) : (idx < 10 ? 1 : 0);
  });

  let sum = Object.values(result).reduce((a, b) => a + b, 0);

  if (sum > 10) {
    const reverseKeys = [...MASTERY_KEYS].reverse();
    for (const m of reverseKeys) {
      while (sum > 10 && result[m.key] > 0) {
        result[m.key]--;
        sum--;
      }
      if (sum === 10) break;
    }
  }

  if (sum < 10) {
    for (const m of MASTERY_KEYS) {
      while (sum < 10 && result[m.key] < 3) {
        result[m.key]++;
        sum++;
      }
      if (sum === 10) break;
    }
  }

  return result;
}

function getDefaultTitanData() {
  return {
    activePageIndex: 0,
    pages: [
      { name: 'Page 1', allocations: getDefaultMasteries() },
      { name: 'Page 2', allocations: getDefaultMasteries() },
      { name: 'Page 3', allocations: getDefaultMasteries() }
    ]
  };
}

function loadAllTitanMasteriesData() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  return {};
}

function saveAllTitanMasteriesData(allData) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(allData));
  } catch (e) {
    console.error('Failed to save titan masteries to localStorage:', e);
  }
}

export function getTitanMasteryData(titan) {
  const key = (titan || 'WARRIOR').toUpperCase();
  const allData = loadAllTitanMasteriesData();
  let data = allData[key];
  if (!data || !Array.isArray(data.pages) || data.pages.length === 0) {
    data = getDefaultTitanData();
    try {
      const saved = sessionStorage.getItem('titanMasteries');
      if (saved) {
        const legacy = JSON.parse(saved);
        data.pages[0].allocations = sanitizeMasteries(legacy);
      }
    } catch (e) {}
    allData[key] = data;
    saveAllTitanMasteriesData(allData);
  } else {
    while (data.pages.length < 3) {
      data.pages.push({ name: `Page ${data.pages.length + 1}`, allocations: getDefaultMasteries() });
    }
    if (data.pages.length > 3) {
      data.pages = data.pages.slice(0, 3);
    }
    let dataModified = false;
    data.pages.forEach((p, idx) => {
      if (!p.name) { p.name = `Page ${idx + 1}`; dataModified = true; }
      const sanitized = sanitizeMasteries(p.allocations);
      if (JSON.stringify(p.allocations) !== JSON.stringify(sanitized)) {
        p.allocations = sanitized;
        dataModified = true;
      }
    });
    if (typeof data.activePageIndex !== 'number' || data.activePageIndex < 0 || data.activePageIndex >= 3) {
      data.activePageIndex = 0;
      dataModified = true;
    }
    if (dataModified) {
      allData[key] = data;
      saveAllTitanMasteriesData(allData);
    }
  }
  return data;
}

export function loadMasteriesForTitan(titan) {
  const tKey = (titan || getSelectedTitan()).toUpperCase();
  const titanData = getTitanMasteryData(tKey);
  const rawAllocations = titanData.pages[titanData.activePageIndex]?.allocations || getDefaultMasteries();
  const activeAllocations = sanitizeMasteries(rawAllocations);
  gameState.controlsHeld.masteries = { ...activeAllocations };
  sessionStorage.setItem('titanMasteries', JSON.stringify(activeAllocations));
  window.dispatchEvent(new CustomEvent('masteriesUpdated', { detail: activeAllocations }));
  return activeAllocations;
}

export function initMasteries() {
  // Initially load masteries for selected titan
  loadMasteriesForTitan(getSelectedTitan());

  const modalBtn = document.getElementById('masteries-modal-btn');
  const modal = document.getElementById('masteries-modal');
  const modeOverlay = document.getElementById('mode-overlay');

  if (modalBtn && modal) {
    modalBtn.addEventListener('click', () => {
      currentEditingTitan = getSelectedTitan();
      const titanData = getTitanMasteryData(currentEditingTitan);
      editingPageIndex = titanData.activePageIndex;
      editingPages = JSON.parse(JSON.stringify(titanData.pages));
      localMasteries = { ...editingPages[editingPageIndex].allocations };

      renderFullMasteriesUI();
      modal.style.display = 'flex';
      if (modeOverlay) modeOverlay.style.pointerEvents = 'none';
    });
  }

  const cancelBtn = document.getElementById('masteries-cancel-btn');
  if (cancelBtn && modal) {
    cancelBtn.addEventListener('click', () => {
      modal.style.display = 'none';
      if (modeOverlay) modeOverlay.style.pointerEvents = 'auto';
    });
  }

  const nameInput = document.getElementById('mastery-page-name-input');
  if (nameInput) {
    nameInput.addEventListener('input', (e) => {
      const val = e.target.value;
      if (editingPages[editingPageIndex]) {
        editingPages[editingPageIndex].name = val;
      }
      renderPageTabs(false);
    });

    nameInput.addEventListener('blur', () => {
      if (editingPages[editingPageIndex]) {
        if (!editingPages[editingPageIndex].name.trim()) {
          editingPages[editingPageIndex].name = `Page ${editingPageIndex + 1}`;
          nameInput.value = editingPages[editingPageIndex].name;
        }
        renderPageTabs(false);
      }
    });
  }

  const resetBtn = document.getElementById('masteries-reset-btn');
  if (resetBtn) {
    resetBtn.addEventListener('click', () => {
      localMasteries = getDefaultMasteries();
      if (editingPages[editingPageIndex]) {
        editingPages[editingPageIndex].allocations = { ...localMasteries };
      }
      renderMasteriesList();
    });
  }

  const exportBtn = document.getElementById('masteries-export-btn');
  if (exportBtn) {
    exportBtn.addEventListener('click', () => {
      const text = JSON.stringify(localMasteries, null, 2);
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(() => {
          _flashBtn(exportBtn, '✓ Copied!');
        }).catch(() => {
          window.prompt('Copy masteries JSON:', text);
        });
      } else {
        window.prompt('Copy masteries JSON:', text);
      }
    });
  }

  const pasteBtn = document.getElementById('masteries-paste-btn');
  if (pasteBtn) {
    pasteBtn.addEventListener('click', () => {
      const errEl = document.getElementById('masteries-error');
      if (errEl) errEl.style.display = 'none';

      const applyText = (text) => {
        if (!text) return;
        const parsed = _parseMasteriesText(text);
        if (!parsed) {
          if (errEl) {
            errEl.textContent = 'Invalid masteries format! Must total 10 points (0 to 3 per key).';
            errEl.style.display = 'block';
          }
          _flashBtn(pasteBtn, 'Invalid!');
          return;
        }
        localMasteries = parsed;
        if (editingPages[editingPageIndex]) {
          editingPages[editingPageIndex].allocations = { ...localMasteries };
        }
        renderMasteriesList();
        _flashBtn(pasteBtn, '✓ Loaded!');
      };

      if (navigator.clipboard && navigator.clipboard.readText) {
        navigator.clipboard.readText().then(text => {
          applyText(text);
        }).catch(() => {
          const text = window.prompt('Paste masteries JSON or key=val string:');
          applyText(text);
        });
      } else {
        const text = window.prompt('Paste masteries JSON or key=val string:');
        applyText(text);
      }
    });
  }

  const saveBtn = document.getElementById('masteries-save-btn');
  if (saveBtn && modal) {
    saveBtn.addEventListener('click', () => {
      const errEl = document.getElementById('masteries-error');

      // Commit current page state
      if (nameInput) {
        const val = nameInput.value.trim();
        editingPages[editingPageIndex].name = val || `Page ${editingPageIndex + 1}`;
      }
      editingPages[editingPageIndex].allocations = { ...localMasteries };

      // Validation check: sum must equal 10 points
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

      // Save to localStorage
      const allData = loadAllTitanMasteriesData();
      allData[currentEditingTitan] = {
        activePageIndex: editingPageIndex,
        pages: editingPages
      };
      saveAllTitanMasteriesData(allData);

      // Save to game state and sessionStorage
      gameState.controlsHeld.masteries = { ...localMasteries };
      sessionStorage.setItem('titanMasteries', JSON.stringify(localMasteries));

      // Dispatch event to live-update stats
      window.dispatchEvent(new CustomEvent('masteriesUpdated', { detail: localMasteries }));

      modal.style.display = 'none';
      if (modeOverlay) modeOverlay.style.pointerEvents = 'auto';
    });
  }
}

function renderFullMasteriesUI() {
  const titleEl = document.getElementById('masteries-modal-title');
  if (titleEl) {
    const formattedTitan = currentEditingTitan.charAt(0) + currentEditingTitan.slice(1).toLowerCase();
    titleEl.textContent = `${formattedTitan} Masteries`;
  }

  renderPageTabs(true);
  renderMasteriesList();
}

function renderPageTabs(updateInputValue = false) {
  const container = document.getElementById('masteries-page-tabs');
  const nameInput = document.getElementById('mastery-page-name-input');
  if (!container) return;

  if (updateInputValue && nameInput && editingPages[editingPageIndex]) {
    nameInput.value = editingPages[editingPageIndex].name ?? `Page ${editingPageIndex + 1}`;
  }

  container.innerHTML = '';
  for (let i = 0; i < 3; i++) {
    const page = editingPages[i] || { name: `Page ${i + 1}`, allocations: getDefaultMasteries() };
    const isActive = i === editingPageIndex;

    const btn = document.createElement('button');
    btn.className = `btn btn-secondary ${isActive ? 'active' : ''}`;
    btn.style.flex = '1';
    btn.style.padding = '6px 12px';
    btn.style.fontSize = '13px';
    btn.style.margin = '0';
    btn.style.borderRadius = '8px';

    if (isActive) {
      btn.style.borderColor = '#38bdf8';
      btn.style.background = 'rgba(56, 189, 248, 0.25)';
      btn.style.color = '#ffffff';
      btn.style.fontWeight = 'bold';
      btn.style.boxShadow = '0 0 10px rgba(56, 189, 248, 0.3)';
    } else {
      btn.style.borderColor = 'rgba(255, 255, 255, 0.15)';
      btn.style.background = 'rgba(0, 0, 0, 0.3)';
      btn.style.color = '#94a3b8';
    }

    const displayName = (page.name && page.name.trim()) ? page.name.trim() : `Page ${i + 1}`;
    btn.textContent = displayName;

    btn.addEventListener('click', () => {
      if (editingPageIndex === i) return;
      // Commit current page name & allocations
      if (nameInput) {
        const val = nameInput.value.trim();
        editingPages[editingPageIndex].name = val || `Page ${editingPageIndex + 1}`;
      }
      editingPages[editingPageIndex].allocations = { ...localMasteries };

      // Switch to clicked page
      editingPageIndex = i;
      localMasteries = { ...editingPages[editingPageIndex].allocations };
      renderFullMasteriesUI();
    });

    container.appendChild(btn);
  }
}

function renderMasteriesList() {
  const container = document.getElementById('masteries-list');
  const pointsLeftEl = document.getElementById('masteries-points-left');
  if (!container) return;

  container.innerHTML = '';

  const totalAllocated = Object.values(localMasteries).reduce((a, b) => a + b, 0);
  const remaining = 10 - totalAllocated;
  if (pointsLeftEl) {
    pointsLeftEl.textContent = remaining;
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

    const minusBtn = row.querySelector('.minus');
    const plusBtn = row.querySelector('.plus');

    minusBtn.disabled = val <= 0;
    plusBtn.disabled = val >= 3 || remaining <= 0;

    minusBtn.addEventListener('click', () => {
      localMasteries[m.key] = Math.max(0, val - 1);
      if (editingPages[editingPageIndex]) {
        editingPages[editingPageIndex].allocations = { ...localMasteries };
      }
      renderMasteriesList();
    });

    plusBtn.addEventListener('click', () => {
      localMasteries[m.key] = Math.min(3, val + 1);
      if (editingPages[editingPageIndex]) {
        editingPages[editingPageIndex].allocations = { ...localMasteries };
      }
      renderMasteriesList();
    });

    container.appendChild(row);
  });
}

function _parseMasteriesText(text) {
  if (!text) return null;
  const result = getDefaultMasteries();
  try {
    const parsed = JSON.parse(text.trim());
    if (typeof parsed === 'object' && parsed !== null) {
      let sum = 0;
      for (const m of MASTERY_KEYS) {
        const val = Number(parsed[m.key] !== undefined ? parsed[m.key] : (m.key === 'boost' ? 0 : 1));
        if (isNaN(val) || val < 0 || val > 3) return null;
        result[m.key] = val;
        sum += val;
      }
      return sum === 10 ? result : null;
    }
  } catch (e) {}

  // Fallback: key=value or key:value lines/tokens
  try {
    const lines = text.split(/[\n,;]/);
    const temp = {};
    for (const raw of lines) {
      const line = raw.trim();
      if (!line) continue;
      const parts = line.split(/[:=]/);
      if (parts.length === 2) {
        const k = parts[0].trim();
        const v = Number(parts[1].trim());
        if (!isNaN(v)) temp[k] = v;
      }
    }
    let sum = 0;
    for (const m of MASTERY_KEYS) {
      const val = temp[m.key] !== undefined ? temp[m.key] : (m.key === 'boost' ? 0 : 1);
      if (isNaN(val) || val < 0 || val > 3) return null;
      result[m.key] = val;
      sum += val;
    }
    return sum === 10 ? result : null;
  } catch (e) {}

  return null;
}

function _flashBtn(btn, msg, durationMs = 1800) {
  const original = btn.textContent;
  btn.textContent = msg;
  btn.disabled = true;
  setTimeout(() => {
    btn.textContent = original;
    btn.disabled = false;
  }, durationMs);
}
