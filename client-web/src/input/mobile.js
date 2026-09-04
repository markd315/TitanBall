import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';
import { getDefaultPreset, executeNextBuildOrder } from './keyboard.js';
import { handleUIClick } from './mouse.js';
import { requestFullscreen, isFullscreenActive } from '../util/fullscreen.js';

let joystickBase = null;
let joystickStick = null;
let container = null;
let btnAbility1 = null;
let btnAbility2 = null;
let btnPossession = null;
let btnUpgrade = null;

// Boost Switch element
let boostSwitchContainer = null;
let boostSwitch = null;

// New elements for Double Joystick Mode
let aimJoystickZone = null;
let aimJoystickBase = null;
let aimJoystickStick = null;
let btnShot = null;
let mobileButtonsZone = null;

let isDragging = false;
let startX = 0;
let startY = 0;
let maxRadius = 50; // max displacement in pixels (will be updated dynamically)
const deadzone = 0.3;

// Aim Joystick tracking variables
let isAimDragging = false;
let aimStartX = 0;
let aimStartY = 0;
let aimMaxRadius = 50;
let lastAimNormX = 1;
let lastAimNormY = 0;

const BASE_ABILITY_RANGES = {
  E: {
    MAGE: 400,
    BUILDER: 200,
    SUPPORT: 130,
    RANGER: 320,
    WARRIOR: 200,
    ARTISAN: 140,
    GRENADIER: 260,
    MARKSMAN: 250,
    HOUNDMASTER: 9999,
    CAPTAIN: 200,
    SPIDER: 280,
    DASHER: 0,
    GOLEM: 0,
    STEALTH: 0,
    GOALIE: 0
  },
  R: {
    MAGE: 250,
    BUILDER: 350,
    SUPPORT: 250,
    RANGER: 120,
    WARRIOR: 140,
    ARTISAN: 200,
    GRENADIER: 180,
    DASHER: 250,
    GOLEM: 90,
    STEALTH: 100,
    CAPTAIN: 250,
    SPIDER: 150,
    MARKSMAN: 0,
    HOUNDMASTER: 0,
    GOALIE: 0
  }
};

export function getAbilityRange(titan, slot) {
  if (!titan) return 300;
  const rangeFactor = titan.rangeFactor || 1.0;
  
  if (titan.rangeIndicators && titan.rangeIndicators.length > 0) {
    for (const ri of titan.rangeIndicators) {
      const color = ri.colorArray || ri.color;
      if (slot === 'E' && color && color[1] > 0.8 && color[0] < 0.2) {
        const rad = ri.radiusX || ri.radius || 0;
        if (rad > 0) return rad * rangeFactor;
      } else if (slot === 'R' && color && color[0] > 0.3 && color[2] > 0.3) {
        const rad = ri.radiusX || ri.radius || 0;
        if (rad > 0) return rad * rangeFactor;
      }
    }
  }

  const type = titan.type;
  if (BASE_ABILITY_RANGES[slot] && BASE_ABILITY_RANGES[slot][type] !== undefined) {
    const fallback = BASE_ABILITY_RANGES[slot][type];
    if (fallback > 0) {
      return fallback * rangeFactor;
    }
  }

  return 300;
}

export function updateDoubleJoyAimPosition(range = 300) {
  const game = gameState.game;
  const t = getControlledTitan(game);
  if (!t) return;

  const playerCanvasX = t.X + 35 - (gameState.camX || 0);
  const playerCanvasY = t.Y + 35 - (gameState.camY || 0);

  if (lastAimNormX === 0 && lastAimNormY === 0) {
    const isAway = t.team === 'AWAY' || t.team === 1;
    lastAimNormX = isAway ? -1 : 1;
    lastAimNormY = 0;
  }

  const effectiveRange = Math.max(10, range);
  gameState.mouseX = playerCanvasX + lastAimNormX * effectiveRange;
  gameState.mouseY = playerCanvasY + lastAimNormY * effectiveRange;
  gameState.controlsHeld.posX = Math.floor(gameState.mouseX);
  gameState.controlsHeld.posY = Math.floor(gameState.mouseY);
}

