import { gameState } from '../state.js';
import { AssetManager } from '../assets/sprites.js';
import {
    TREE_NODES, NODE_DEFS, ANALYSIS_IMG_WIDTH, ANALYSIS_IMG_HEIGHT,
    tabNames, tabColors, tabKeys,
    isNodeUnlocked, getNodeDef, getNodeConfigKey,
    HARDCODED_COSTS, ABILITY_TOOLTIPS, TREE_SHORT_NAME
} from '../render/hud.js';

// ─── module-level state ───────────────────────────────────────────────────────
let plannerActiveTabIndex = 0;
let localBuildOrder = [];          // working copy while the modal is open
let plannerCanvas = null;
let plannerCtx = null;
let hoveredNodeIdx = -1;

// ─── public API ───────────────────────────────────────────────────────────────
export function initBuildOrderPlanner() {
    _loadBuildOrder();

    const planBtn    = document.getElementById('plan-build-btn');
    const modal      = document.getElementById('build-order-modal');
    const modeOverlay = document.getElementById('mode-overlay');

    if (!planBtn || !modal) return;

    // Open the modal
    planBtn.addEventListener('click', () => {
        localBuildOrder = gameState.buildOrder.map(item => ({ ...item }));
        plannerActiveTabIndex = 0;
        hoveredNodeIdx = -1;
        modal.style.display = 'flex';
        if (modeOverlay) modeOverlay.style.pointerEvents = 'none';
        // slight delay so canvas has final dimensions after flex layout
        requestAnimationFrame(() => _renderAll());
    });

    // Tree tabs
    for (let i = 0; i < tabNames.length; i++) {
        const btn = document.getElementById(`planner-tab-${i}`);
        if (btn) {
            btn.addEventListener('click', () => {
                plannerActiveTabIndex = i;
                hoveredNodeIdx = -1;
                _renderTabs();
                _renderTree();
            });
        }
    }

    // Save
    const saveBtn = document.getElementById('build-order-save-btn');
    if (saveBtn) {
        saveBtn.addEventListener('click', () => {
            gameState.buildOrder = localBuildOrder.map(item => ({ ...item }));
            gameState.buildOrderIndex = 0;
            _saveBuildOrder();
            _closeModal();
        });
    }

    // Cancel / close buttons
    ['build-order-cancel-btn', 'build-order-close-btn'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.addEventListener('click', _closeModal);
    });

    // Clear
    const clearBtn = document.getElementById('build-order-clear-btn');
    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            localBuildOrder = [];
            _renderAll();
        });
    }

    // Export — copy build order to clipboard as newline-separated node keys
    const exportBtn = document.getElementById('build-order-export-btn');
    if (exportBtn) {
        exportBtn.addEventListener('click', () => {
            if (localBuildOrder.length === 0) {
                _flashBtn(exportBtn, 'Nothing to export');
                return;
            }
            const text = localBuildOrder.map(item => item.nodeKey).join('\n');
            navigator.clipboard.writeText(text).then(() => {
                _flashBtn(exportBtn, '✓ Copied!');
            }).catch(() => {
                // Fallback: prompt with text
                window.prompt('Copy the build order below:', text);
            });
        });
    }

    // Paste — read clipboard and import build order
    const pasteBtn = document.getElementById('build-order-paste-btn');
    if (pasteBtn) {
        pasteBtn.addEventListener('click', () => {
            navigator.clipboard.readText().then(text => {
                const imported = _parseBuildOrderText(text);
                if (imported.length === 0) {
                    _flashBtn(pasteBtn, 'Nothing valid found');
                    return;
                }
                localBuildOrder = imported;
                _renderAll();
                _flashBtn(pasteBtn, `✓ ${imported.length} steps loaded`);
            }).catch(() => {
                // Clipboard API not available — use prompt fallback
                const text = window.prompt('Paste your build order (one node key per line):');
                if (!text) return;
                const imported = _parseBuildOrderText(text);
                localBuildOrder = imported;
                _renderAll();
                _flashBtn(pasteBtn, `✓ ${imported.length} steps loaded`);
            });
        });
    }

    // Canvas
    plannerCanvas = document.getElementById('planner-canvas');
    if (plannerCanvas) {
        plannerCtx = plannerCanvas.getContext('2d');
        plannerCanvas.addEventListener('click', _handleCanvasClick);
        plannerCanvas.addEventListener('mousemove', _handleCanvasHover);
        plannerCanvas.addEventListener('mouseleave', () => {
            hoveredNodeIdx = -1;
            _renderTree();
        });
    }

    function _closeModal() {
        modal.style.display = 'none';
        if (modeOverlay) modeOverlay.style.pointerEvents = 'auto';
    }
}

