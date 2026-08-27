import { gameState, clientUI } from '../state.js';
import { currentConfig, actionMap } from './keyboard.js';
import { CONSTANTS } from '../constants.js';
import { AssetManager } from '../assets/sprites.js';
import {
    TREE_NODES, ANALYSIS_IMG_WIDTH, ANALYSIS_IMG_HEIGHT,
    tabKeys, tabCount, tabWidth, tabHeight, spacing,
    getNodeDef, isNodeUnlocked, getTreeState, getNodeConfigKey
} from '../render/hud.js';

// Hit-tests a click against the currently open tree image and, if it
// landed on a buyable node, queues a purchase/use request. Returns true
// if the click was consumed by the tree (whether or not it hit a node),
// so it never falls through to a gameplay shot/lob click.
function handleNodeClick(game, activeKey, mx, my) {
    const activeImg = AssetManager.images[activeKey];
    const nodes = TREE_NODES[activeKey];
    if (!activeImg || !nodes) return false;

    const ox = (CONSTANTS.X_RES - activeImg.width) / 2;
    const oy = (CONSTANTS.Y_RES - activeImg.height) / 2;

    const inside = mx >= ox && mx <= ox + activeImg.width && my >= oy && my <= oy + activeImg.height;
    if (!inside) return false; // let the caller decide whether this closes the menu

    const scaleX = activeImg.width / ANALYSIS_IMG_WIDTH;
    const scaleY = activeImg.height / ANALYSIS_IMG_HEIGHT;

    let hitIdx = -1;
    for (let idx = 0; idx < nodes.length; idx++) {
        const [x1, y1, x2, y2] = nodes[idx];
        const boxX = ox + (x1 * scaleX);
        const boxY = oy + (y1 * scaleY);
        const boxW = (x2 - x1) * scaleX;
        const boxH = (y2 - y1) * scaleY;
        if (mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + boxH) {
            hitIdx = idx;
            break;
        }
    }
    if (hitIdx === -1) return true; // inside the image but not on any node - consume, do nothing

    const def = getNodeDef(activeKey, hitIdx);
    if (!def) return true; // no definition for this box - consume, do nothing

    const treeState = getTreeState(game, game.underControl.team, activeKey);
    const unlocked = isNodeUnlocked(activeKey, hitIdx, treeState);
    const nodeKey = getNodeConfigKey(activeKey, hitIdx, treeState.purchased);
    const purchased = def.kind === 'cost' && treeState.purchased.has(nodeKey);

    if (!unlocked) {
        return true;
    }
    if (purchased) {
        return true;
    }

    // Client makes no decision about cost, currency, or legality here -
    // it only names which node was clicked. Whether this is a one-time
    // unlock ('cost') or a repeatable charge ('use'), and which currency
    // it draws from, is resolved server-side in
    // Game.handleGoalieTreePurchase, gated by the CHECK_BALANCE feature
    // toggle in costing.cfg.
    gameState.pendingGoalieBuy = { tree: activeKey, nodeKey };
    return true;
}

export function handleUIClick(mx, my) {
    const game = gameState.game;
    if (!game || !game.underControl || game.underControl.type !== 'GOALIE') return false;

    const totalWidth = tabCount * tabWidth + (tabCount - 1) * spacing;
    const startX = (CONSTANTS.X_RES - totalWidth) / 2;
    const y = CONSTANTS.Y_RES - 60; // must match drawHud's tab y exactly

    // Click on a tab
    for (let i = 0; i < tabCount; i++) {
        const x = startX + i * (tabWidth + spacing);
        if (mx >= x && mx <= x + tabWidth && my >= y && my <= y + tabHeight) {
            clientUI.goalieTabIndex = i;
            gameState.uiClick = true;
            return true;
        }
    }

    // A tree is open - either it's a node click (buy/use/locked/purchased)
    // or a click outside the image, which closes the menu.
    if (clientUI.goalieTabIndex >= 0 && clientUI.goalieTabIndex < tabCount) {
        const selectedKey = tabKeys[clientUI.goalieTabIndex];

        if (handleNodeClick(game, selectedKey, mx, my)) {
            gameState.uiClick = true;
            return true;
        }

        // Click was outside the tree image - close the menu.
        clientUI.goalieTabIndex = -1;
        gameState.uiClick = true;
        return true;
    }

    return false;
}

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
        // First: UI click check
        if (handleUIClick(gameState.mouseX, gameState.mouseY)) {
            // UI consumed the click
            return;
        }
        // Otherwise: gameplay click
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