// Single Joystick Mode touch tracking variables
let activeCanvasTouchId = null;
let hasLobbedCurrentTouch = false;

// Active action of the possession button (LOB or STEAL)
let possessionActiveAction = null;

export function getControlledTitan(game) {
  if (!game) return null;
  if (game.underControl) return game.underControl;
  if (game.clients && game.players) {
    const token = gameState.token || sessionStorage.getItem('accessToken');
    if (token) {
      try {
        const payloadBase64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const decoded = decodeURIComponent(atob(payloadBase64).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''));
        const email = JSON.parse(decoded).sub || '';
        const client = game.clients.find(c => c.email === email);
        if (client && client.selection > 0) {
          return game.players[client.selection - 1] || null;
        }
      } catch (e) {}
    }
  }
  return null;
}

export function setBoostState(enabled) {
  gameState.controlsHeld.BOOST = enabled;
  if (boostSwitch) {
    if (enabled) {
      boostSwitch.classList.add('active');
      boostSwitch.setAttribute('aria-checked', 'true');
    } else {
      boostSwitch.classList.remove('active');
      boostSwitch.setAttribute('aria-checked', 'false');
    }
  }
}

export function toggleBoost() {
  const game = gameState.game;
  const t = getControlledTitan(game);
  if (!gameState.controlsHeld.BOOST) {
    // Attempting to turn ON
    if (t) {
      if (t.type === 'GOALIE' || t.health <= 0 || t.fuel <= 0 || t.fuel < 1.0) {
        return;
      }
      if (t.possession === 1 && t.type !== 'DASHER') {
        return;
      }
    }
    setBoostState(true);
  } else {
    // Turn OFF
    setBoostState(false);
  }
}