// Called by main.js whenever class-select changes
export function updatePlanBuildButtonVisibility() {
    const classSelect = document.getElementById('class-select');
    const container   = document.getElementById('plan-build-btn-container');
    if (!container || !classSelect) return;
    container.style.display = classSelect.value === 'GOALIE' ? '' : 'none';
}

// ─── persistence ─────────────────────────────────────────────────────────────
function _loadBuildOrder() {
    try {
        const saved = sessionStorage.getItem('goalieBuildOrder');
        if (saved) gameState.buildOrder = JSON.parse(saved);
    } catch (e) {
        gameState.buildOrder = [];
    }
    gameState.buildOrderIndex = 0;
}

function _saveBuildOrder() {
    sessionStorage.setItem('goalieBuildOrder', JSON.stringify(gameState.buildOrder));
}

// ─── simulated tree state (based on local build order, not server state) ─────
// `purchased` is GLOBAL (all trees) so canPollinateT5 can see cultivation.t5.manapollinate
// when rendering any non-cultivation tree.
// `tierProgress` is LOCAL to activeKey so tierUnlocked math stays per-tree.
function _getSimulatedTreeState(activeKey) {
    const purchased    = new Set();
    const tierProgress = {};

    for (const item of localBuildOrder) {
        const itemShortName = TREE_SHORT_NAME[item.tree];
        const defs = NODE_DEFS[item.tree];
        if (!defs || !itemShortName) continue;
        const def = defs.find(d => `${itemShortName}.${d.tier}.${d.name}` === item.nodeKey);
        if (!def || def.kind !== 'cost') continue;

        purchased.add(item.nodeKey); // global

        if (item.tree === activeKey) {
            tierProgress[def.tier] = (tierProgress[def.tier] || 0) + 1;
        }
    }
    return { purchased, tierProgress };
}

// Returns true when no more instances of this node should be added to the plan.
// For most cost nodes: max 1. For focusedtraining: max 2 (resolves to focusedtraining2).
// For 'use' nodes: never fully queued.
function _isFullyQueued(activeKey, def) {
    if (def.kind === 'use') return false;
    const shortName = TREE_SHORT_NAME[activeKey];
    const baseKey   = `${shortName}.${def.tier}.${def.name}`;
    const count     = localBuildOrder.filter(item => item.nodeKey === baseKey && item.tree === activeKey).length;
    const maxAllowed = (def.name === 'focusedtraining' && activeKey === 'GOALIE_TREE_EMPOWERMENT') ? 2 : 1;
    return count >= maxAllowed;
}

// ─── rendering ───────────────────────────────────────────────────────────────
function _renderAll() {
    _renderTabs();
    _renderTree();
    _renderList();
    _updateCount();
}

function _renderTabs() {
    for (let i = 0; i < tabNames.length; i++) {
        const btn = document.getElementById(`planner-tab-${i}`);
        if (!btn) continue;
        const active = i === plannerActiveTabIndex;
        btn.style.opacity      = active ? '1' : '0.55';
        btn.style.borderBottom = active ? `3px solid ${tabColors[i]}` : '3px solid transparent';
        btn.style.color        = active ? tabColors[i] : '#cbd5e1';
    }
}

