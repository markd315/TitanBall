import { gameState } from './state.js';
import { initCanvas, clearScreen, drawImageCam } from './render/canvas.js';
import { drawCredits } from './screens/credits.js';
import { initKeyboard } from './input/keyboard.js';
import { initMouse } from './input/mouse.js';
import { GamePhase } from './constants.js';
import { updateCamera } from './util/math.js';
import { initAssets, AssetManager } from './assets/sprites.js';
import { drawPlayers } from './render/players.js';
import { drawMinions } from './render/minions.js';
import { drawBall, displayBallArrow } from './render/ball.js';
import { drawGoals } from './render/goals.js';
import { drawHud } from './render/hud.js';
import { login, joinQueue, checkGame, register } from './network/auth.js';
import { connectGame, disconnectGame } from './network/socket.js';

let ctx;
let lastTime = 0;
let pollingInterval = null;
let currentScreen = 'login';
let lastPhase = null;
let lastScreen = null;

function updateOverlays() {
  if (gameState.phase === lastPhase && currentScreen === lastScreen) return;
  lastPhase = gameState.phase;
  lastScreen = currentScreen;

  const loginOverlay = document.getElementById('login-overlay');
  const signupOverlay = document.getElementById('signup-overlay');
  const modeOverlay = document.getElementById('mode-overlay');
  const lobbyOverlay = document.getElementById('lobby-overlay');
  
  if (!loginOverlay || !signupOverlay || !modeOverlay || !lobbyOverlay) return;

  loginOverlay.style.display = 'none';
  signupOverlay.style.display = 'none';
  modeOverlay.style.display = 'none';
  lobbyOverlay.style.display = 'none';

  if (gameState.phase === GamePhase.CREDITS) {
    if (!localStorage.getItem('accessToken')) {
      if (currentScreen === 'login') {
        loginOverlay.style.display = 'flex';
      } else {
        signupOverlay.style.display = 'flex';
      }
    }
  } else if (gameState.phase === GamePhase.SHOW_GAME_MODES) {
    modeOverlay.style.display = 'flex';
    const usernameSpan = document.getElementById('player-username');
    if (usernameSpan) {
      usernameSpan.textContent = localStorage.getItem('username') || 'Player';
    }
  } else if (gameState.phase === GamePhase.WAIT_FOR_GAME) {
    lobbyOverlay.style.display = 'flex';
  }
}

function startQueuePolling() {
  if (pollingInterval) clearInterval(pollingInterval);
  
  pollingInterval = setInterval(async () => {
    try {
      const status = await checkGame();
      if (status && status !== 'WAITING' && status !== 'NOT QUEUED') {
        clearInterval(pollingInterval);
        pollingInterval = null;
        console.log("Match found! Game ID:", status);
        gameState.gameID = status;
        gameState.phase = GamePhase.COUNTDOWN;
        connectGame(status);
      }
    } catch (e) {
      console.error("Queue poll error:", e);
    }
  }, 1000);
}

function stopQueuePolling() {
  if (pollingInterval) {
    clearInterval(pollingInterval);
    pollingInterval = null;
  }
}