export function initMobileControls() {
  joystickBase = document.getElementById('joystick-base');
  joystickStick = document.getElementById('joystick-stick');
  container = document.getElementById('mobile-controls-container');
  btnAbility1 = document.getElementById('btn-ability-1');
  btnAbility2 = document.getElementById('btn-ability-2');
  btnPossession = document.getElementById('btn-possession');
  btnUpgrade = document.getElementById('btn-upgrade');
  boostSwitchContainer = document.getElementById('boost-switch-container');
  boostSwitch = document.getElementById('boost-switch');

  // Double joystick elements
  aimJoystickZone = document.getElementById('aim-joystick-zone');
  aimJoystickBase = document.getElementById('aim-joystick-base');
  aimJoystickStick = document.getElementById('aim-joystick-stick');
  btnShot = document.getElementById('btn-shot');
  mobileButtonsZone = document.getElementById('mobile-buttons-zone');

  const canvas = document.getElementById('gameCanvas');

  if (!joystickBase || !joystickStick || !container || !btnAbility1 || !btnAbility2 || !btnPossession || !btnUpgrade || !canvas ||
      !aimJoystickZone || !aimJoystickBase || !aimJoystickStick || !btnShot || !mobileButtonsZone ||
      !boostSwitchContainer || !boostSwitch) {
    console.warn("Mobile control HTML elements not found.");
    return;
  }

  // Request fullscreen on mobile controls interaction if not already active
  container.addEventListener('touchstart', () => {
    if (!isFullscreenActive()) {
      requestFullscreen();
    }
  }, { passive: true });

  // Boost Switch toggle events
  const handleBoostToggle = (e) => {
    toggleBoost();
    if (e.cancelable) e.preventDefault();
    e.stopPropagation();
  };
  boostSwitch.addEventListener('touchstart', handleBoostToggle, { passive: false });
  boostSwitch.addEventListener('click', handleBoostToggle);

  // Virtual Joystick touch events
  joystickBase.addEventListener('touchstart', handleJoystickStart, { passive: false });
  window.addEventListener('touchmove', handleJoystickMove, { passive: false });
  window.addEventListener('touchend', handleJoystickEnd, { passive: false });

  // Virtual Aim Joystick touch events
  aimJoystickBase.addEventListener('touchstart', handleAimJoystickStart, { passive: false });
  window.addEventListener('touchmove', handleAimJoystickMove, { passive: false });
  window.addEventListener('touchend', handleAimJoystickEnd, { passive: false });

  // Button Ability 1 (labeled "1")
  btnAbility1.addEventListener('touchstart', (e) => {
    const currentPreset = sessionStorage.getItem('controlPreset') || getDefaultPreset();
    const game = gameState.game;
    const t = getControlledTitan(game);
    if (t) {
      if (t.type === 'ARTISAN' && t.possession === 1) {
        const current = gameState.controlsHeld.artisanShot || 'SHOT';
        let next = 'LEFT';
        if (current === 'LEFT') next = 'RIGHT';
        else if (current === 'RIGHT') next = 'SHOT';
        gameState.controlsHeld.artisanShot = next;
      }
      if (currentPreset === 'mobile-double') {
        const abilityRange = getAbilityRange(t, 'E');
        updateDoubleJoyAimPosition(abilityRange);
      }
    }
    gameState.controlsHeld.E = true;
    e.preventDefault();
    e.stopPropagation();
  }, { passive: false });
  btnAbility1.addEventListener('touchend', (e) => {
    gameState.controlsHeld.E = false;
    e.preventDefault();
    e.stopPropagation();
  }, { passive: false });

  // Button Ability 2 (labeled "2")
  btnAbility2.addEventListener('touchstart', (e) => {
    const currentPreset = sessionStorage.getItem('controlPreset') || getDefaultPreset();
    const game = gameState.game;
    const t = getControlledTitan(game);
    if (t && currentPreset === 'mobile-double') {
      const abilityRange = getAbilityRange(t, 'R');
      updateDoubleJoyAimPosition(abilityRange);
    }
    gameState.controlsHeld.R = true;
    e.preventDefault();
    e.stopPropagation();
  }, { passive: false });
  btnAbility2.addEventListener('touchend', (e) => {
    gameState.controlsHeld.R = false;
    e.preventDefault();
    e.stopPropagation();
  }, { passive: false });

  // Button Possession (swaps STEAL / LOB)
  btnPossession.addEventListener('touchstart', (e) => {
    const currentPreset = sessionStorage.getItem('controlPreset') || getDefaultPreset();
    const game = gameState.game;
    const myTitan = getControlledTitan(game);
    const hasPossession = myTitan && myTitan.possession === 1;
    if (hasPossession) {
      if (currentPreset === 'mobile-single' && activeCanvasTouchId !== null) {
        hasLobbedCurrentTouch = true;
      }
      gameState.controlsHeld.lobBtn = true;
      possessionActiveAction = 'LOB';
    } else {
      gameState.controlsHeld.STEAL = true;
      possessionActiveAction = 'STEAL';
    }
    e.preventDefault();
    e.stopPropagation();
  }, { passive: false });
  btnPossession.addEventListener('touchend', (e) => {
    if (possessionActiveAction === 'LOB') {
      gameState.controlsHeld.lobBtn = false;
    } else if (possessionActiveAction === 'STEAL') {
      gameState.controlsHeld.STEAL = false;
    }
    possessionActiveAction = null;
    e.preventDefault();
    e.stopPropagation();
  }, { passive: false });

  // Button Upgrade (sends goalie upgrade hotkey to backend, same as X key)
  const handleUpgradeClick = (e) => {
    const game = gameState.game;
    if (game) {
      executeNextBuildOrder(game);
    }
    if (e.cancelable) e.preventDefault();
    e.stopPropagation();
  };
  btnUpgrade.addEventListener('touchstart', handleUpgradeClick, { passive: false });
  btnUpgrade.addEventListener('click', handleUpgradeClick);

  // Button Shot (only for Double Joystick Mode)
  btnShot.addEventListener('touchstart', (e) => {
    const currentPreset = sessionStorage.getItem('controlPreset') || getDefaultPreset();
    const game = gameState.game;
    const t = getControlledTitan(game);
    if (t && currentPreset === 'mobile-double') {
      updateDoubleJoyAimPosition(300);
    }
    gameState.controlsHeld.shotBtn = true;
    e.preventDefault();
    e.stopPropagation();
  }, { passive: false });
  btnShot.addEventListener('touchend', (e) => {
    gameState.controlsHeld.shotBtn = false;
    e.preventDefault();
    e.stopPropagation();
  }, { passive: false });

  // Canvas screen tap shooting/passing (only used in mobile-single)
  canvas.addEventListener('touchstart', handleCanvasTouchStart, { passive: false });
  canvas.addEventListener('touchmove', handleCanvasTouchMove, { passive: false });
  canvas.addEventListener('touchend', handleCanvasTouchEnd, { passive: false });

  // Double-tap or multi-tap screen on mobile to return to lobby menu when game has ended
  let lastEndedTouchTime = 0;
  window.addEventListener('touchend', (e) => {
    const isGameEnded = gameState.phase === GamePhase.ENDED ||
                        gameState.phase === 'ENDED' ||
                        (gameState.game && (gameState.game.ended || gameState.game.phase === 'ENDED'));
    if (isGameEnded) {
      const now = Date.now();
      if (now - lastEndedTouchTime < 500 && now - lastEndedTouchTime > 30) {
        window.location.reload();
      }
      lastEndedTouchTime = now;
    }
  }, { passive: false });

  window.addEventListener('touchstart', (e) => {
    const isGameEnded = gameState.phase === GamePhase.ENDED ||
                        gameState.phase === 'ENDED' ||
                        (gameState.game && (gameState.game.ended || gameState.game.phase === 'ENDED'));
    if (isGameEnded && e.touches && e.touches.length >= 2) {
      window.location.reload();
    }
  }, { passive: false });
}

