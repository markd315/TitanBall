import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';

const keyMap = {
  'ArrowUp': 'UP',
  'ArrowDown': 'DOWN',
  'ArrowLeft': 'LEFT',
  'ArrowRight': 'RIGHT',
  'KeyW': 'UP',
  'KeyS': 'DOWN',
  'KeyA': 'LEFT',
  'KeyD': 'RIGHT',
  'KeyQ': 'E',
  'KeyE': 'STEAL',
  'KeyR': 'BOOST_LOCK',
  'KeyF': 'BOOST',
  'KeyT': 'lobBtn',
  'KeyZ': 'SWITCH',
  'Space': 'CAM'
};

export function initKeyboard() {
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
      }
    }

    const action = keyMap[e.code];
    if (action) {
      gameState.controlsHeld[action] = true;
      if (action === 'CAM') {
        // Toggle camera lock behavior if needed
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

    const action = keyMap[e.code];
    if (action) {
      gameState.controlsHeld[action] = false;
      e.preventDefault();
    }
  });
}