function initUIListeners() {
  // Login click
  const loginBtn = document.getElementById('login-btn');
  if (loginBtn) {
    loginBtn.addEventListener('click', async () => {
      const email = document.getElementById('login-email').value || 'markd315@gmail.com';
      const pass = document.getElementById('login-pass').value || 'pass';
      const errorDiv = document.getElementById('login-error');
      
      try {
        if (errorDiv) errorDiv.style.display = 'none';
        console.log("Logging in as:", email);
        const data = await login(email, pass);
        localStorage.setItem('username', email.split('@')[0]);
        console.log("Login successful");
        gameState.phase = GamePhase.SHOW_GAME_MODES;
      } catch (err) {
        console.error(err);
        if (errorDiv) {
          errorDiv.textContent = 'Invalid credentials or server offline.';
          errorDiv.style.display = 'block';
        }
      }
    });
  }

  // Toggle Sign Up overlay
  const signupToggleBtn = document.getElementById('signup-toggle-btn');
  if (signupToggleBtn) {
    signupToggleBtn.addEventListener('click', () => {
      currentScreen = 'signup';
      const errorDiv = document.getElementById('signup-error');
      const successDiv = document.getElementById('signup-success');
      if (errorDiv) errorDiv.style.display = 'none';
      if (successDiv) successDiv.style.display = 'none';
      updateOverlays();
    });
  }

  // Toggle back to Login overlay
  const signupBackBtn = document.getElementById('signup-back-btn');
  if (signupBackBtn) {
    signupBackBtn.addEventListener('click', () => {
      currentScreen = 'login';
      const errorDiv = document.getElementById('login-error');
      if (errorDiv) errorDiv.style.display = 'none';
      updateOverlays();
    });
  }

  // Sign Up click
  const signupBtn = document.getElementById('signup-btn');
  if (signupBtn) {
    signupBtn.addEventListener('click', async () => {
      const username = document.getElementById('signup-username').value;
      const email = document.getElementById('signup-email').value;
      const pass = document.getElementById('signup-pass').value;
      const errorDiv = document.getElementById('signup-error');
      const successDiv = document.getElementById('signup-success');

      if (!username || !email || !pass) {
        if (errorDiv) {
          errorDiv.textContent = 'Please fill out all fields.';
          errorDiv.style.display = 'block';
        }
        return;
      }

      try {
        if (errorDiv) errorDiv.style.display = 'none';
        if (successDiv) successDiv.style.display = 'none';
        console.log("Registering:", username);
        await register(email, username, pass);
        
        if (successDiv) {
          successDiv.textContent = 'Account created! Logging in...';
          successDiv.style.display = 'block';
        }

        // Auto login on success
        setTimeout(async () => {
          try {
            const data = await login(email, pass);
            localStorage.setItem('username', username);
            gameState.phase = GamePhase.SHOW_GAME_MODES;
          } catch (loginErr) {
            console.error(loginErr);
            currentScreen = 'login';
            updateOverlays();
          }
        }, 1200);

      } catch (err) {
        console.error(err);
        if (errorDiv) {
          errorDiv.textContent = 'Registration failed. Email/username might be taken.';
          errorDiv.style.display = 'block';
        }
      }
    });
  }

  // Join 3v3 click
  const queue3v3Btn = document.getElementById('queue-3v3-btn');
  if (queue3v3Btn) {
    queue3v3Btn.addEventListener('click', async () => {
      try {
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = '3v3';
        
        console.log("Joining 3v3 Queue");
        await joinQueue('');
        gameState.phase = GamePhase.WAIT_FOR_GAME;
        startQueuePolling();
      } catch (err) {
        console.error(err);
      }
    });
  }

  // Join 1v1 click
  const queue1v1Btn = document.getElementById('queue-1v1-btn');
  if (queue1v1Btn) {
    queue1v1Btn.addEventListener('click', async () => {
      try {
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = '1v1';
        
        console.log("Joining 1v1 Queue");
        await joinQueue('1v1');
        gameState.phase = GamePhase.WAIT_FOR_GAME;
        startQueuePolling();
      } catch (err) {
        console.error(err);
      }
    });
  }

  // Leave queue click
  const leaveBtn = document.getElementById('leave-queue-btn');
  if (leaveBtn) {
    leaveBtn.addEventListener('click', async () => {
      try {
        console.log("Leaving queue");
        stopQueuePolling();
        const token = localStorage.getItem('accessToken');
        await fetch('/api/leave', {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${token}` }
        });
        gameState.phase = GamePhase.SHOW_GAME_MODES;
      } catch (err) {
        console.error(err);
      }
    });
  }
}

function drawIngame(ctx, dt) {
  const game = gameState.game;
  if (!game) return;
  
  updateCamera(game, gameState);
  const { camX, camY } = gameState;
  
  // Draw Field
  if (AssetManager.images['field']) {
    drawImageCam(ctx, AssetManager.images['field'], 1, 1, camX, camY);
  } else {
    ctx.fillStyle = '#0f3d0f';
    ctx.fillRect(0, 0, 1920, 1080);
  }
  
  drawGoals(ctx, game, camX, camY);
  drawPlayers(ctx, game, camX, camY);
  drawMinions(ctx, game, camX, camY);
  drawBall(ctx, game, camX, camY);
  displayBallArrow(ctx, game, camX, camY);
  drawHud(ctx, game, gameState);
}

function gameLoop(timestamp) {
  const dt = timestamp - lastTime;
  lastTime = timestamp;

  clearScreen(ctx);
  updateOverlays();

  // If already logged in, skip login screen
  if (gameState.phase === GamePhase.CREDITS && localStorage.getItem('accessToken')) {
    gameState.phase = GamePhase.SHOW_GAME_MODES;
  }

  switch (gameState.phase) {
    case GamePhase.CREDITS:
      drawCredits(ctx);
      break;
    case GamePhase.CONTROLS:
      ctx.fillStyle = 'white';
      ctx.font = '50px Arial';
      ctx.fillText('Controls - Press Space to Start Match Selection', 200, 200);
      break;
    case GamePhase.SHOW_GAME_MODES:
      // Rendered via HTML overlay
      break;
    case GamePhase.WAIT_FOR_GAME:
      // Rendered via HTML overlay
      break;
    case GamePhase.COUNTDOWN:
      ctx.fillStyle = 'white';
      ctx.font = '80px Arial';
      ctx.fillText('PREPARING MATCH...', 600, 540);
      break;
    case GamePhase.INGAME:
    case GamePhase.SCORE_FREEZE:
      drawIngame(ctx, dt);
      break;
    case GamePhase.ENDED:
      ctx.fillStyle = 'white';
      ctx.font = '50px Arial';
      ctx.fillText('Game Over', 200, 200);
      break;
    default:
      ctx.fillStyle = 'white';
      ctx.font = '30px Arial';
      ctx.fillText('Phase: ' + gameState.phase, 100, 100);
      break;
  }

  requestAnimationFrame(gameLoop);
}

export function start() {
  ctx = initCanvas();
  initAssets();
  initKeyboard();
  initMouse();
  initUIListeners();
  requestAnimationFrame(gameLoop);
}

start();