function _renderTree() {
    if (!plannerCtx || !plannerCanvas) return;

    const activeKey = tabKeys[plannerActiveTabIndex];
    const img       = AssetManager.images[activeKey];

    plannerCtx.clearRect(0, 0, plannerCanvas.width, plannerCanvas.height);

    if (!img || !img.width) {
        plannerCtx.fillStyle = '#1a2a20';
        plannerCtx.fillRect(0, 0, plannerCanvas.width, plannerCanvas.height);
        plannerCtx.fillStyle = '#666';
        plannerCtx.font = '18px Outfit, sans-serif';
        plannerCtx.textAlign = 'center';
        plannerCtx.fillText('Loading tree…', plannerCanvas.width / 2, plannerCanvas.height / 2);
        return;
    }

    const cW    = plannerCanvas.width;
    const cH    = plannerCanvas.height;
    const scale = Math.min(cW / img.width, cH / img.height);
    const drawW = img.width  * scale;
    const drawH = img.height * scale;
    const ox    = (cW - drawW) / 2;
    const oy    = (cH - drawH) / 2;

    plannerCtx.drawImage(img, ox, oy, drawW, drawH);

    const nodes     = TREE_NODES[activeKey];
    const simState  = _getSimulatedTreeState(activeKey);
    const shortName = TREE_SHORT_NAME[activeKey];
    const defs      = NODE_DEFS[activeKey];
    const scX       = drawW / ANALYSIS_IMG_WIDTH;
    const scY       = drawH / ANALYSIS_IMG_HEIGHT;

    if (!nodes || !defs) return;

    nodes.forEach((coords, idx) => {
        const [x1, y1, x2, y2] = coords;
        const boxX = ox + x1 * scX;
        const boxY = oy + y1 * scY;
        const boxW = (x2 - x1) * scX;
        const boxH = (y2 - y1) * scY;

        const def = getNodeDef(activeKey, idx);
        if (!def) return;

        const baseKey      = `${shortName}.${def.tier}.${def.name}`;
        const unlocked     = isNodeUnlocked(activeKey, idx, simState);
        const fullyQueued  = _isFullyQueued(activeKey, def);
        const queueCount   = localBuildOrder.filter(item => item.nodeKey === baseKey && item.tree === activeKey).length;
        const isHovered    = idx === hoveredNodeIdx;
        const tabColor     = tabColors[plannerActiveTabIndex];

        if (fullyQueued) {
            // All allowed instances are planned — show star
            _drawStar(boxX, boxY, boxW, boxH, tabColor);
        } else if (!unlocked) {
            // Tier prerequisites not yet met in the planned sequence
            _drawLock(boxX, boxY, boxW, boxH);
        } else {
            // Available to add (or partially queued, e.g. Focused Training ×1/2)
            plannerCtx.save();
            if (isHovered) {
                plannerCtx.fillStyle = tabColor + '30';
                plannerCtx.fillRect(boxX, boxY, boxW, boxH);
            }
            plannerCtx.strokeStyle = tabColor;
            plannerCtx.lineWidth   = isHovered ? 4 : 3;
            plannerCtx.strokeRect(boxX, boxY, boxW, boxH);
            plannerCtx.restore();

            // Badge for any node that is partially queued (use nodes or focusedtraining 1/2)
            if (queueCount > 0) {
                plannerCtx.save();
                plannerCtx.fillStyle    = tabColor;
                plannerCtx.font         = 'bold 11px Arial';
                plannerCtx.textAlign    = 'center';
                plannerCtx.textBaseline = 'middle';
                const bx = boxX + boxW - 11;
                const by = boxY + 11;
                plannerCtx.beginPath();
                plannerCtx.arc(bx, by, 10, 0, Math.PI * 2);
                plannerCtx.fill();
                plannerCtx.fillStyle = '#fff';
                plannerCtx.fillText(`×${queueCount}`, bx, by);
                plannerCtx.restore();
            }
        }
    });

    // Draw tooltip on top of everything else
    if (hoveredNodeIdx >= 0 && hoveredNodeIdx < nodes.length) {
        const [x1, y1, x2, y2] = nodes[hoveredNodeIdx];
        const boxX = ox + x1 * scX;
        const boxY = oy + y1 * scY;
        const boxW = (x2 - x1) * scX;
        const boxH = (y2 - y1) * scY;
        const def  = getNodeDef(activeKey, hoveredNodeIdx);
        if (def) {
            const nk = getNodeConfigKey(activeKey, hoveredNodeIdx, simState.purchased);
            _drawTooltip(nk, def, boxX, boxY, boxW, boxH);
        }
    }
}