function handleJoystickStart(e) {
  const touch = e.touches[0];
  const rect = joystickBase.getBoundingClientRect();
  startX = rect.left + rect.width / 2;
  startY = rect.top + rect.height / 2;
  maxRadius = rect.width * 0.32;
  isDragging = true;
  e.preventDefault();
  e.stopPropagation();
}

function handleJoystickMove(e) {
  if (!isDragging) return;
  const rect = joystickBase.getBoundingClientRect();
  const searchRadius = rect.width * 1.2;
  
  // Find the touch that corresponds to the joystick base
  let touch = null;
  for (let i = 0; i < e.touches.length; i++) {
    const t = e.touches[i];
    // Check distance from base center to find joystick touch
    const dx = t.clientX - startX;
    const dy = t.clientY - startY;
    if (Math.sqrt(dx*dx + dy*dy) < searchRadius || touch === null) {
      touch = t;
    }
  }

  if (!touch) return;

  let dx = touch.clientX - startX;
  let dy = touch.clientY - startY;
  const dist = Math.sqrt(dx*dx + dy*dy);

  if (dist > maxRadius) {
    dx = (dx / dist) * maxRadius;
    dy = (dy / dist) * maxRadius;
  }

  // Move the stick visually (preserving CSS centering translate)
  joystickStick.style.transform = `translate(-50%, -50%) translate3d(${dx}px, ${dy}px, 0)`;

  // Normalized values
  const valX = dx / maxRadius;
  const valY = dy / maxRadius;

  // Map to directions with deadzone
  if (valY < -deadzone) {
    gameState.controlsHeld.UP = true;
    gameState.controlsHeld.DOWN = false;
  } else if (valY > deadzone) {
    gameState.controlsHeld.DOWN = true;
    gameState.controlsHeld.UP = false;
  } else {
    gameState.controlsHeld.UP = false;
    gameState.controlsHeld.DOWN = false;
  }

  if (valX < -deadzone) {
    gameState.controlsHeld.LEFT = true;
    gameState.controlsHeld.RIGHT = false;
  } else if (valX > deadzone) {
    gameState.controlsHeld.RIGHT = true;
    gameState.controlsHeld.LEFT = false;
  } else {
    gameState.controlsHeld.LEFT = false;
    gameState.controlsHeld.RIGHT = false;
  }

  e.preventDefault();
  e.stopPropagation();
}

