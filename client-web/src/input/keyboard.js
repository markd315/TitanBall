import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';
import { returnToMainMenu } from '../main.js';
import {
    TREE_SHORT_NAME, NODE_DEFS,
    getNodeDef, getNodeConfigKey, isNodeUnlocked, getTreeState,
    isBuildOrderManaNode, HARDCODED_COSTS
} from '../render/hud.js';

export let currentConfig = {};
export const actionMap = {
  'UP': 'UP',
  'DOWN': 'DOWN',
  'LEFT': 'LEFT',
  'RIGHT': 'RIGHT',
  'E': 'E',
  'R': 'R',
  'CAM': 'CAM',
  'STEAL': 'STEAL',
  'SWITCH': 'SWITCH',
  'BOOST': 'BOOST',
  'BOOST_LOCK': 'BOOST_LOCK',
  'LOB': 'lobBtn',
  'SHOT': 'shotBtn',
  'MV_CLICK': 'MV_CLICK',
  'MV_BALL': 'MV_BALL',
  'BUILD_NEXT': 'BUILD_NEXT'
};

export function getKeysForAction(actionName) {
  const keys = [];
  for (const [key, act] of Object.entries(currentConfig)) {
    if (act === actionName && !['Xres', 'Yres', 'SCALE', 'theme', 'rangewidth', 'shotwidth'].includes(key)) {
      if (!isNaN(key) && key.length > 1) continue;
      keys.push(key);
    }
  }
  return keys.length > 0 ? keys.join(' / ') : actionName;
}

export async function setControlPreset(preset) {
  try {
    let url = 'res/ctrls_example_rts.json';
    if (preset === 'keyboard' || preset === 'mobile-single' || preset === 'mobile-double') {
      url = 'res/ctrls_example_3_pers_shooter.json';
    }
    const response = await fetch(url);
    const data = await response.json();
    currentConfig = data;
    sessionStorage.setItem('controlPreset', preset);
  } catch (e) {
    console.error("Failed to load control configuration:", e);
  }
}

export function getDefaultPreset() {
  const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) || ('ontouchstart' in window) || (navigator.maxTouchPoints > 0);
  return isMobile ? 'mobile-double' : 'rts';
}

export async function initControlConfig() {
  const defaultPreset = getDefaultPreset();
  const saved = sessionStorage.getItem('controlPreset') || defaultPreset;
  await setControlPreset(saved);
  // Set UI dropdown value if present
  const select = document.getElementById('controls-select');
  if (select) {
    select.value = saved;
  }
}

function getActionForKey(e) {
  // Check key code (e.g. "32", "37" etc.)
  const codeStr = String(e.keyCode);
  if (currentConfig[codeStr]) {
    return currentConfig[codeStr];
  }
  
  // Check uppercase key (e.g. "Q", "W")
  const keyStr = e.key.toUpperCase();
  if (currentConfig[keyStr]) {
    return currentConfig[keyStr];
  }
  
  // Fallbacks for default movement keys if NOT overridden in currentConfig
  if (!currentConfig['W'] && !currentConfig['KeyW']) {
    if (e.code === 'KeyW' || e.code === 'ArrowUp') return 'UP';
  }
  if (!currentConfig['S'] && !currentConfig['KeyS']) {
    if (e.code === 'KeyS' || e.code === 'ArrowDown') return 'DOWN';
  }
  if (!currentConfig['A'] && !currentConfig['KeyA']) {
    if (e.code === 'KeyA' || e.code === 'ArrowLeft') return 'LEFT';
  }
  if (!currentConfig['D'] && !currentConfig['KeyD']) {
    if (e.code === 'KeyD' || e.code === 'ArrowRight') return 'RIGHT';
  }
  
  // Space bar maps to CAM for lock toggle if not mapped
  if (e.code === 'Space' && !currentConfig['32']) {
    return 'CAM';
  }
  
  return null;
}