function _drawStar(boxX, boxY, boxW, boxH, color) {
    const starImg = AssetManager.images['star'];
    if (starImg) {
        const size = Math.min(boxW, boxH) * 0.6;
        plannerCtx.drawImage(starImg, boxX + (boxW - size) / 2, boxY + (boxH - size) / 2, size, size);
    }
    plannerCtx.save();
    plannerCtx.strokeStyle = color;
    plannerCtx.lineWidth   = 3;
    plannerCtx.strokeRect(boxX, boxY, boxW, boxH);
    plannerCtx.restore();
}

function _drawLock(boxX, boxY, boxW, boxH) {
    const lockImg = AssetManager.images['lock'];
    if (lockImg) {
        const size = Math.min(boxW, boxH) * 0.6;
        plannerCtx.drawImage(lockImg, boxX + (boxW - size) / 2, boxY + (boxH - size) / 2, size, size);
    }
}

function _drawTooltip(nodeKey, def, boxX, boxY, boxW, boxH) {
    const info     = ABILITY_TOOLTIPS[nodeKey] || { title: nodeKey, desc: 'Upgrade.' };
    const costData = HARDCODED_COSTS[nodeKey] || {};

    let costText;
    if (def.kind === 'cost') {
        const amt = costData.cost !== undefined ? costData.cost : 0;
        costText = costData.isMana ? `Cost: ${amt} Mana` : `Cost: ${amt} Gold`;
    } else {
        const amt = costData.use !== undefined ? costData.use : 0;
        costText = costData.isMana ? `Use: ${amt} Mana` : `Use: ${amt} Gold`;
    }

    const tooltipWidth = 280;
    const words = info.desc.split(' ');
    const lines = [];
    let cur = '';
    plannerCtx.font = '13px Outfit, sans-serif';
    for (const word of words) {
        const test = cur + word + ' ';
        if (plannerCtx.measureText(test).width > tooltipWidth - 24 && cur !== '') {
            lines.push(cur.trim()); cur = word + ' ';
        } else { cur = test; }
    }
    if (cur) lines.push(cur.trim());

    const ttH = 62 + lines.length * 18;
    let tx = boxX + boxW / 2 - tooltipWidth / 2;
    let ty = boxY - ttH - 10;
    if (tx < 4) tx = 4;
    if (tx + tooltipWidth > plannerCanvas.width - 4) tx = plannerCanvas.width - tooltipWidth - 4;
    if (ty < 4) ty = boxY + boxH + 10;

    plannerCtx.save();
    plannerCtx.fillStyle   = 'rgba(12, 18, 36, 0.97)';
    plannerCtx.strokeStyle = 'rgba(139, 92, 246, 0.65)';
    plannerCtx.lineWidth   = 1.5;
    plannerCtx.beginPath();
    if (plannerCtx.roundRect) plannerCtx.roundRect(tx, ty, tooltipWidth, ttH, 8);
    else plannerCtx.rect(tx, ty, tooltipWidth, ttH);
    plannerCtx.fill();
    plannerCtx.stroke();

    plannerCtx.textAlign = 'center';
    plannerCtx.fillStyle = '#fff';
    plannerCtx.font = 'bold 14px Outfit, sans-serif';
    plannerCtx.fillText(info.title, tx + tooltipWidth / 2, ty + 20);

    plannerCtx.fillStyle = def.kind === 'cost' ? '#38bdf8' : '#fb7185';
    plannerCtx.font = 'bold 11px Outfit, sans-serif';
    plannerCtx.fillText(`${def.kind === 'cost' ? 'Passive' : 'Active'} · ${costText}`, tx + tooltipWidth / 2, ty + 38);

    plannerCtx.fillStyle = '#cbd5e1';
    plannerCtx.font = '12px Outfit, sans-serif';
    let ly = ty + 55;
    for (const line of lines) { plannerCtx.fillText(line, tx + tooltipWidth / 2, ly); ly += 18; }
    plannerCtx.restore();
}