function handleJoystickEnd(e) {
  if (!isDragging) return;
  const rect = joystickBase.getBoundingClientRect();
  const searchRadius = rect.width * 1.2;
  
  // If there are no touches left, or the joystick touch ended
  if (e.touches.length === 0) {
    isDragging = false;
    joystickStick.style.transform = 'translate(-50%, -50%) translate3d(0px, 0px, 0px)';
    gameState.controlsHeld.UP = false;
    gameState.controlsHeld.DOWN = false;
    gameState.controlsHeld.LEFT = false;
    gameState.controlsHeld.RIGHT = false;
  } else {
    // Check if joystick touch is still active
    let joystickStillActive = false;
    for (let i = 0; i < e.touches.length; i++) {
      const t = e.touches[i];
      const dx = t.clientX - startX;
      const dy = t.clientY - startY;
      if (Math.sqrt(dx*dx + dy*dy) < searchRadius) {
        joystickStillActive = true;
        break;
      }
    }
    if (!joystickStillActive) {
      isDragging = false;
      joystickStick.style.transform = 'translate(-50%, -50%) translate3d(0px, 0px, 0px)';
      gameState.controlsHeld.UP = false;
      gameState.controlsHeld.DOWN = false;
      gameState.controlsHeld.LEFT = false;
      gameState.controlsHeld.RIGHT = false;
    }
  }
}

function handleAimJoystickStart(e) {
  const touch = e.touches[0];
  const rect = aimJoystickBase.getBoundingClientRect();
  aimStartX = rect.left + rect.width / 2;
  aimStartY = rect.top + rect.height / 2;
  aimMaxRadius = rect.width * 0.32;
  isAimDragging = true;
  e.preventDefault();
  e.stopPropagation();
}

function handleAimJoystickMove(e) {
  if (!isAimDragging) return;
  const rect = aimJoystickBase.getBoundingClientRect();
  const searchRadius = rect.width * 1.2;

  // Find the touch corresponding to the aim joystick base
  let touch = null;
  for (let i = 0; i < e.touches.length; i++) {
    const t = e.touches[i];
    const dx = t.clientX - aimStartX;
    const dy = t.clientY - aimStartY;
    if (Math.sqrt(dx*dx + dy*dy) < searchRadius || touch === null) {
      touch = t;
    }
  }

  if (!touch) return;

  let dx = touch.clientX - aimStartX;
  let dy = touch.clientY - aimStartY;
  const dist = Math.sqrt(dx*dx + dy*dy);

  if (dist > aimMaxRadius) {
    dx = (dx / dist) * aimMaxRadius;
    dy = (dy / dist) * aimMaxRadius;
  }

  // Move the stick visually (preserving CSS centering translate)
  aimJoystickStick.style.transform = `translate(-50%, -50%) translate3d(${dx}px, ${dy}px, 0)`;

  // Aim direction vector (normalized)
  if (aimMaxRadius > 0 && dist > aimMaxRadius * 0.1) {
    const normX = dx / dist;
    const normY = dy / dist;
    lastAimNormX = normX;
    lastAimNormY = normY;
    gameState.aimAngle = Math.atan2(normY, normX);
    gameState.aimDirX = normX;
    gameState.aimDirY = normY;

    // Update aim target in game state relative to current player position
    updateDoubleJoyAimPosition(300);
  }

  e.preventDefault();
  e.stopPropagation();
}

function handleAimJoystickEnd(e) {
  if (!isAimDragging) return;
  const rect = aimJoystickBase.getBoundingClientRect();
  const searchRadius = rect.width * 1.2;

  if (e.touches.length === 0) {
    isAimDragging = false;
    aimJoystickStick.style.transform = 'translate(-50%, -50%) translate3d(0px, 0px, 0px)';
  } else {
    let aimStillActive = false;
    for (let i = 0; i < e.touches.length; i++) {
      const t = e.touches[i];
      const dx = t.clientX - aimStartX;
      const dy = t.clientY - aimStartY;
      if (Math.sqrt(dx*dx + dy*dy) < searchRadius) {
        aimStillActive = true;
        break;
      }
    }
    if (!aimStillActive) {
      isAimDragging = false;
      aimJoystickStick.style.transform = 'translate(-50%, -50%) translate3d(0px, 0px, 0px)';
    }
  }
}

