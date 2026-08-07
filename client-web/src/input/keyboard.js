import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';

export let currentConfig = {};
export const actionMap = {
  'UP': 'UP',
  'DOWN': 'DOWN',
  'LEFT': 'LEFT',
  'RIGHT': 'RIGHT',
  'E': 'E',
  'R': 'R',
  'CAM': 'CAM',
  'STEAL': 'STEAL',
  'SWITCH': 'SWITCH',
  'BOOST': 'BOOST',
  'BOOST_LOCK': 'BOOST_LOCK',
  'LOB': 'lobBtn',
  'SHOT': 'shotBtn',
  'MV_CLICK': 'MV_CLICK',
  'MV_BALL': 'MV_BALL'
};

export async function setControlPreset(preset) {
  try {
    const url = preset === 'rts' ? 'res/ctrls_example_rts.json' : 'res/config.json';
    const response = await fetch(url);
    const data = await response.json();
    currentConfig = data;
    localStorage.setItem('controlPreset', preset);
    console.log("Loaded control layout configuration:", preset, currentConfig);
  } catch (e) {
    console.error("Failed to load control configuration:", e);
  }
}

export async function initControlConfig() {
  const saved = localStorage.getItem('controlPreset') || 'default';
  await setControlPreset(saved);
  // Set UI dropdown value if present
  const select = document.getElementById('controls-select');
  if (select) {
    select.value = saved;
  }
}

function getActionForKey(e) {
  // Check key code (e.g. "32", "37" etc.)
  const codeStr = String(e.keyCode);
  if (currentConfig[codeStr]) {
    return currentConfig[codeStr];
  }
  
  // Check uppercase key (e.g. "Q", "W")
  const keyStr = e.key.toUpperCase();
  if (currentConfig[keyStr]) {
    return currentConfig[keyStr];
  }
  
  // Fallbacks for default movement keys if NOT overridden in currentConfig
  if (!currentConfig['W'] && !currentConfig['KeyW']) {
    if (e.code === 'KeyW' || e.code === 'ArrowUp') return 'UP';
  }
  if (!currentConfig['S'] && !currentConfig['KeyS']) {
    if (e.code === 'KeyS' || e.code === 'ArrowDown') return 'DOWN';
  }
  if (!currentConfig['A'] && !currentConfig['KeyA']) {
    if (e.code === 'KeyA' || e.code === 'ArrowLeft') return 'LEFT';
  }
  if (!currentConfig['D'] && !currentConfig['KeyD']) {
    if (e.code === 'KeyD' || e.code === 'ArrowRight') return 'RIGHT';
  }
  
  // Space bar maps to CAM for lock toggle if not mapped
  if (e.code === 'Space' && !currentConfig['32']) {
    return 'CAM';
  }
  
  return null;
}

export function initKeyboard() {
  // Load config first
  initControlConfig();

  window.addEventListener('keydown', (e) => {
    // If typing in an input field, do not capture/prevent default controls
    if (document.activeElement && document.activeElement.tagName === 'INPUT') {
      return;
    }

    // Menu navigation on Space or Enter
    if (e.code === 'Space' || e.code === 'Enter') {
      if (gameState.phase === GamePhase.CREDITS) {
        gameState.phase = GamePhase.CONTROLS;
        e.preventDefault();
        return;
      } else if (gameState.phase === GamePhase.CONTROLS) {
        gameState.phase = GamePhase.SHOW_GAME_MODES;
        e.preventDefault();
        return;
      } else if (gameState.phase === GamePhase.ENDED) {
        window.location.reload();
        e.preventDefault();
        return;
      }
    }

    const action = getActionForKey(e);
    if (action && actionMap[action]) {
      const field = actionMap[action];
      gameState.controlsHeld[field] = true;
      
      if (field === 'CAM') {
        // Toggle camera lock behavior
        gameState.camFollow = !gameState.camFollow;
      }
      e.preventDefault();
    }
  });

  window.addEventListener('keyup', (e) => {
    // If typing in an input field, do not capture/prevent default controls
    if (document.activeElement && document.activeElement.tagName === 'INPUT') {
      return;
    }

    const action = getActionForKey(e);
    if (action && actionMap[action]) {
      const field = actionMap[action];
      gameState.controlsHeld[field] = false;
      e.preventDefault();
    }
  });
}