export function initKeyboard() {
  // Load config first
  initControlConfig();

  window.addEventListener('keydown', (e) => {
    const isGameEnded = gameState.phase === GamePhase.ENDED ||
                        gameState.phase === 'ENDED' ||
                        (gameState.game && (gameState.game.ended || gameState.game.phase === 'ENDED'));

    if (isGameEnded) {
      if (e.code === 'Space' || e.key === ' ' || e.key === 'Spacebar' || e.keyCode === 32 || e.code === 'Enter' || e.key === 'Enter' || e.keyCode === 13) {
        e.preventDefault();
        e.stopPropagation();
        returnToMainMenu();
        return;
      }
    }

    // If typing in an input field, do not capture/prevent default controls
    if (document.activeElement && document.activeElement.tagName === 'INPUT') {
      return;
    }

    // Tab key — Show Class Stats & Ability Guide HUD
    if (e.key === 'Tab' || e.code === 'Tab') {
      gameState.controlsHeld.TAB = true;
      e.preventDefault();
      return;
    }

    // X key — execute next build order upgrade (Goalie, in-game)
    if (e.key === 'x' || e.key === 'X') {
      const game = gameState.game;
      if (
        game &&
        game.underControl &&
        game.underControl.type === 'GOALIE' &&
        (gameState.phase === GamePhase.INGAME || gameState.phase === GamePhase.SCORE_FREEZE)
      ) {
        executeNextBuildOrder(game);
        e.preventDefault();
        return;
      }
    }

    // Menu navigation on Space or Enter
    if (e.code === 'Space' || e.code === 'Enter' || e.key === ' ' || e.key === 'Spacebar' || e.keyCode === 32 || e.keyCode === 13) {
      if (gameState.phase === GamePhase.CREDITS || gameState.phase === 'CREDITS') {
        gameState.phase = GamePhase.CONTROLS;
        e.preventDefault();
        return;
      } else if (gameState.phase === GamePhase.CONTROLS || gameState.phase === 'CONTROLS') {
        gameState.phase = GamePhase.SHOW_GAME_MODES;
        e.preventDefault();
        return;
      }
    }

    // Physical 'E' key — Call for Ball
    if (e.key === 'e' || e.key === 'E' || e.code === 'KeyE') {
      gameState.controlsHeld.callForBall = true;
    }

    // Arrow keys — Pan camera when camera lock is disabled
    if (!gameState.camFollow) {
      const panStep = 30;
      if (e.key === 'ArrowLeft' || e.code === 'ArrowLeft') {
        gameState.camX = Math.max(0, (gameState.camX || 0) - panStep);
        e.preventDefault();
        return;
      } else if (e.key === 'ArrowRight' || e.code === 'ArrowRight') {
        gameState.camX = Math.min(188, (gameState.camX || 0) + panStep);
        e.preventDefault();
        return;
      } else if (e.key === 'ArrowUp' || e.code === 'ArrowUp') {
        gameState.camY = Math.max(0, (gameState.camY || 130) - panStep);
        e.preventDefault();
        return;
      } else if (e.key === 'ArrowDown' || e.code === 'ArrowDown') {
        gameState.camY = Math.min(260, (gameState.camY || 130) + panStep);
        e.preventDefault();
        return;
      }
    }

    const action = getActionForKey(e);
    if (action && actionMap[action]) {
      const field = actionMap[action];
      
      // If action is LOB or SHOT, only allow holding the button if we actually have possession (or goalie)
      if (field === 'lobBtn' || field === 'shotBtn') {
        const game = gameState.game;
        const myTitan = game?.underControl;
        if (myTitan && myTitan.type !== 'GOALIE' && myTitan.possession !== 1) {
          e.preventDefault();
          return;
        }
      }

      if (field === 'E' && !gameState.controlsHeld.E) {
        const game = gameState.game;
        if (game && game.underControl) {
          const t = game.underControl;
          if (t.type === 'ARTISAN' && t.possession === 1) {
            const current = gameState.controlsHeld.artisanShot || 'SHOT';
            let next = 'LEFT';
            if (current === 'LEFT') next = 'RIGHT';
            else if (current === 'RIGHT') next = 'SHOT';
            gameState.controlsHeld.artisanShot = next;
          }
        }
      }
      
      gameState.controlsHeld[field] = true;
      
      if (field === 'CAM') {
        // Toggle camera lock behavior
        gameState.camFollow = !gameState.camFollow;
      }
      e.preventDefault();
    }
  });

  window.addEventListener('keyup', (e) => {
    // If typing in an input field, do not capture/prevent default controls
    if (e.key === 'Tab' || e.code === 'Tab') {
      gameState.controlsHeld.TAB = false;
      e.preventDefault();
      return;
    }

    if (e.key === 'e' || e.key === 'E' || e.code === 'KeyE') {
      gameState.controlsHeld.callForBall = false;
    }

    const action = getActionForKey(e);
    if (action && actionMap[action]) {
      const field = actionMap[action];
      gameState.controlsHeld[field] = false;
      e.preventDefault();
    }
  });
}