function updateCoordinates(touch, canvas) {
  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width / rect.width;
  const scaleY = canvas.height / rect.height;
  gameState.controlsHeld.posX = Math.floor((touch.clientX - rect.left) * scaleX);
  gameState.controlsHeld.posY = Math.floor((touch.clientY - rect.top) * scaleY);
  gameState.mouseX = gameState.controlsHeld.posX;
  gameState.mouseY = gameState.controlsHeld.posY;
}

function handleCanvasTouchStart(e) {
  const myTitan = getControlledTitan(gameState.game);
  const isGoalie = myTitan && myTitan.type === 'GOALIE';
  const currentPreset = sessionStorage.getItem('controlPreset') || getDefaultPreset();

  if (!isGoalie && currentPreset !== 'mobile-single') return;

  const canvas = e.currentTarget;
  if (e.targetTouches.length > 0) {
    const touch = e.targetTouches[0];
    activeCanvasTouchId = touch.identifier;
    hasLobbedCurrentTouch = false;
    updateCoordinates(touch, canvas);

    if (isGoalie) {
      if (handleUIClick(gameState.mouseX, gameState.mouseY)) {
        e.preventDefault();
        return;
      }
      // Goalie screen tap: target & kill minion without affecting movement or shotBtn
      const posX = Math.floor((gameState.mouseX || 0) / 0.9375);
      const posY = gameState.mouseY || 0;
      gameState.pendingGoalieAttack = { x: posX, y: posY };
      e.preventDefault();
      return;
    }

    e.preventDefault();
  }
}

function handleCanvasTouchMove(e) {
  const myTitan = getControlledTitan(gameState.game);
  const isGoalie = myTitan && myTitan.type === 'GOALIE';
  const currentPreset = sessionStorage.getItem('controlPreset') || getDefaultPreset();

  if (!isGoalie && currentPreset !== 'mobile-single') return;

  const canvas = e.currentTarget;
  if (activeCanvasTouchId !== null) {
    // Find the active touch
    for (let i = 0; i < e.touches.length; i++) {
      if (e.touches[i].identifier === activeCanvasTouchId) {
        updateCoordinates(e.touches[i], canvas);
        break;
      }
    }
    e.preventDefault();
  }
}

function handleCanvasTouchEnd(e) {
  const myTitan = getControlledTitan(gameState.game);
  const isGoalie = myTitan && myTitan.type === 'GOALIE';
  const currentPreset = sessionStorage.getItem('controlPreset') || getDefaultPreset();

  if (!isGoalie && currentPreset !== 'mobile-single') return;

  if (activeCanvasTouchId !== null) {
    // Check if the active touch has ended
    let touchEnded = true;
    for (let i = 0; i < e.touches.length; i++) {
      if (e.touches[i].identifier === activeCanvasTouchId) {
        touchEnded = false;
        break;
      }
    }

    if (touchEnded) {
      if (!isGoalie) {
        if (!hasLobbedCurrentTouch) {
          gameState.controlsHeld.shotBtn = true;
          setTimeout(() => {
            gameState.controlsHeld.shotBtn = false;
          }, 50);
        }
      }
      activeCanvasTouchId = null;
    }
  }
}

