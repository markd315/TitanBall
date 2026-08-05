import { gameState } from '../state.js';

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

  canvas.addEventListener('mousemove', updateCoordinates);

  canvas.addEventListener('mousedown', (e) => {
    updateCoordinates(e);
    if (e.button === 0) { // Left Click -> SHOT
      gameState.controlsHeld.shotBtn = true;
    } else if (e.button === 2) { // Right Click -> LOB / MOVE
      gameState.controlsHeld.lobBtn = true;
    }
    e.preventDefault();
  });

  canvas.addEventListener('mouseup', (e) => {
    updateCoordinates(e);
    if (e.button === 0) {
      gameState.controlsHeld.shotBtn = false;
    } else if (e.button === 2) {
      gameState.controlsHeld.lobBtn = false;
    }
    e.preventDefault();
  });

  // Prevent right click menu on canvas
  canvas.addEventListener('contextmenu', (e) => {
    e.preventDefault();
  });
}