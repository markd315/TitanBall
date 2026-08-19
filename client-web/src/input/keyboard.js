import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';
import {
    TREE_SHORT_NAME, NODE_DEFS,
    getNodeDef, getNodeConfigKey, isNodeUnlocked, getTreeState
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

export async function setControlPreset(preset) {
  try {
    let url = 'res/ctrls_example_rts.json';
    if (preset === 'keyboard' || preset === 'mobile-single' || preset === 'mobile-double') {
      url = 'res/ctrls_example_3_pers_shooter.json';
    }
    const response = await fetch(url);
    const data = await response.json();
    currentConfig = data;
    localStorage.setItem('controlPreset', preset);
    console.log("Loaded control layout configuration:", preset, currentConfig);
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
  const saved = localStorage.getItem('controlPreset') || defaultPreset;
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
    // If typing in an input field, do not capture/prevent default controls
    if (document.activeElement && document.activeElement.tagName === 'INPUT') {
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
        _executeNextBuildOrder(game);
        e.preventDefault();
        return;
      }
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
      } else if (gameState.phase === GamePhase.ENDED) {
        window.location.reload();
        e.preventDefault();
        return;
      }
    }

    const action = getActionForKey(e);
    if (action && actionMap[action]) {
      const field = actionMap[action];
      
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
            console.log(`Artisan shot mode cycled to: ${next}`);
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
    if (document.activeElement && document.activeElement.tagName === 'INPUT') {
      return;
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
function _executeNextBuildOrder(game) {
  const order = gameState.buildOrder;
  if (!order || order.length === 0) {
    console.log('[Build Order] No build order configured.');
    return;
  }

  const team = game.underControl.team;
  let i = gameState.buildOrderIndex || 0;

  while (i < order.length) {
    const item      = order[i];
    const activeKey = item.tree;
    const targetKey = item.nodeKey;   // base key stored in planner

    const treeState = getTreeState(game, team, activeKey);
    const defs      = NODE_DEFS[activeKey];
    const shortName = TREE_SHORT_NAME[activeKey];

    if (!defs || !shortName) { i++; continue; }

    // Find the node index by matching the stored base key
    let nodeIdx = -1;
    for (let j = 0; j < defs.length; j++) {
      if (`${shortName}.${defs[j].tier}.${defs[j].name}` === targetKey) {
        nodeIdx = j; break;
      }
    }
    if (nodeIdx === -1) { i++; continue; }

    const def         = getNodeDef(activeKey, nodeIdx);
    if (!def) { i++; continue; }

    // Resolve actual key (handles focusedtraining → focusedtraining2)
    const resolvedKey = getNodeConfigKey(activeKey, nodeIdx, treeState.purchased);
    const purchased   = def.kind === 'cost' && treeState.purchased.has(resolvedKey);
    const unlocked    = isNodeUnlocked(activeKey, nodeIdx, treeState);

    if (purchased) {
      // Already bought — skip this slot permanently
      i++;
      gameState.buildOrderIndex = i;
      continue;
    }

    if (!unlocked) {
      // Tier prerequisites not yet met — stop and tell the player
      console.log(`[Build Order] Step ${i + 1} "${resolvedKey}" is locked — tier requirements not met yet.`);
      break;
    }

    // Fire the purchase via the standard pending-buy pipe
    gameState.pendingGoalieBuy  = { tree: activeKey, nodeKey: resolvedKey };
    gameState.buildOrderIndex   = i + 1;
    console.log(`[Build Order] Executing step ${i + 1}: ${resolvedKey}`);
    return;
  }

  // Build order exhausted (or blocked by locks) — repeat the last 'use' node in the list.
  // Scan backward; no state changes, lists are short.
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
    gameState.pendingGoalieBuy = { tree: activeKey, nodeKey: resolvedKey };
    console.log(`[Build Order] Repeating last use node: ${resolvedKey}`);
    return;
  }

  console.log('[Build Order] Build order complete — no repeatable use node found.');
}