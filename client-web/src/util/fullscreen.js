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
    const fn = docEl.requestFullscreen || docEl.webkitRequestFullscreen || docEl.mozRequestFullScreen || docEl.msRequestFullscreen;
    if (fn) await (fn.call(docEl, { navigationUI: 'hide' }).catch ? fn.call(docEl, { navigationUI: 'hide' }).catch(() => fn.call(docEl)) : fn.call(docEl));
  } catch (err) {
    console.warn('Fullscreen request failed:', err);
  }
  await lockLandscapeOrientation();
  updateFullscreenButtonUI();
}

export async function exitFullscreen() {
  try {
    const fn = document.exitFullscreen || document.webkitExitFullscreen || document.mozCancelFullScreen || document.msExitFullscreen;
    if (fn) await fn.call(document);
  } catch (err) {
    console.warn('Exit fullscreen failed:', err);
  }
  unlockOrientation();
  updateFullscreenButtonUI();
}

export async function toggleFullscreen() {
  await (isFullscreenActive() ? exitFullscreen() : requestFullscreen());
}

export function updateFullscreenButtonUI() {
  const btn = document.getElementById('fullscreen-btn');
  if (!btn) return;
  const active = isFullscreenActive();
  btn.classList.toggle('active', active);
  btn.title = active ? 'Exit Fullscreen' : 'Enter Fullscreen';
  const iconEl = btn.querySelector('.fs-icon'), labelEl = btn.querySelector('.fs-label');
  if (iconEl) iconEl.textContent = active ? '🗗' : '⛶';
  if (labelEl) labelEl.textContent = active ? 'EXIT' : 'FULL';
}

export function checkOrientation() {
  const overlay = document.getElementById('orientation-overlay');
  if (!overlay) return;
  const isQueuedOrInGame = [GamePhase.WAIT_FOR_GAME, GamePhase.COUNTDOWN, GamePhase.INGAME, GamePhase.SCORE_FREEZE, GamePhase.TUTORIAL_START, GamePhase.TUTORIAL, 'TUTORIAL', 'TUTORIAL_START'].includes(gameState.phase);
  overlay.style.display = (isQueuedOrInGame && isMobileDevice() && window.innerHeight > window.innerWidth) ? 'flex' : 'none';
}

export function initFullscreenListeners() {
  const fsBtn = document.getElementById('fullscreen-btn');
  if (fsBtn) {
    const toggle = (e) => { e.preventDefault(); toggleFullscreen(); };
    fsBtn.addEventListener('click', toggle);
    fsBtn.addEventListener('touchstart', toggle, { passive: false });
  }

  const orientFsBtn = document.getElementById('orientation-fullscreen-btn');
  if (orientFsBtn) {
    const handleOrientFs = (e) => { e.preventDefault(); requestFullscreen(); };
    orientFsBtn.addEventListener('click', handleOrientFs);
    orientFsBtn.addEventListener('touchstart', handleOrientFs, { passive: false });
  }

  ['fullscreenchange', 'webkitfullscreenchange', 'mozfullscreenchange', 'MSFullscreenChange'].forEach(evt => {
    document.addEventListener(evt, () => { updateFullscreenButtonUI(); checkOrientation(); });
  });

  window.addEventListener('resize', checkOrientation);
  window.addEventListener('orientationchange', () => setTimeout(checkOrientation, 200));

  updateFullscreenButtonUI();
  checkOrientation();
}