function updateMobileButtonStates(game, myTitan) {
  if (!myTitan) return;

  const isDead = myTitan.health !== undefined && myTitan.health <= 0;
  
  // Check active cooldowns from effectPool
  let hasCdQ = false;
  let hasCdW = false;
  let hasCdSteal = false;
  let hasCdCurve = false;

  if (game && game.effectPool && Array.isArray(game.effectPool.effects)) {
    const effects = game.effectPool.effects;
    const onEntities = game.effectPool.on || [];
    for (let i = 0; i < effects.length; i++) {
      const e = effects[i];
      const en = onEntities[i] || (e && e.on);
      if (e && en && en.id !== undefined && en.id.toString() === myTitan.id.toString()) {
        const effName = e.effect || '';
        if (effName === 'COOLDOWN_Q' || effName === 'COOLDOWN_E') hasCdQ = true;
        if (effName === 'COOLDOWN_W' || effName === 'COOLDOWN_R') hasCdW = true;
        if (effName === 'COOLDOWN_STEAL') hasCdSteal = true;
        if (effName === 'COOLDOWN_CURVE') hasCdCurve = true;
      }
    }
  }

  // Ability 1 (E/Q)
  if (btnAbility1) {
    let disabled = isDead;
    if (myTitan.type === 'ARTISAN') {
      if (myTitan.possession === 1) {
        disabled = disabled || hasCdCurve;
      } else {
        disabled = disabled || hasCdQ;
      }
    } else {
      disabled = disabled || hasCdQ;
    }
    btnAbility1.classList.toggle('disabled', disabled);
  }

  // Ability 2 (R/W)
  if (btnAbility2) {
    const disabled = isDead || hasCdW;
    btnAbility2.classList.toggle('disabled', disabled);
  }

  // Possession (Steal / Lob)
  if (btnPossession) {
    const hasPossession = myTitan.possession === 1;
    // Greys out during STEAL cooldown or death (Lob mode does not show when not in possession)
    const disabled = isDead || (!hasPossession && hasCdSteal);
    btnPossession.classList.toggle('disabled', disabled);
  }

  // Shot
  if (btnShot) {
    const disabled = isDead;
    btnShot.classList.toggle('disabled', disabled);
  }

  // Boost Switch
  if (boostSwitch) {
    const cannotBoost = isDead || (myTitan.fuel !== undefined && myTitan.fuel < 1.0) || (myTitan.possession === 1 && myTitan.type !== 'DASHER');
    boostSwitch.classList.toggle('disabled', cannotBoost);
  }
}

