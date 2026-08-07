import { gameState } from '../state.js';
import { currentConfig, actionMap } from './keyboard.js';

export function initMouse() {
  const canvas = document.getElementById('gameCanvas');
  if (!canvas) return;

  const updateCoordinates = (e) => {
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    gameState.controlsHeld.posX = Math.floor((e.clientX - rect.left) * scaleX);
    gameState.controlsHeld.posY = Math.floor((e.clientY - rect.top) * scaleY);
    gameState.mouseX = gameState.controlsHeld.posX;
    gameState.mouseY = gameState.controlsHeld.posY;
  };

  const getMouseAction = (button) => {
    if (button === 0) return currentConfig['LMB'] || 'SHOT'; // fallback to SHOT
    if (button === 2) return currentConfig['RMB'] || 'LOB';  // fallback to LOB
    return null;
  };

  canvas.addEventListener('mousemove', updateCoordinates);

  canvas.addEventListener('mousedown', (e) => {
    updateCoordinates(e);
    const action = getMouseAction(e.button);
    if (action && actionMap[action]) {
      gameState.controlsHeld[actionMap[action]] = true;
    }
    e.preventDefault();
  });

  canvas.addEventListener('mouseup', (e) => {
    updateCoordinates(e);
    const action = getMouseAction(e.button);
    if (action && actionMap[action]) {
      gameState.controlsHeld[actionMap[action]] = false;
    }
    e.preventDefault();
  });

  // Prevent right click menu on canvas
  canvas.addEventListener('contextmenu', (e) => {
    e.preventDefault();
  });
}