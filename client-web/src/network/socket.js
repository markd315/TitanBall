import { gameState } from '../state.js';

let socket = null;
let updateInterval = null;
let pingInterval = null;
let _diagMsgCount = 0;
let _diagSendCount = 0;
let _diagLastPhase = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 20;

export function connectGame(gameID) {
  if (socket) {
    socket.close();
  }

  const token = sessionStorage.getItem('accessToken');
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const url = `${protocol}//${window.location.host}/pages/titanball/game`;
  
  console.log("Connecting to WebSocket:", url);
  socket = new WebSocket(url);
  
  socket.onopen = () => {
    console.log("WebSocket connected");
    reconnectAttempts = 0;
    // Reset build order pointer for fresh game
    gameState.buildOrderIndex = 0;
    // Start sending control inputs

    updateInterval = setInterval(() => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        const isGoalie = gameState.game && gameState.game.underControl && gameState.game.underControl.type === 'GOALIE';
        const posX = isGoalie ? Math.floor((gameState.mouseX || 0) / 0.9375) : (gameState.mouseX || 0);
        const posY = gameState.mouseY || 0;
        
        if (gameState.game && gameState.game.underControl && gameState.game.underControl.possession !== 1) {
          gameState.controlsHeld.artisanShot = 'SHOT';
        }

        const controls = {
          ...gameState.controlsHeld,
          token: token,
          gameID: gameID,
          camX: gameState.camX || 0,
          camY: gameState.camY || 0,
          posX: posX,
          posY: posY
        };

        if (gameState.pendingGoalieBuy) {
          controls.buyGoalieTree = gameState.pendingGoalieBuy.tree;
          controls.buyGoalieNode = gameState.pendingGoalieBuy.nodeKey;
          gameState.pendingGoalieBuy = null;
        } else {
          controls.buyGoalieTree = null;
          controls.buyGoalieNode = null;
        }

        _diagSendCount++;
        if (_diagSendCount <= 3) {
          console.log(`[DIAG] WS send #${_diagSendCount}: classSelection='${controls.classSelection}' masteries=`, controls.masteries);
        }
        socket.send(JSON.stringify(controls));
      }
    }, 15);

    // Start periodic ping loop for latency tracking
    //pingInterval = setInterval(() => {
    //  if (socket && socket.readyState === WebSocket.OPEN) {
     //   socket.send(JSON.stringify({ type: 'ping', sent: Date.now() }));
     // }
    //}, 2000);
  };
  
  socket.onmessage = (event) => {
    const update = JSON.parse(event.data);
    //if (update.type === 'pong') {
    //  const latency = Date.now() - update.sent;
    //  console.log(`[PING LOG] Round-Trip Latency: ${latency}ms`);
    //  return;
    //}
    _diagMsgCount++;
    
    // Log the first 3 messages in detail and any phase transition
    if (_diagMsgCount <= 3) {
      console.log(`[DIAG] WS msg #${_diagMsgCount} phase='${update.phase}' players=`, update.players
        ? update.players.map(p => ({ type: p.type, team: p.team, X: p.X, Y: p.Y, action: p.actionState }))
        : 'null'
      );
    }
    if (update.players) {
      const nullTypes = update.players.filter(p => p.type == null);
      if (nullTypes.length > 0 && _diagMsgCount <= 10) {
        console.warn(`[DIAG] WS msg #${_diagMsgCount}: ${nullTypes.length} player(s) with null type:`,
          nullTypes.map(p => ({ id: p.id, team: p.team, entityClass: p.entityClass })));
      }
    }
    if (update.phase !== _diagLastPhase) {
      console.log(`[DIAG] Phase transition: '${_diagLastPhase}' => '${update.phase}'`);
      if (update.players) {
        console.log('[DIAG] Players on phase change:', update.players.map(p => ({ id: p.id, type: p.type, team: p.team })));
      }
      _diagLastPhase = update.phase;
    }
    
    gameState.game = update;
    if (update.phase) {
      gameState.phase = update.phase;
    }

    // Combo goal detection (local detection via score changes to bypass server sleep/ticks block)
    if (update.home && update.away && gameState.prevHomeScore !== undefined && gameState.prevAwayScore !== undefined) {
      const prevHomeFloored = Math.floor(gameState.prevHomeScore);
      const prevAwayFloored = Math.floor(gameState.prevAwayScore);
      
      const newHomeFloored = Math.floor(update.home.score);
      const newAwayFloored = Math.floor(update.away.score);
      
      const homeDiff = newHomeFloored - prevHomeFloored;
      const awayDiff = newAwayFloored - prevAwayFloored;
      const diff = Math.max(homeDiff, awayDiff);
      
      if (diff > 0) {
        gameState.localGoalVisible = true;
        gameState.localGoalTime = Date.now();
        
        if (diff === 2) gameState.goalComboType = 'DOUBLE';
        else if (diff === 3) gameState.goalComboType = 'TRIPLE';
        else if (diff === 4) gameState.goalComboType = 'QUAD';
        else gameState.goalComboType = 'NORMAL';
      }
    }
    
    gameState.prevHomeScore = update.home ? update.home.score : 0;
    gameState.prevAwayScore = update.away ? update.away.score : 0;
  };
  
  socket.onclose = () => {
    console.log("WebSocket closed");
    if (updateInterval) {
      clearInterval(updateInterval);
      updateInterval = null;
    }
    //if (pingInterval) {
    //  clearInterval(pingInterval);
    //  pingInterval = null;
    //}
    
    const wasIngame = (gameState.phase === 'INGAME' || gameState.phase === 'SCORE_FREEZE');
    if (wasIngame && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
      reconnectAttempts++;
      console.log(`WebSocket disconnected mid-game. Attempting reconnect ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS} in 500ms...`);
      setTimeout(() => {
        connectGame(gameID);
      }, 500);
    } else {
      if (wasIngame) {
        gameState.phase = 'ENDED';
      }
      reconnectAttempts = 0;
    }
  };
  
  socket.onerror = (error) => {
    console.error("WebSocket error:", error);
  };
}

export function disconnectGame() {
  reconnectAttempts = MAX_RECONNECT_ATTEMPTS; // Prevent reconnect on manual disconnect
  if (socket) {
    socket.close();
    socket = null;
  }
  if (updateInterval) {
    clearInterval(updateInterval);
    updateInterval = null;
  }
  if (pingInterval) {
    clearInterval(pingInterval);
    pingInterval = null;
  }
}