export function updateMobileControls(game) {
  if (!container) return;

  const currentPreset = sessionStorage.getItem('controlPreset') || getDefaultPreset();
  const isGameplayPhase = gameState.phase === GamePhase.INGAME || 
                          gameState.phase === GamePhase.COUNTDOWN || 
                          gameState.phase === GamePhase.SCORE_FREEZE ||
                          gameState.phase === GamePhase.TUTORIAL ||
                          gameState.phase === GamePhase.TUTORIAL_START ||
                          gameState.phase === 'TUTORIAL' ||
                          gameState.phase === 'TUTORIAL_START';

  const isMobile = currentPreset === 'mobile-single' || currentPreset === 'mobile-double';

  if (isMobile && isGameplayPhase) {
    if (!document.body.classList.contains('mobile-controls-active')) {
      document.body.classList.add('mobile-controls-active');
    }
    if (container.style.display !== 'flex') {
      container.style.display = 'flex';
      // Reset stick/directions on show
      if (joystickStick) joystickStick.style.transform = 'translate(-50%, -50%) translate3d(0px, 0px, 0px)';
      if (aimJoystickStick) aimJoystickStick.style.transform = 'translate(-50%, -50%) translate3d(0px, 0px, 0px)';
      gameState.controlsHeld.UP = false;
      gameState.controlsHeld.DOWN = false;
      gameState.controlsHeld.LEFT = false;
      gameState.controlsHeld.RIGHT = false;
      isDragging = false;
      isAimDragging = false;
      activeCanvasTouchId = null;
      hasLobbedCurrentTouch = false;
    }

    const myTitan = getControlledTitan(game);
    const isGoalie = myTitan && myTitan.type === 'GOALIE';

    if (joystickBase && joystickBase.style.display !== '') {
      joystickBase.style.display = '';
    }
    if (mobileButtonsZone && mobileButtonsZone.style.display !== 'grid') {
      mobileButtonsZone.style.display = 'grid';
    }

    // Only show Upgrade button for Goalie titans
    if (btnUpgrade) {
      if (isGoalie) {
        if (btnUpgrade.style.display !== 'flex') btnUpgrade.style.display = 'flex';
      } else {
        if (btnUpgrade.style.display !== 'none') btnUpgrade.style.display = 'none';
      }
    }

    // Toggle single vs double joystick specific layout/UI elements
    if (currentPreset === 'mobile-double') {
      if (aimJoystickZone && aimJoystickZone.style.display !== 'flex') {
        aimJoystickZone.style.display = 'flex';
      }
      if (btnShot && btnShot.style.display !== 'flex') {
        btnShot.style.display = 'flex';
      }
      if (mobileButtonsZone && !mobileButtonsZone.classList.contains('double-joy')) {
        mobileButtonsZone.classList.add('double-joy');
      }

      // Initialize default aim direction if not set
      if (gameState.aimAngle === undefined) {
        const isAway = myTitan && (myTitan.team === 'AWAY' || myTitan.team === 1);
        lastAimNormX = isAway ? -1 : 1;
        lastAimNormY = 0;
        gameState.aimAngle = isAway ? Math.PI : 0;
        gameState.aimDirX = lastAimNormX;
        gameState.aimDirY = lastAimNormY;
      }

      // Maintain aim position continuously as player moves
      updateDoubleJoyAimPosition(300);
    } else {
      if (aimJoystickZone && aimJoystickZone.style.display !== 'none') {
        aimJoystickZone.style.display = 'none';
      }
      if (btnShot && btnShot.style.display !== 'none') {
        btnShot.style.display = 'none';
      }
      if (mobileButtonsZone && mobileButtonsZone.classList.contains('double-joy')) {
        mobileButtonsZone.classList.remove('double-joy');
      }
    }

    // Ability 1 and 2 buttons (hidden for Goalie as Goalie has no 1/2 abilities)
    if (isGoalie) {
      if (btnAbility1 && btnAbility1.style.display !== 'none') {
        btnAbility1.style.display = 'none';
        gameState.controlsHeld.E = false;
      }
      if (btnAbility2 && btnAbility2.style.display !== 'none') {
        btnAbility2.style.display = 'none';
        gameState.controlsHeld.R = false;
      }
    } else {
      if (btnAbility1 && btnAbility1.style.display !== 'flex') {
        btnAbility1.style.display = 'flex';
      }
      if (btnAbility2 && btnAbility2.style.display !== 'flex') {
        btnAbility2.style.display = 'flex';
      }
    }

    // Dynamic possession button text & classes
    if (btnPossession && myTitan) {
      const hasPossession = myTitan.possession === 1;
      if (hasPossession) {
        if (btnPossession.textContent !== 'LOB') {
          btnPossession.textContent = 'LOB';
          btnPossession.classList.remove('steal-mode');
          btnPossession.classList.add('lob-mode');
        }
      } else {
        if (btnPossession.textContent !== 'STEAL') {
          btnPossession.textContent = 'STEAL';
          btnPossession.classList.remove('lob-mode');
          btnPossession.classList.add('steal-mode');
        }
      }
    }

    // Dynamic Boost switch state synchronization
    if (boostSwitch) {
      if (boostSwitchContainer && boostSwitchContainer.style.display !== 'flex') {
        boostSwitchContainer.style.display = 'flex';
      }

      // Auto-shutoff: Turn off automatically if titan runs out of boost or cannot boost
      if (gameState.controlsHeld.BOOST && myTitan) {
        if (myTitan.fuel <= 0 || myTitan.fuel < 1.0 || myTitan.health <= 0 || (myTitan.possession === 1 && myTitan.type !== 'DASHER')) {
          setBoostState(false);
        }
      }

      // Ensure visual switch state always mirrors gameState.controlsHeld.BOOST
      const isBoosting = !!gameState.controlsHeld.BOOST;
      if (isBoosting !== boostSwitch.classList.contains('active')) {
        if (isBoosting) {
          boostSwitch.classList.add('active');
          boostSwitch.setAttribute('aria-checked', 'true');
        } else {
          boostSwitch.classList.remove('active');
          boostSwitch.setAttribute('aria-checked', 'false');
        }
      }
    }

    // Dynamically grey out mobile buttons based on cooldowns, fuel, and possession
    updateMobileButtonStates(game, myTitan);
  } else {
    if (document.body.classList.contains('mobile-controls-active')) {
      document.body.classList.remove('mobile-controls-active');
    }
    if (container.style.display !== 'none') {
      container.style.display = 'none';
      // Clear buttons states if hidden
      gameState.controlsHeld.E = false;
      gameState.controlsHeld.R = false;
      gameState.controlsHeld.lobBtn = false;
      gameState.controlsHeld.STEAL = false;
      gameState.controlsHeld.shotBtn = false;
      gameState.controlsHeld.MV_CLICK = false;
      setBoostState(false);
    }
  }
}