function _renderList() {
    const container = document.getElementById('planner-build-list');
    if (!container) return;
    container.innerHTML = '';

    if (localBuildOrder.length === 0) {
        container.innerHTML = `<div class="bo-empty">Click unlocked nodes to add upgrades</div>`;
        return;
    }

    localBuildOrder.forEach((item, idx) => {
        const activeKey  = item.tree;
        const shortName  = TREE_SHORT_NAME[activeKey];
        const defs       = NODE_DEFS[activeKey];
        const def        = defs ? defs.find(d => `${shortName}.${d.tier}.${d.name}` === item.nodeKey) : null;
        const info       = ABILITY_TOOLTIPS[item.nodeKey] || { title: item.nodeKey };
        const treeIdx    = tabKeys.indexOf(activeKey);
        const treeColor  = tabColors[treeIdx] || '#fff';
        const treeName   = tabNames[treeIdx]  || activeKey;
        const kindLabel  = def ? (def.kind === 'cost' ? 'Passive' : 'Active') : '';

        const row = document.createElement('div');
        row.className = 'build-order-row';
        row.innerHTML = `
            <span class="bo-num">${idx + 1}</span>
            <div class="bo-info">
                <span class="bo-name" style="color:${treeColor}">${info.title}</span>
                <span class="bo-tree">${treeName}${kindLabel ? ' · ' + kindLabel : ''}</span>
            </div>
            <div class="bo-controls">
                <button class="bo-arrow" data-action="up"   data-idx="${idx}" title="Move up"   ${idx === 0                        ? 'disabled' : ''}>▲</button>
                <button class="bo-arrow" data-action="down" data-idx="${idx}" title="Move down" ${idx === localBuildOrder.length - 1 ? 'disabled' : ''}>▼</button>
                <button class="bo-remove" data-idx="${idx}" title="Remove">✕</button>
            </div>
        `;
        container.appendChild(row);
    });

    // Wire buttons
    container.querySelectorAll('.bo-remove').forEach(btn => {
        btn.addEventListener('click', () => {
            localBuildOrder.splice(parseInt(btn.dataset.idx), 1);
            _renderAll();
        });
    });
    container.querySelectorAll('.bo-arrow').forEach(btn => {
        btn.addEventListener('click', () => {
            const i = parseInt(btn.dataset.idx);
            if (btn.dataset.action === 'up' && i > 0) {
                [localBuildOrder[i - 1], localBuildOrder[i]] = [localBuildOrder[i], localBuildOrder[i - 1]];
            } else if (btn.dataset.action === 'down' && i < localBuildOrder.length - 1) {
                [localBuildOrder[i], localBuildOrder[i + 1]] = [localBuildOrder[i + 1], localBuildOrder[i]];
            }
            _renderAll();
        });
    });
}

function _updateCount() {
    const el = document.getElementById('build-order-count');
    if (el) el.textContent = localBuildOrder.length === 0
        ? ''
        : `${localBuildOrder.length} step${localBuildOrder.length !== 1 ? 's' : ''} planned`;
}

// ─── canvas event handlers ────────────────────────────────────────────────────
function _getHitInfo(e) {
    const rect   = plannerCanvas.getBoundingClientRect();
    const mx     = (e.clientX - rect.left) * (plannerCanvas.width  / rect.width);
    const my     = (e.clientY - rect.top)  * (plannerCanvas.height / rect.height);
    const key    = tabKeys[plannerActiveTabIndex];
    const img    = AssetManager.images[key];
    const nodes  = TREE_NODES[key];
    if (!img || !nodes) return null;

    const scale  = Math.min(plannerCanvas.width / img.width, plannerCanvas.height / img.height);
    const drawW  = img.width  * scale;
    const drawH  = img.height * scale;
    const ox     = (plannerCanvas.width  - drawW) / 2;
    const oy     = (plannerCanvas.height - drawH) / 2;
    const scX    = drawW / ANALYSIS_IMG_WIDTH;
    const scY    = drawH / ANALYSIS_IMG_HEIGHT;

    for (let idx = 0; idx < nodes.length; idx++) {
        const [x1, y1, x2, y2] = nodes[idx];
        const boxX = ox + x1 * scX, boxY = oy + y1 * scY;
        const boxW = (x2 - x1) * scX, boxH = (y2 - y1) * scY;
        if (mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + boxH) {
            return { idx, activeKey: key };
        }
    }
    return null;
}

