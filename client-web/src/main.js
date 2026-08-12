import { gameState } from './state.js';
import { initCanvas, clearScreen, drawImageCam } from './render/canvas.js';
import { initMasteries } from './screens/masteries.js';
import { drawCredits } from './screens/credits.js';
import { initKeyboard, setControlPreset } from './input/keyboard.js';
import { initMouse } from './input/mouse.js';
import { GamePhase } from './constants.js';
import { updateCamera } from './util/math.js';
import { initAssets, AssetManager } from './assets/sprites.js';
import { drawPlayers } from './render/players.js';
import { drawMinions } from './render/minions.js';
import { drawAimAndRangeIndicators } from './render/aim.js';
import { drawEffectIcons } from './render/effects.js';
import { drawBall, displayBallArrow } from './render/ball.js';
import { drawGoals } from './render/goals.js';
import { drawHud } from './render/hud.js';
import { login, joinQueue, checkGame, register, startTutorial } from './network/auth.js';
import { connectGame, disconnectGame } from './network/socket.js';
import { warmServer } from './network/warm.js';

let ctx;
let lastTime = 0;
let pollingInterval = null;
let currentScreen = 'login';
let lastPhase = null;
let lastScreen = null;
let idleStart = null;
window.warmExpired = false;

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
    if (!sessionStorage.getItem('accessToken')) {
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
      usernameSpan.textContent = sessionStorage.getItem('username') || 'Player';
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
        sessionStorage.setItem('username', email.split('@')[0]);
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
            sessionStorage.setItem('username', username);
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

  // Selected Match Size state
  let selectedMatchSize = 4;
  
  const sizeDisplay = document.getElementById('match-size-display');
  const sizeDownBtn = document.getElementById('size-down-btn');
  const sizeUpBtn = document.getElementById('size-up-btn');
  
  if (sizeDownBtn && sizeUpBtn && sizeDisplay) {
    sizeDownBtn.addEventListener('click', () => {
      if (selectedMatchSize > 2) {
        selectedMatchSize--;
        sizeDisplay.textContent = `${selectedMatchSize}v${selectedMatchSize}`;
      }
    });
    sizeUpBtn.addEventListener('click', () => {
      if (selectedMatchSize < 8) {
        selectedMatchSize++;
        sizeDisplay.textContent = `${selectedMatchSize}v${selectedMatchSize}`;
      }
    });
  }

  // Join Team Match click
  const queueTeamBtn = document.getElementById('queue-team-btn');
  if (queueTeamBtn) {
    queueTeamBtn.addEventListener('click', async () => {
      try {
        const classSelect = document.getElementById('class-select');
        const classSel = classSelect ? classSelect.value : 'WARRIOR';
        
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = `${selectedMatchSize}v${selectedMatchSize}`;
        const lobbyTitle = document.querySelector('#lobby-overlay h2');
        if (lobbyTitle) lobbyTitle.textContent = 'Searching Match';
        const lobbyStatus = document.querySelector('#lobby-overlay .stat-value[style*="pulse"]');
        if (lobbyStatus) lobbyStatus.textContent = 'FINDING PLAYERS...';
        
        const code = `/${selectedMatchSize}/0/1/5/2/9999/10/12`;
        console.log(`Joining Team Match Queue with size ${selectedMatchSize}v${selectedMatchSize} and class ${classSel}`);
        await joinQueue(code, classSel);
        gameState.is3v3 = true;
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
        const classSelect = document.getElementById('class-select');
        const classError = document.getElementById('class-select-error');
        if (classSelect && classSelect.value === 'GOALIE') {
          if (classError) {
            classError.textContent = "Error: Cannot queue for 1v1 Scrimmage as a Goalie. Please select a Titan class.";
            classError.style.display = 'block';
          }
          return;
        }
        
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = 'Scrimmage';
        const lobbyTitle = document.querySelector('#lobby-overlay h2');
        if (lobbyTitle) lobbyTitle.textContent = 'Searching Match';
        const lobbyStatus = document.querySelector('#lobby-overlay .stat-value[style*="pulse"]');
        if (lobbyStatus) lobbyStatus.textContent = 'FINDING PLAYERS...';
        
        const classSel = classSelect ? classSelect.value : 'WARRIOR';
        console.log("Joining Scrimmage (1v1) Queue as class:", classSel);
        await joinQueue('/1/0/1/5/2/9999/10/12', classSel);
        gameState.is3v3 = false;
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
        const token = sessionStorage.getItem('accessToken');
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

  // Play Tutorial click
  const tutorialBtn = document.getElementById('tutorial-btn');
  if (tutorialBtn) {
    tutorialBtn.addEventListener('click', async () => {
      try {
        console.log("Starting Tutorial");
        gameState.is3v3 = false;

        // Pre-unlock narration audios to avoid browser autoplay policy blocks
        for (let i = 0; i <= 4; i++) {
          const aud = AssetManager.audio['tut' + i];
          if (aud) {
            const oldVol = aud.volume;
            aud.volume = 0;
            aud.play().then(() => {
              aud.pause();
              aud.currentTime = 0;
              aud.volume = oldVol;
            }).catch(e => console.warn("Audio pre-unlock skipped:", e));
          }
        }

        // Set search overlay indicators for Tutorial
        const modeLabel = document.getElementById('queue-mode-label');
        if (modeLabel) modeLabel.textContent = 'Tutorial';
        const lobbyTitle = document.querySelector('#lobby-overlay h2');
        if (lobbyTitle) lobbyTitle.textContent = 'Preparing Tutorial';
        const lobbyStatus = document.querySelector('#lobby-overlay .stat-value[style*="pulse"]');
        if (lobbyStatus) lobbyStatus.textContent = 'LOADING RESOURCE...';

        const gameId = await startTutorial();
        gameState.phase = GamePhase.WAIT_FOR_GAME;
        startQueuePolling();
      } catch (err) {
        console.error(err);
      }
    });
  }

  // Logout click
  const logoutBtn = document.getElementById('logout-btn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      console.log("Logging out...");
      sessionStorage.removeItem('accessToken');
      sessionStorage.removeItem('refreshToken');
      sessionStorage.removeItem('username');
      sessionStorage.removeItem('classSelection');
      sessionStorage.removeItem('titanMasteries');
      gameState.phase = GamePhase.CREDITS;
      currentScreen = 'login';
      updateOverlays();
    });
  }

  // Class Select dropdown change
  const classSelect = document.getElementById('class-select');
  if (classSelect) {
    const savedClass = sessionStorage.getItem('classSelection') || 'WARRIOR';
    classSelect.value = savedClass;
    gameState.controlsHeld.classSelection = savedClass;
    
    const classError = document.getElementById('class-select-error');

    classSelect.addEventListener('change', (e) => {
      if (classError) {
        classError.style.display = 'none';
      }
      gameState.controlsHeld.classSelection = e.target.value;
      sessionStorage.setItem('classSelection', e.target.value);
      console.log("Selected Titan Class:", e.target.value);
    });
  }

  // Controls Layout dropdown change
  const controlsSelect = document.getElementById('controls-select');
  if (controlsSelect) {
    controlsSelect.addEventListener('change', async (e) => {
      await setControlPreset(e.target.value);
    });
  }
}

let lastNarrationPhase = -1;

function handleTutorialNarration(game) {
  if (!game || !game.gameId || !game.gameId.startsWith('tutorial-')) return;
  const nPhase = game.narrationPhase || 0;
  if (nPhase !== lastNarrationPhase) {
    console.log("Tutorial Narration Phase changed to:", nPhase);
    // Stop all narration audios
    for (let i = 0; i <= 4; i++) {
      const aud = AssetManager.audio['tut' + i];
      if (aud) {
        aud.pause();
        aud.currentTime = 0;
      }
    }
    // Play new narration audio if valid
    if (nPhase > 0 && nPhase <= 5) {
      const newAud = AssetManager.audio['tut' + (nPhase - 1)];
      if (newAud) {
        newAud.play().catch(e => console.warn("Audio play blocked by browser autoplay policy:", e));
      }
    }
    lastNarrationPhase = nPhase;
  }
}

function drawIngame(ctx, dt) {
  const game = gameState.game;
  if (!game) return;
  
  handleTutorialNarration(game);
  updateCamera(game, gameState);
  const { camX, camY } = gameState;
  
  const isGoalie = game.underControl && game.underControl.type === 'GOALIE';
  if (isGoalie) {
    ctx.save();
    ctx.scale(0.9375, 1.0);
  }

  // Draw Field
  if (AssetManager.images['field']) {
    drawImageCam(ctx, AssetManager.images['field'], 1, 1, camX, camY);
  } else {
    ctx.fillStyle = '#0f3d0f';
    ctx.fillRect(0, 0, 1920, 1080);
  }
  
  drawGoals(ctx, game, camX, camY);
  drawAimAndRangeIndicators(ctx, game, gameState.controlsHeld, camX, camY);
  drawPlayers(ctx, game, camX, camY);
  drawMinions(ctx, game, camX, camY);
  drawEffectIcons(ctx, game, camX, camY);
  drawBall(ctx, game, camX, camY);
  displayBallArrow(ctx, game, camX, camY);

  if (isGoalie) {
    ctx.restore();
  }

  drawHud(ctx, game, gameState);

  // Draw Goal Scored Asset Overlay (local detection via score changes)
  if (gameState.localGoalVisible) {
    if (Date.now() - gameState.localGoalTime > 3000) {
      gameState.localGoalVisible = false;
    } else {
      const img = AssetManager.images['goal'];
      let gy = 1080 / 2 - 100;
      if (img && img.width > 0) {
        const gx = 1920 / 2 - img.width / 2;
        gy = 1080 / 2 - img.height / 2 - 100;
        ctx.drawImage(img, gx, gy);
      }
      
      // Draw Text Banner (glowing text)
      ctx.save();
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.font = 'italic bold 70px Impact, Arial, sans-serif';
      
      let txt = "GOAL!";
      let fillStyle = '#3b82f6'; // Vibrant Blue for normal goal
      
      if (gameState.goalComboType === 'DOUBLE') {
        txt = "DOUBLE GOAL!";
        fillStyle = '#ff7f11'; // Bright Orange
      } else if (gameState.goalComboType === 'TRIPLE') {
        txt = "TRIPLE GOAL!";
        fillStyle = '#9d4edd'; // Bright Purple
      } else if (gameState.goalComboType === 'QUAD') {
        txt = "QUAD GOAL!";
        fillStyle = '#ffd700'; // Gold for Quad
      }
      
      const textY = gy + (img && img.height > 0 ? img.height : 100) + 60;
      
      ctx.strokeStyle = 'black';
      ctx.lineWidth = 8;
      ctx.strokeText(txt, 1920 / 2, textY);
      
      ctx.fillStyle = fillStyle;
      ctx.fillText(txt, 1920 / 2, textY);
      ctx.restore();
    }
  }

  // Draw Tutorial Objective Banner Overlay
  if (game.gameId && game.gameId.startsWith('tutorial-')) {
    ctx.save();
    ctx.fillStyle = 'rgba(10, 26, 20, 0.85)';
    ctx.strokeStyle = '#ff7f11';
    ctx.lineWidth = 2;
    ctx.beginPath();
    // roundRect fallback
    if (ctx.roundRect) {
      ctx.roundRect(1920/2 - 250, 40, 500, 80, 12);
    } else {
      ctx.rect(1920/2 - 250, 40, 500, 80);
    }
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = '#ff9f1c';
    ctx.font = 'bold 22px Outfit, Arial, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('TUTORIAL OBJECTIVE', 1920/2, 70);

    ctx.fillStyle = 'white';
    ctx.font = '16px Outfit, Arial, sans-serif';
    let objText = '';
    const tPhase = game.tutorialPhase || 1;
    if (tPhase === 1) objText = 'Catch the bouncing ball to perform a Rebound!';
    else if (tPhase === 2) objText = 'Steal the ball from the enemy Golem!';
    else if (tPhase === 3) objText = 'Use abilities (Q/W/E) to defeat the Golem!';
    else if (tPhase === 4) objText = 'Shoot the ball into the Side Goal!';
    else if (tPhase === 5) objText = 'Score a Goal in the Main Hoop!';
    ctx.fillText(objText, 1920/2, 103);
    ctx.restore();
  }
}

function gameLoop(timestamp) {
  const dt = timestamp - lastTime;
  lastTime = timestamp;

  // Track idle time (4 hours = 14400000 ms)
  const isIdle = gameState.phase === GamePhase.SHOW_GAME_MODES || gameState.phase === GamePhase.ENDED;
  if (isIdle) {
    if (idleStart === null) {
      idleStart = Date.now();
    } else if (Date.now() - idleStart > 14400000) {
      window.warmExpired = true;
    }
  } else {
    if (gameState.phase === GamePhase.INGAME || gameState.phase === GamePhase.COUNTDOWN || gameState.phase === GamePhase.SCORE_FREEZE) {
      idleStart = null;
    }
  }

  if (window.warmExpired) {
    const expiredOverlay = document.getElementById('session-expired-overlay');
    if (expiredOverlay && expiredOverlay.style.display !== 'flex') {
      expiredOverlay.style.display = 'flex';
    }
    requestAnimationFrame(gameLoop);
    return;
  }

  clearScreen(ctx);
  updateOverlays();

  // If already logged in, skip login screen
  if (gameState.phase === GamePhase.CREDITS && sessionStorage.getItem('accessToken')) {
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
      if (gameState.is3v3) {
        drawDraftShowcase(ctx);
      } else {
        ctx.fillStyle = 'white';
        ctx.font = '80px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('PREPARING MATCH...', 1920 / 2, 1080 / 2);
      }
      break;
    case GamePhase.INGAME:
    case GamePhase.SCORE_FREEZE:
      drawIngame(ctx, dt);
      break;
    case GamePhase.ENDED:
      drawGameEnded(ctx);
      break;
    default:
      ctx.fillStyle = 'white';
      ctx.font = '30px Arial';
      ctx.fillText('Phase: ' + gameState.phase, 100, 100);
      break;
  }

  requestAnimationFrame(gameLoop);
}

function jwtDecodeEmail(token) {
  if (!token) return '';
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
    const parsed = JSON.parse(jsonPayload);
    return parsed.sub || '';
  } catch (e) {
    return '';
  }
}

function drawGameEnded(ctx) {
  const game = gameState.game;
  if (!game) {
    ctx.fillStyle = 'white';
    ctx.font = '50px Arial';
    ctx.textAlign = 'center';
    ctx.fillText('Game Over', 1920 / 2, 540);
    return;
  }

  // Determine which team this player was on
  let myTeam = 'HOME';
  if (game.underControl) {
    myTeam = game.underControl.team;
  } else if (game.clients && gameState.token) {
    const email = jwtDecodeEmail(gameState.token);
    const clientDivider = game.clients.find(c => c.email === email);
    if (clientDivider) {
      const selIndex = clientDivider.selection - 1;
      if (game.players && game.players[selIndex]) {
        myTeam = game.players[selIndex].team;
      }
    }
  }

  const team = myTeam === 'HOME' ? game.home : game.away;
  const enemy = myTeam === 'HOME' ? game.away : game.home;
  
  const isDisconnect = game.phase !== 'ENDED';
  
  let resultKey = 'tie';
  if (!isDisconnect && team && enemy) {
    if (team.score > enemy.score) resultKey = 'victory';
    else if (team.score < enemy.score) resultKey = 'defeat';
  }

  if (isDisconnect) {
    ctx.fillStyle = '#ef4444';
    ctx.font = 'bold 70px Arial';
    ctx.textAlign = 'center';
    ctx.fillText('CONNECTION LOST', 1920 / 2, 220);
    
    ctx.fillStyle = '#e2e8f0';
    ctx.font = '24px Arial';
    ctx.fillText('Unexpectedly disconnected from the game server.', 1920 / 2, 300);
  } else {
    // Draw Result Image (victory/defeat/tie)
    const img = AssetManager.images[resultKey];
    if (img) {
      const rx = 1920 / 2 - img.width / 2;
      const ry = 120;
      ctx.drawImage(img, rx, ry);
    } else {
      ctx.fillStyle = 'white';
      ctx.font = 'bold 70px Arial';
      ctx.textAlign = 'center';
      ctx.fillText(resultKey.toUpperCase(), 1920 / 2, 220);
    }
  }

  // Draw Subtitle / Final Score
  ctx.save();
  ctx.textAlign = 'center';
  ctx.font = 'bold 36px Arial';
  ctx.fillStyle = '#ff9f1c';
  const homeScore = game.home ? game.home.score : 0;
  const awayScore = game.away ? game.away.score : 0;
  const scoreStr = `Final Score: HOME ${homeScore} - AWAY ${awayScore}`;
  ctx.fillText(scoreStr, 1920 / 2, 380);
  ctx.restore();

  // Draw Stats Table
  const email = jwtDecodeEmail(gameState.token || sessionStorage.getItem('accessToken'));
  if (email && game.stats && game.stats.gamestats) {
    ctx.save();
    ctx.fillStyle = 'rgba(10, 26, 20, 0.85)';
    ctx.fillRect(1920 / 2 - 300, 420, 600, 460);
    ctx.strokeStyle = '#ff7f11';
    ctx.lineWidth = 3;
    ctx.strokeRect(1920 / 2 - 300, 420, 600, 460);

    ctx.font = 'bold 28px Arial';
    ctx.fillStyle = '#00ff00';
    ctx.textAlign = 'center';
    ctx.fillText('MATCH STATISTICS', 1920 / 2, 460);

    const STAT_NAMES = [
      'GOALS', 'SIDEGOALS', 'POINTS',
      'STEALS', 'BLOCKS', 'PASSES',
      'KILLS', 'DEATHS', 'TURNOVERS',
      'KILLASSISTS', 'GOALASSISTS', 'REBOUND'
    ];

    ctx.font = '22px Courier New';
    let sy = 510;
    
    STAT_NAMES.forEach((name, idx) => {
      const statMap = game.stats.gamestats[idx];
      let val = 0;
      if (statMap && statMap[email] !== undefined) {
        val = statMap[email];
      }
      
      ctx.fillStyle = '#ffffff';
      ctx.textAlign = 'left';
      ctx.fillText(name.padEnd(20, '.'), 1920 / 2 - 200, sy);
      
      ctx.fillStyle = '#ff9f1c';
      ctx.textAlign = 'right';
      ctx.fillText(idx === 2 ? val.toFixed(2) : Math.floor(val).toString(), 1920 / 2 + 200, sy);
      
      sy += 30;
    });

    ctx.restore();
  }

  // Draw return info
  ctx.save();
  ctx.font = 'bold 24px Arial';
  ctx.fillStyle = '#888888';
  ctx.textAlign = 'center';
  ctx.fillText('Press SPACE to return to lobby menu', 1920 / 2, 940);
  ctx.restore();
}

function drawDraftShowcase(ctx) {
  const game = gameState.game;
  if (!game || !game.players) {
    ctx.fillStyle = 'white';
    ctx.font = '80px Arial';
    ctx.textAlign = 'center';
    ctx.fillText("PREPARING MATCH...", 1920 / 2, 1080 / 2);
    return;
  }

  const homePlayers = game.players.filter(p => p.team === 'HOME');
  const awayPlayers = game.players.filter(p => p.team === 'AWAY');

  // Background panel
  ctx.fillStyle = 'rgba(10, 26, 20, 0.9)';
  ctx.fillRect(0, 0, 1920, 1080);
  
  // Draw Center VS
  ctx.save();
  ctx.fillStyle = '#ff7f11';
  ctx.font = 'italic bold 90px Impact, Arial';
  ctx.textAlign = 'center';
  ctx.fillText('VS', 1920 / 2, 1080 / 2 - 50);
  ctx.restore();

  // Roster Columns
  const drawColumn = (players, isHome, startX) => {
    ctx.save();
    ctx.fillStyle = isHome ? 'rgba(59, 130, 246, 0.15)' : 'rgba(239, 68, 68, 0.15)';
    ctx.fillRect(startX, 150, 700, 700);
    ctx.strokeStyle = isHome ? '#3b82f6' : '#ef4444';
    ctx.lineWidth = 4;
    ctx.strokeRect(startX, 150, 700, 700);

    ctx.fillStyle = isHome ? '#3b82f6' : '#ef4444';
    ctx.font = 'bold 40px Arial';
    ctx.textAlign = 'center';
    ctx.fillText(isHome ? 'HOME TEAM DRAFT' : 'AWAY TEAM DRAFT', startX + 350, 210);

    let py = 250;
    
    // Calculate dynamic sizes for cards when player counts are high to prevent overflow
    let cardHeight = 110;
    let spacing = 140;
    let fontSizeText = 24;
    let fontSizeClass = 20;
    let spriteSize = 100;
    let spriteOffset = 5;
    let textOffsetY1 = 45;
    let textOffsetY2 = 80;

    if (players.length > 4) {
      const maxContainerHeight = 550;
      spacing = Math.floor(maxContainerHeight / players.length);
      cardHeight = Math.max(50, Math.floor((maxContainerHeight - 40) / players.length));
      fontSizeText = Math.max(12, Math.floor(cardHeight * 0.22));
      fontSizeClass = Math.max(10, Math.floor(cardHeight * 0.18));
      spriteSize = Math.max(40, cardHeight - 8);
      spriteOffset = Math.floor((cardHeight - spriteSize) / 2);
      textOffsetY1 = Math.floor(cardHeight * 0.4);
      textOffsetY2 = Math.floor(cardHeight * 0.78);
    }

    players.forEach((p, idx) => {
      // Draw card background
      ctx.fillStyle = 'rgba(0, 0, 0, 0.55)';
      ctx.fillRect(startX + 50, py, 600, cardHeight);
      ctx.strokeStyle = isHome ? 'rgba(59, 130, 246, 0.3)' : 'rgba(239, 68, 68, 0.3)';
      ctx.strokeRect(startX + 50, py, 600, cardHeight);

      // Text details
      ctx.fillStyle = '#ffffff';
      ctx.font = `bold ${fontSizeText}px Arial`;
      ctx.textAlign = 'left';
      ctx.fillText(`Slot ${idx + 1}: Player`, startX + 80, py + textOffsetY1);

      ctx.fillStyle = '#ff9f1c';
      ctx.font = `bold ${fontSizeClass}px Courier New`;
      ctx.fillText(`Class: ${p.type}`, startX + 80, py + textOffsetY2);

      // Small stand sprite preview
      const standImg = AssetManager.images[`${p.type}_standR`] || AssetManager.images[`${p.type}_standL`];
      if (standImg) {
        ctx.drawImage(standImg, startX + 490, py + spriteOffset, spriteSize, spriteSize);
      }

      py += spacing;
    });
    ctx.restore();
  };

  drawColumn(homePlayers, true, 150);
  drawColumn(awayPlayers, false, 1070);

  // Banned Overlay
  ctx.save();
  ctx.fillStyle = '#ef4444';
  ctx.font = 'bold 24px Courier New';
  ctx.textAlign = 'center';
  ctx.fillText('TACTICAL BANS: [STEALTH] banned by HOME | [GOLEM] banned by AWAY', 1920 / 2, 920);
  ctx.restore();

  // Match Start timer
  ctx.save();
  ctx.fillStyle = '#00ff00';
  ctx.font = 'bold 36px Arial';
  ctx.textAlign = 'center';
  const sec = game.secondsToStart !== undefined ? Math.max(0, Math.ceil(game.secondsToStart)) : 5;
  ctx.fillText(`MATCH STARTING IN: ${sec}s`, 1920 / 2, 990);
  ctx.restore();
}

export function start() {
  ctx = initCanvas();
  initAssets();
  initMasteries();
  initKeyboard();
  initMouse();
  initUIListeners();
  
  // Warm the pilot-light server immediately on startup and keep warm every 10 minutes
  warmServer();
  setInterval(warmServer, 600000);

  requestAnimationFrame(gameLoop);
}

start();