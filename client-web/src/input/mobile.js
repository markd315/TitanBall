import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';

let joystickBase = null;
let joystickStick = null;
let container = null;
let btnAbility1 = null;
let btnAbility2 = null;
let btnPossession = null;

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

// Single Joystick Mode touch tracking variables
let activeCanvasTouchId = null;
let hasLobbedCurrentTouch = false;

// Active action of the possession button (LOB or STEAL)
let possessionActiveAction = null;

export function initMobileControls() {
  joystickBase = document.getElementById('joystick-base');
  joystickStick = document.getElementById('joystick-stick');
  container = document.getElementById('mobile-controls-container');
  btnAbility1 = document.getElementById('btn-ability-1');
  btnAbility2 = document.getElementById('btn-ability-2');
  btnPossession = document.getElementById('btn-possession');

  // Double joystick elements
  aimJoystickZone = document.getElementById('aim-joystick-zone');
  aimJoystickBase = document.getElementById('aim-joystick-base');
  aimJoystickStick = document.getElementById('aim-joystick-stick');
  btnShot = document.getElementById('btn-shot');
  mobileButtonsZone = document.getElementById('mobile-buttons-zone');

  const canvas = document.getElementById('gameCanvas');

  if (!joystickBase || !joystickStick || !container || !btnAbility1 || !btnAbility2 || !btnPossession || !canvas ||
      !aimJoystickZone || !aimJoystickBase || !aimJoystickStick || !btnShot || !mobileButtonsZone) {
    console.warn("Mobile control HTML elements not found.");
    return;
  }

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
    const currentPreset = localStorage.getItem('controlPreset') || 'rts';
    const game = gameState.game;
    const hasPossession = game && game.underControl && game.underControl.possession === 1;
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

  // Button Shot (only for Double Joystick Mode)
  btnShot.addEventListener('touchstart', (e) => {
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
}

function handleJoystickStart(e) {
  const touch = e.touches[0];
  const rect = joystickBase.getBoundingClientRect();
  startX = rect.left + rect.width / 2;
  startY = rect.top + rect.height / 2;
  maxRadius = rect.width * 0.35;
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

  // Move the stick visually
  joystickStick.style.transform = `translate3d(${dx}px, ${dy}px, 0)`;

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
    joystickStick.style.transform = 'translate3d(0px, 0px, 0px)';
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
      joystickStick.style.transform = 'translate3d(0px, 0px, 0px)';
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
  aimMaxRadius = rect.width * 0.35;
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

  // Move the stick visually
  aimJoystickStick.style.transform = `translate3d(${dx}px, ${dy}px, 0)`;

  // Aim direction vector (normalized)
  if (aimMaxRadius > 0 && dist > aimMaxRadius * 0.1) {
    const normX = dx / dist;
    const normY = dy / dist;

    // Update aim target in game state
    const game = gameState.game;
    if (game && game.underControl) {
      const playerX = game.underControl.X;
      const playerY = game.underControl.Y;

      // Calculate screen coordinate for player center (width/height is 70)
      const playerCanvasX = playerX + 35 - (gameState.camX || 0);
      const playerCanvasY = playerY + 35 - (gameState.camY || 0);

      const aimDistance = 300; // Aiming radius in canvas pixels
      gameState.mouseX = playerCanvasX + normX * aimDistance;
      gameState.mouseY = playerCanvasY + normY * aimDistance;
      gameState.controlsHeld.posX = Math.floor(gameState.mouseX);
      gameState.controlsHeld.posY = Math.floor(gameState.mouseY);
    }
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
    aimJoystickStick.style.transform = 'translate3d(0px, 0px, 0px)';
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
      aimJoystickStick.style.transform = 'translate3d(0px, 0px, 0px)';
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
  const currentPreset = localStorage.getItem('controlPreset') || 'rts';
  if (currentPreset !== 'mobile-single') return;

  const canvas = e.currentTarget;
  if (e.targetTouches.length > 0) {
    const touch = e.targetTouches[0];
    activeCanvasTouchId = touch.identifier;
    hasLobbedCurrentTouch = false;
    updateCoordinates(touch, canvas);
    e.preventDefault();
  }
}

function handleCanvasTouchMove(e) {
  const currentPreset = localStorage.getItem('controlPreset') || 'rts';
  if (currentPreset !== 'mobile-single') return;

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
  const currentPreset = localStorage.getItem('controlPreset') || 'rts';
  if (currentPreset !== 'mobile-single') return;

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
      if (!hasLobbedCurrentTouch) {
        gameState.controlsHeld.shotBtn = true;
        setTimeout(() => {
          gameState.controlsHeld.shotBtn = false;
        }, 50);
      }
      activeCanvasTouchId = null;
      hasLobbedCurrentTouch = false;
      e.preventDefault();
    }
  }
}

export function updateMobileControls(game) {
  if (!container) return;

  const currentPreset = localStorage.getItem('controlPreset') || 'rts';
  const isGameplayPhase = gameState.phase === GamePhase.INGAME || 
                          gameState.phase === GamePhase.COUNTDOWN || 
                          gameState.phase === GamePhase.SCORE_FREEZE;

  const isMobile = currentPreset === 'mobile-single' || currentPreset === 'mobile-double';

  if (isMobile && isGameplayPhase) {
    if (container.style.display !== 'flex') {
      container.style.display = 'flex';
      // Reset stick/directions on show
      if (joystickStick) joystickStick.style.transform = 'translate3d(0px, 0px, 0px)';
      if (aimJoystickStick) aimJoystickStick.style.transform = 'translate3d(0px, 0px, 0px)';
      gameState.controlsHeld.UP = false;
      gameState.controlsHeld.DOWN = false;
      gameState.controlsHeld.LEFT = false;
      gameState.controlsHeld.RIGHT = false;
      isDragging = false;
      isAimDragging = false;
      activeCanvasTouchId = null;
      hasLobbedCurrentTouch = false;
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

    // Dynamic possession button text & classes
    if (btnPossession && game && game.underControl) {
      const hasPossession = game.underControl.possession === 1;
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
  } else {
    if (container.style.display !== 'none') {
      container.style.display = 'none';
      // Clear buttons states if hidden
      gameState.controlsHeld.E = false;
      gameState.controlsHeld.R = false;
      gameState.controlsHeld.lobBtn = false;
      gameState.controlsHeld.STEAL = false;
      gameState.controlsHeld.shotBtn = false;
    }
  }
}