function _handleCanvasClick(e) {
    const hit = _getHitInfo(e);
    console.log('[BuildOrderPlanner] canvas click — hit:', hit);
    if (!hit) return;

    const { idx, activeKey } = hit;
    const def       = getNodeDef(activeKey, idx);
    if (!def) { console.log('[BuildOrderPlanner] no def for idx', idx); return; }

    const simState  = _getSimulatedTreeState(activeKey);
    const unlocked  = isNodeUnlocked(activeKey, idx, simState);
    console.log(`[BuildOrderPlanner] node idx=${idx} def=${def.tier}.${def.name} unlocked=${unlocked}`);
    if (!unlocked) return;

    const shortName = TREE_SHORT_NAME[activeKey];
    const baseKey   = `${shortName}.${def.tier}.${def.name}`;

    // Block when this node slot is fully queued (cost: max 1; focusedtraining: max 2; use: never blocked)
    if (_isFullyQueued(activeKey, def)) {
        console.log('[BuildOrderPlanner] fully queued (max reached):', baseKey);
        return;
    }

    localBuildOrder.push({ tree: activeKey, nodeKey: baseKey });
    console.log('[BuildOrderPlanner] added to plan:', baseKey, '— order now:', localBuildOrder.length, 'steps');
    _renderAll();
}

function _handleCanvasHover(e) {
    const hit    = _getHitInfo(e);
    const newIdx = hit ? hit.idx : -1;
    if (newIdx !== hoveredNodeIdx) {
        hoveredNodeIdx = newIdx;
        _renderTree();
    }
}

// ─── export / paste helpers ───────────────────────────────────────────────────

// Build a reverse map: shortName → tabKey  (e.g. "siege" → "GOALIE_TREE_SIEGE")
const _SHORT_TO_TABKEY = Object.fromEntries(
    Object.entries(TREE_SHORT_NAME).map(([k, v]) => [v, k])
);

// Parse newline-separated node keys into a valid localBuildOrder array.
// Silently skips blank lines, comment lines (#), and unrecognised keys.
function _parseBuildOrderText(text) {
    const result = [];
    const lines = text.split(/\r?\n/);
    for (const raw of lines) {
        const line = raw.trim();
        if (!line || line.startsWith('#')) continue;

        // Key format: "shortName.tier.name" e.g. "siege.t1.siegedoctrine"
        const parts = line.split('.');
        if (parts.length < 3) continue;
        const shortName = parts[0];
        const tier      = parts[1];
        const name      = parts.slice(2).join('.'); // handles dots in future names

        const activeKey = _SHORT_TO_TABKEY[shortName];
        if (!activeKey) continue;

        const defs = NODE_DEFS[activeKey];
        if (!defs) continue;

        // Accept 'focusedtraining2' → maps to base name 'focusedtraining'
        const baseName = name === 'focusedtraining2' ? 'focusedtraining' : name;
        const def = defs.find(d => d.tier === tier && d.name === baseName);
        if (!def) continue;

        result.push({ tree: activeKey, nodeKey: `${shortName}.${tier}.${baseName}` });
    }
    return result;
}

// Briefly replace a button's text content to show feedback, then restore.
function _flashBtn(btn, msg, durationMs = 1800) {
    const original = btn.textContent;
    btn.textContent = msg;
    btn.disabled = true;
    setTimeout(() => {
        btn.textContent = original;
        btn.disabled = false;
    }, durationMs);
}
