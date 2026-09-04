import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';

export function isFullscreenActive() {
  return !!(
    document.fullscreenElement ||
    document.webkitFullscreenElement ||
    document.mozFullScreenElement ||
    document.msFullscreenElement
  );
}

export function isMobileDevice() {
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) ||
    ('ontouchstart' in window) ||
    (navigator.maxTouchPoints > 0);
}

export async function lockLandscapeOrientation() {
  if (screen.orientation && typeof screen.orientation.lock === 'function') {
    try {
      await screen.orientation.lock('landscape');
    } catch (e) {
      try {
        await screen.orientation.lock('landscape-primary');
      } catch (err) {
        console.log('Orientation lock not permitted or supported:', err);
      }
    }
  }
}

export async function unlockOrientation() {
  if (screen.orientation && typeof screen.orientation.unlock === 'function') {
    try {
      screen.orientation.unlock();
    } catch (e) {
      console.log('Orientation unlock failed:', e);
    }
  }
}

export async function requestFullscreen() {
  const docEl = document.documentElement;
  try {
    if (docEl.requestFullscreen) {
      await docEl.requestFullscreen({ navigationUI: 'hide' });
    } else if (docEl.webkitRequestFullscreen) {
      await docEl.webkitRequestFullscreen();
    } else if (docEl.mozRequestFullScreen) {
      await docEl.mozRequestFullScreen();
    } else if (docEl.msRequestFullscreen) {
      await docEl.msRequestFullscreen();
    }
  } catch (err) {
    console.warn('Fullscreen request failed:', err);
  }

  // Attempt landscape orientation lock
  await lockLandscapeOrientation();
  updateFullscreenButtonUI();
}

export async function exitFullscreen() {
  try {
    if (document.exitFullscreen) {
      await document.exitFullscreen();
    } else if (document.webkitExitFullscreen) {
      await document.webkitExitFullscreen();
    } else if (document.mozCancelFullScreen) {
      await document.mozCancelFullScreen();
    } else if (document.msExitFullscreen) {
      await document.msExitFullscreen();
    }
  } catch (err) {
    console.warn('Exit fullscreen failed:', err);
  }

  unlockOrientation();
  updateFullscreenButtonUI();
}

export async function toggleFullscreen() {
  if (isFullscreenActive()) {
    await exitFullscreen();
  } else {
    await requestFullscreen();
  }
}

export function updateFullscreenButtonUI() {
  const btn = document.getElementById('fullscreen-btn');
  if (!btn) return;

  const active = isFullscreenActive();
  const iconEl = btn.querySelector('.fs-icon');
  const labelEl = btn.querySelector('.fs-label');

  if (active) {
    btn.classList.add('active');
    if (iconEl) iconEl.textContent = '🗗';
    if (labelEl) labelEl.textContent = 'EXIT';
    btn.title = 'Exit Fullscreen';
  } else {
    btn.classList.remove('active');
    if (iconEl) iconEl.textContent = '⛶';
    if (labelEl) labelEl.textContent = 'FULL';
    btn.title = 'Enter Fullscreen';
  }
}

export function checkOrientation() {
  const overlay = document.getElementById('orientation-overlay');
  if (!overlay) return;

  const isQueuedOrInGame = gameState.phase === GamePhase.WAIT_FOR_GAME ||
                           gameState.phase === GamePhase.COUNTDOWN ||
                           gameState.phase === GamePhase.INGAME ||
                           gameState.phase === GamePhase.SCORE_FREEZE ||
                           gameState.phase === GamePhase.TUTORIAL_START ||
                           gameState.phase === GamePhase.TUTORIAL ||
                           gameState.phase === 'TUTORIAL' ||
                           gameState.phase === 'TUTORIAL_START';

  // Only show rotation prompt if player has joined queue/game, is on a mobile device, and currently in portrait orientation
  const isPortrait = window.innerHeight > window.innerWidth;
  if (isQueuedOrInGame && isMobileDevice() && isPortrait) {
    overlay.style.display = 'flex';
  } else {
    overlay.style.display = 'none';
  }
}

export function initFullscreenListeners() {
  const fsBtn = document.getElementById('fullscreen-btn');
  if (fsBtn) {
    fsBtn.addEventListener('click', (e) => {
      e.preventDefault();
      toggleFullscreen();
    });
    fsBtn.addEventListener('touchstart', (e) => {
      e.preventDefault();
      toggleFullscreen();
    }, { passive: false });
  }

  const orientFsBtn = document.getElementById('orientation-fullscreen-btn');
  if (orientFsBtn) {
    const handleOrientFs = (e) => {
      e.preventDefault();
      requestFullscreen();
    };
    orientFsBtn.addEventListener('click', handleOrientFs);
    orientFsBtn.addEventListener('touchstart', handleOrientFs, { passive: false });
  }

  // Handle Fullscreen change events across browsers
  document.addEventListener('fullscreenchange', () => {
    updateFullscreenButtonUI();
    checkOrientation();
  });
  document.addEventListener('webkitfullscreenchange', () => {
    updateFullscreenButtonUI();
    checkOrientation();
  });
  document.addEventListener('mozfullscreenchange', () => {
    updateFullscreenButtonUI();
    checkOrientation();
  });
  document.addEventListener('MSFullscreenChange', () => {
    updateFullscreenButtonUI();
    checkOrientation();
  });

  // Handle orientation and resize events
  window.addEventListener('resize', checkOrientation);
  window.addEventListener('orientationchange', () => {
    setTimeout(checkOrientation, 200);
  });

  // Initial check
  updateFullscreenButtonUI();
  checkOrientation();
}
