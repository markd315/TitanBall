import { gameState } from '../state.js';

let socket = null;
let updateInterval = null;

export function connectGame(gameID) {
  if (socket) {
    socket.close();
  }

  const token = localStorage.getItem('accessToken');
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const url = `${protocol}//${window.location.host}/game`;
  
  socket = new WebSocket(url);
  
  socket.onopen = () => {
    console.log("WebSocket connected");
    // Start sending control inputs
    updateInterval = setInterval(() => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        const controls = {
          ...gameState.controlsHeld,
          token: token,
          gameID: gameID,
          camX: gameState.camX || 0,
          camY: gameState.camY || 0,
          posX: gameState.mouseX || 0,
          posY: gameState.mouseY || 0
        };
        socket.send(JSON.stringify(controls));
      }
    }, 15);
  };
  
  socket.onmessage = (event) => {
    const update = JSON.parse(event.data);
    gameState.game = update;
    if (update.phase) {
      gameState.phase = update.phase;
    }
  };
  
  socket.onclose = () => {
    console.log("WebSocket closed");
    if (updateInterval) {
      clearInterval(updateInterval);
      updateInterval = null;
    }
  };
  
  socket.onerror = (error) => {
    console.error("WebSocket error:", error);
  };
}

export function disconnectGame() {
  if (socket) {
    socket.close();
    socket = null;
  }
  if (updateInterval) {
    clearInterval(updateInterval);
    updateInterval = null;
  }
}