// ─── build order execution ────────────────────────────────────────────────────
export function executeNextBuildOrder(game) {
  const order = gameState.buildOrder;
  if (!order || order.length === 0) {
    return;
  }

  const team = game.underControl.team;
  const isHome = team === 'HOME';
  const goldAmt = Math.floor(isHome ? (game.homeGoalieCurrency || 0) : (game.awayGoalieCurrency || 0));
  const manaAmt = Math.floor(isHome ? (game.homeGoalieMana    || 0) : (game.awayGoalieMana    || 0));

  let candidateGold = null;
  let candidateMana = null;

  // 1. Find next unpurchased Gold candidate
  for (let idx = 0; idx < order.length; idx++) {
    const item = order[idx];
    if (isBuildOrderManaNode(order, item)) continue;
    const activeKey = item.tree;
    const defs      = NODE_DEFS[activeKey];
    const shortName = TREE_SHORT_NAME[activeKey];
    if (!defs || !shortName) continue;

    let nodeIdx = -1;
    for (let j = 0; j < defs.length; j++) {
      if (`${shortName}.${defs[j].tier}.${defs[j].name}` === item.nodeKey) {
        nodeIdx = j; break;
      }
    }
    if (nodeIdx === -1) continue;

    const def = getNodeDef(activeKey, nodeIdx);
    if (!def) continue;

    const treeState = getTreeState(game, team, activeKey);
    const resolvedKey = getNodeConfigKey(activeKey, nodeIdx, treeState.purchased);
    const isPurchased = def.kind === 'cost' && treeState.purchased.has(resolvedKey);

    if (isPurchased) continue;

    const unlocked = isNodeUnlocked(activeKey, nodeIdx, treeState);
    if (!unlocked) {
      break; // blocked by prerequisites in gold track
    }

    const costData = HARDCODED_COSTS[resolvedKey] || HARDCODED_COSTS[item.nodeKey] || {};
    const amount = costData.use !== undefined ? costData.use : (costData.cost !== undefined ? costData.cost : 0);
    const affordable = amount > 0 && goldAmt >= amount;
    candidateGold = { item, resolvedKey, index: idx, affordable };
    break;
  }

  // 2. Find next unpurchased Mana candidate
  for (let idx = 0; idx < order.length; idx++) {
    const item = order[idx];
    if (!isBuildOrderManaNode(order, item)) continue;
    const activeKey = item.tree;
    const defs      = NODE_DEFS[activeKey];
    const shortName = TREE_SHORT_NAME[activeKey];
    if (!defs || !shortName) continue;

    let nodeIdx = -1;
    for (let j = 0; j < defs.length; j++) {
      if (`${shortName}.${defs[j].tier}.${defs[j].name}` === item.nodeKey) {
        nodeIdx = j; break;
      }
    }
    if (nodeIdx === -1) continue;

    const def = getNodeDef(activeKey, nodeIdx);
    if (!def) continue;

    const treeState = getTreeState(game, team, activeKey);
    const resolvedKey = getNodeConfigKey(activeKey, nodeIdx, treeState.purchased);
    const isPurchased = def.kind === 'cost' && treeState.purchased.has(resolvedKey);

    if (isPurchased) continue;

    const isPollinated = treeState.purchased.has('cultivation.t5.manapollinate') && def.tier === 't5' && activeKey !== 'GOALIE_TREE_CULTIVATION';
    const unlocked = isPollinated || isNodeUnlocked(activeKey, nodeIdx, treeState);
    if (!unlocked) {
      break; // blocked by prerequisites in mana track
    }

    const costData = HARDCODED_COSTS[resolvedKey] || HARDCODED_COSTS[item.nodeKey] || {};
    const amount = costData.use !== undefined ? costData.use : (costData.cost !== undefined ? costData.cost : 0);
    const affordable = amount > 0 && manaAmt >= amount;
    candidateMana = { item, resolvedKey, index: idx, affordable };
    break;
  }

  // 3. Choose candidate to purchase
  let chosen = null;
  if (candidateGold && candidateGold.affordable && candidateMana && candidateMana.affordable) {
    // Both affordable: pick whichever appeared earlier in the build order
    chosen = candidateGold.index <= candidateMana.index ? candidateGold : candidateMana;
  } else if (candidateGold && candidateGold.affordable) {
    chosen = candidateGold;
  } else if (candidateMana && candidateMana.affordable) {
    chosen = candidateMana;
  }

  if (chosen) {
    gameState.pendingGoalieBuy = { tree: chosen.item.tree, nodeKey: chosen.resolvedKey };
    return;
  }

  // 4. Fallback: if build order exhausted or both blocked, repeat the last affordable 'use' node
  for (let j = order.length - 1; j >= 0; j--) {
    const item      = order[j];
    const activeKey = item.tree;
    const defs      = NODE_DEFS[activeKey];
    const shortName = TREE_SHORT_NAME[activeKey];
    if (!defs || !shortName) continue;

    const def = defs.find(d => `${shortName}.${d.tier}.${d.name}` === item.nodeKey);
    if (!def || def.kind !== 'use') continue;

    const treeState = getTreeState(game, team, activeKey);
    const nodeIdx   = defs.indexOf(def);
    if (!isNodeUnlocked(activeKey, nodeIdx, treeState)) continue;

    const resolvedKey = getNodeConfigKey(activeKey, nodeIdx, treeState.purchased);
    const costData = HARDCODED_COSTS[resolvedKey] || HARDCODED_COSTS[item.nodeKey] || {};
    const amount = costData.use !== undefined ? costData.use : (costData.cost !== undefined ? costData.cost : 0);
    const isMana = isBuildOrderManaNode(order, item);
    const balance = isMana ? manaAmt : goldAmt;
    if (amount > 0 && balance >= amount) {
      gameState.pendingGoalieBuy = { tree: activeKey, nodeKey: resolvedKey };
      return;
    }
  }
}