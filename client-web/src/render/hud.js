import { drawImageCam } from './canvas.js';
import { CONSTANTS } from '../constants.js';
import { AssetManager } from '../assets/sprites.js';
import { clientUI, gameState } from '../state.js';
import { isKillableEntity } from './targeting.js';

export * from '../data/goalieTree.js';
import {
    ABILITY_TOOLTIPS,
    HARDCODED_COSTS,
    ANALYSIS_IMG_WIDTH,
    ANALYSIS_IMG_HEIGHT,
    TREE_NODES,
    NODE_DEFS,
    tabNames,
    tabColors,
    tabKeys,
    tabCount,
    tabWidth,
    tabHeight,
    spacing,
    TREE_SHORT_NAME,
    isNodeUnlocked,
    getNodeDef,
    getNodeConfigKey,
    getTreeState
} from '../data/goalieTree.js';

const DEBUG_MODE = false; // true = flat grey boxes over every node, no lock/star logic.

function drawOverlayIcon(ctx, src, x, y, w, h, alpha) {
    const img = AssetManager.images[src];
    if (!img) return;
    const size = Math.min(w, h) * 0.6;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.drawImage(img, x + (w - size) / 2, y + (h - size) / 2, size, size);
    ctx.restore();
}

export function drawHealthBars(ctx, game, camX, camY) {
    if (!game) return;
    ctx.globalCompositeOperation = "source-over";

    // Draw Health bars above entities
    const entities = [...(game.players || []), ...(game.entityPool || [])];

    for (const e of entities) {
        if (e.health > 0 && e.entityClass !== 'LaneMinion') {
            if (!isKillableEntity(e)) continue;

            const invisible = Boolean(
                game.underControl && game.underControl.team && e.team && game.underControl.team !== e.team &&
                game.effectPool && Array.isArray(game.effectPool.effects) && e.id &&
                game.effectPool.effects.some(ef => ef && ef.effect === 'STEALTHED' && ef.on && ef.on.id && ef.on.id.toString() === e.id.toString()) &&
                !game.effectPool.effects.some(ef => ef && ef.effect === 'FLARE' && ef.on && ef.on.id && ef.on.id.toString() === e.id.toString())
            );
            if (invisible) continue;

            if (e.entityClass === 'Dragon') {
                const cx = Math.floor(e.X + (e.width || 120) / 2 - camX);
                const cy = Math.floor(e.Y - 25 - camY);
                const radius = 22;

                ctx.save();
                
                ctx.beginPath();
                ctx.arc(cx, cy, radius, 0, 2 * Math.PI);
                ctx.fillStyle = 'rgba(10, 26, 20, 0.8)';
                ctx.fill();

                ctx.beginPath();
                ctx.arc(cx, cy, radius, 0, 2 * Math.PI);
                ctx.strokeStyle = '#475569';
                ctx.lineWidth = 4;
                ctx.stroke();

                const homeRatio = Math.max(0, Math.min(1.0, (e.homeDamage || 0) / 250.0));
                ctx.beginPath();
                ctx.arc(cx, cy, radius, Math.PI / 2, Math.PI / 2 + homeRatio * Math.PI, false);
                ctx.strokeStyle = '#3b82f6';
                ctx.lineWidth = 4;
                ctx.stroke();

                const awayRatio = Math.max(0, Math.min(1.0, (e.awayDamage || 0) / 250.0));
                ctx.beginPath();
                ctx.arc(cx, cy, radius, Math.PI / 2, Math.PI / 2 - awayRatio * Math.PI, true);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 4;
                ctx.stroke();

                ctx.restore();
                continue;
            }

            const hpPercent = e.health / e.maxHealth;
            let xOffset = (e.team === 'AWAY') ? -21 : -25;

            ctx.fillStyle = e.team === 'HOME' ? 'blue' : 'white';
            const x = Math.floor(e.X + xOffset - camX);
            const y = Math.floor(e.Y - 13 - camY);

            // Background
            ctx.fillRect(x, y, 100, 15);

            // Foreground
            ctx.fillStyle = getHpColor(hpPercent * 100);
            ctx.fillRect(x, Math.floor(e.Y - 10 - camY), Math.floor(hpPercent * 100), 9);

            if (e.entityClass !== 'Wall' && e.entityClass !== 'Trap') {
                if (e.fuel !== undefined) {
                    const maxFuel = 100;
                    const fuelRatio = Math.max(0, Math.min(1.0, e.fuel / maxFuel));
                    ctx.fillStyle = fuelRatio > 0.25 ? 'rgb(128,128,255)' : 'darkred';
                    ctx.fillRect(x, Math.floor(e.Y - 4 - camY), Math.floor(fuelRatio * 100), 3);
                }

                // Render 0-8 ammo dots above health bar for Captain
                if (e.type === 'CAPTAIN' || (e.maxAmmo !== undefined && e.maxAmmo > 0)) {
                    const totalDots = e.maxAmmo || 8;
                    const curAmmo = e.ammo !== undefined ? e.ammo : 8;
                    const dotSpacing = 11;
                    const startDotX = x + (100 - (totalDots - 1) * dotSpacing) / 2;
                    const dotY = Math.floor(e.Y - 18 - camY);

                    for (let d = 0; d < totalDots; d++) {
                        const dx = startDotX + d * dotSpacing;
                        const isLoaded = d < curAmmo;

                        ctx.beginPath();
                        ctx.arc(dx, dotY, 3, 0, 2 * Math.PI);
                        ctx.fillStyle = isLoaded ? '#facc15' : 'rgba(55, 65, 81, 0.8)';
                        ctx.fill();
                        ctx.strokeStyle = '#000000';
                        ctx.lineWidth = 1;
                        ctx.stroke();
                    }
                }
            }
        }
    }
}

export function drawHud(ctx, game, state) {
    if (!game) return;
    let hoveredNode = null;
    ctx.globalCompositeOperation = "source-over";

    // Draw Scores
    ctx.font = 'bold 30px Arial';
    if (game.home) {
        ctx.fillStyle = '#3b82f6'; // Home team blue
        ctx.fillText(`HOME: ${game.home.score}`, 50, 35);
    }
    if (game.away) {
        ctx.fillStyle = '#ffffff'; // Away team white
        ctx.fillText(`AWAY: ${game.away.score}`, CONSTANTS.X_RES - 200, 35);
    }

    // Draw 4x20 Dashed Line Upgrade Grids for both teams next to score
    drawTreeProgressGrids(ctx, game);

    // Draw Game Timer
    const fps = 1000 / (game.GAMETICK_MS || 25);
    const timeSec = game.framesSinceStart / fps;
    const sdTime = (game.options && game.options.suddenDeathIndex ? game.options.suddenDeathIndex * 60 : 240);
    const hsdTime = (game.options && game.options.tieIndex ? game.options.hardcoreSuddenDeathIndex * 60 : 240); //TODO recover value from server
    const tieTime = (game.options && game.options.tieIndex ? game.options.tieIndex * 60 : 360);

    let displayTime = 0;
    let timerColor = '#00ff00';
    let isOvertime = false;

    if (timeSec < sdTime) {
        displayTime = sdTime - timeSec;
    } else {
        displayTime = timeSec - sdTime;
        timerColor = '#ff3b30';
        isOvertime = true;
    }

    const timeRounded = Math.floor(displayTime * 10) / 10;
    const minutes = Math.floor(timeRounded / 60);
    const seconds = Math.floor(timeRounded % 60);
    const tenths = Math.floor((timeRounded * 10) % 10);
    let timeStr = `${minutes}:${seconds.toString().padStart(2, '0')}.${tenths}`;
    if (isOvertime) timeStr = "SD " + timeStr;

    ctx.save();
    ctx.textAlign = 'center';
    ctx.font = 'bold 36px Courier New';
    ctx.fillStyle = timerColor;
    ctx.fillText(timeStr, CONSTANTS.X_RES / 2, 35);
    ctx.restore();

    // Timer Warnings
    let bottomText = "";
    let warningColor = "rgba(0, 255, 0, 0.4)";
    const WARN = 30, FWARN = 10;
    const gTime = game.GOALIE_DISABLE_TIME || 120;

    const timer = Math.floor(timeSec);
    const CHWARN = 10;

    if (timer >= gTime - WARN && timer < gTime - FWARN) {
        warningColor = "rgba(230, 230, 0, 0.8)";
        bottomText = "GOALIES VANISHING WARNING";
    } else if (timer >= gTime - FWARN && timer < gTime) {
        warningColor = "rgba(255, 0, 0, 0.9)";
        bottomText = "GOALIES VANISHING WARNING";
    } else if (timer >= gTime && timer < gTime + CHWARN) {
        warningColor = "rgba(255, 0, 0, 1.0)";
        bottomText = "GOALIES VANISHED";
    } else if (timer >= sdTime - WARN && timer < sdTime - FWARN) {
        warningColor = "rgba(230, 230, 0, 0.8)";
        bottomText = "SUDDEN DEATH WARNING";
    } else if (timer >= sdTime - FWARN && timer < sdTime) {
        warningColor = "rgba(255, 0, 0, 0.9)";
        bottomText = "SUDDEN DEATH WARNING";
    } else if (timer >= sdTime && timer < sdTime + CHWARN) {
        warningColor = "rgba(255, 0, 0, 1.0)";
        bottomText = "SUDDEN DEATH ENABLED";
    } else if (timer >= hsdTime - WARN && timer < hsdTime - FWARN) {
        warningColor = "rgba(230, 230, 0, 0.8)";
        bottomText = "HARDCORE SUDDEN DEATH WARNING";
    } else if (timer >= hsdTime - FWARN && timer < hsdTime) {
        warningColor = "rgba(255, 0, 0, 0.9)";
        bottomText = "HARDCORE SUDDEN DEATH WARNING";
    } else if (timer >= hsdTime && timer < hsdTime + CHWARN) {
        warningColor = "rgba(255, 0, 0, 1.0)";
        bottomText = "HARDCORE SUDDEN DEATH ENABLED";
    } else if (timer >= tieTime - WARN && timer < tieTime - FWARN) {
        warningColor = "rgba(230, 230, 0, 0.9)";
        bottomText = "TIE GAME WARNING";
    } else if (timer >= tieTime - FWARN && timer < tieTime) {
        warningColor = "rgba(255, 0, 0, 0.9)";
        bottomText = "TIE GAME WARNING";
    } else if (timer >= tieTime && timer < tieTime + CHWARN) {
        warningColor = "rgba(255, 0, 0, 1.0)";
        bottomText = "TIE GAME";
    }

    if (bottomText) {
        ctx.save();
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';

        const isActiveState = (bottomText === "GOALIES VANISHED" || bottomText === "SUDDEN DEATH ENABLED" || bottomText === "TIE GAME");

        if (isActiveState) {
            ctx.font = 'bold 36px Verdana';
            ctx.fillStyle = 'rgba(0, 0, 0, 0.65)';
            ctx.fillRect(0, 160, CONSTANTS.X_RES, 80);

            ctx.strokeStyle = 'black';
            ctx.lineWidth = 6;
            ctx.strokeText(bottomText, CONSTANTS.X_RES / 2, 200);

            ctx.fillStyle = warningColor;
            ctx.fillText(bottomText, CONSTANTS.X_RES / 2, 200);
        } else {
            ctx.font = 'bold 24px Verdana';
            ctx.fillStyle = warningColor;
            ctx.fillText(bottomText, CONSTANTS.X_RES / 2, 90);
        }
        ctx.restore();
    }

    // Draw Rolling Purchase Announcements (shown up to 10s after purchase, main announcements take priority)
    drawRollingAnnouncements(ctx, game, bottomText);

    // Draw Personal Cooldown / Status Effects
    if (game.underControl && game.effectPool && game.effectPool.effects) {
        let xOffset = 50;
        const yOffset = CONSTANTS.Y_RES - 80;

        let bannerText = "";
        let bannerColor = "";

        game.effectPool.effects.forEach((eff) => {
            if (eff.on && eff.on.id === game.underControl.id) {
                if (eff.effect === 'ROOT') {
                    bannerText = "Rooted!";
                    bannerColor = 'rgba(92, 130, 71, 0.9)';
                } else if (eff.effect === 'SLOW') {
                    bannerText = "Slowed!";
                    bannerColor = 'rgba(115, 230, 191, 0.9)';
                } else if (eff.effect === 'STUN') {
                    bannerText = "Stunned!";
                    bannerColor = 'rgba(255, 189, 0, 0.9)';
                } else if (eff.effect === 'STEAL') {
                    bannerText = "Stolen!";
                    bannerColor = 'rgba(200, 50, 50, 0.9)';
                } else if (eff.effect === 'DEAD') {
                    bannerText = "Dead!";
                    bannerColor = 'black';
                }

                if (eff.effect !== 'ATTACKED') {
                    let spriteKey = `EFFECT_${eff.effect}`;
                    if (eff.effect === 'COOLDOWN_GOALIE') {
                        spriteKey = 'EFFECT_RELOAD';
                    } else if (eff.effect === 'COOLDOWN_Q' && game.underControl.type === 'CAPTAIN' && game.underControl.ammo === 0) {
                        spriteKey = 'EFFECT_RELOAD';
                    }
                    const iconImg = AssetManager.images[spriteKey];
                    if (iconImg) {
                        ctx.save();
                        ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
                        ctx.fillRect(xOffset - 4, yOffset - 4, 48, 48);

                        ctx.drawImage(iconImg, xOffset, yOffset, 40, 40);

                        const percent = eff.percentLeft !== undefined ? eff.percentLeft : 100;
                        if (percent > 0 && percent < 100) {
                            ctx.fillStyle = 'rgba(255, 255, 255, 0.55)';
                            ctx.beginPath();
                            ctx.moveTo(xOffset + 20, yOffset + 20);
                            ctx.arc(
                                xOffset + 20,
                                yOffset + 20,
                                20,
                                -Math.PI / 2,
                                -Math.PI / 2 + ((100 - percent) / 100) * Math.PI * 2,
                                false
                            );
                            ctx.closePath();
                            ctx.fill();
                        }
                        ctx.restore();
                        xOffset += 56;
                    }
                }
            }
        });

        if (bannerText) {
            ctx.save();
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.font = 'bold 54px Verdana';

            ctx.fillStyle = (bannerColor === 'black' || bannerColor === 'rgba(0, 0, 0, 0.9)') ? 'rgba(255, 255, 255, 0.6)' : 'black';
            ctx.fillText(bannerText, CONSTANTS.X_RES / 2 + 3, CONSTANTS.Y_RES / 2 - 100 + 3);

            ctx.fillStyle = bannerColor;
            ctx.fillText(bannerText, CONSTANTS.X_RES / 2, CONSTANTS.Y_RES / 2 - 100);
            ctx.restore();
        }

        // Draw Goalie Currency (Gold) & Mana
        if (game.underControl && game.underControl.type === 'GOALIE') {
            const isHome       = game.underControl.team === 'HOME';
            const purchased    = isHome ? (game.homeGoaliePurchasedUpgrades || []) : (game.awayGoaliePurchasedUpgrades || []);
            const purchasedSet = new Set(purchased);
            const purchasedArray = Array.from(purchasedSet);
            const hasMana = purchasedArray.some(key => key.startsWith('cultivation.'));

            // ── Forked Build Order: Determine next Gold & Mana upgrades for flash ──
            const order = gameState.buildOrder || [];
            let flashGold = false;
            let flashMana = false;
            const goldAmt = Math.floor(isHome ? (game.homeGoalieCurrency || 0) : (game.awayGoalieCurrency || 0));
            const manaAmt = Math.floor(isHome ? (game.homeGoalieMana    || 0) : (game.awayGoalieMana    || 0));

            // Find next unpurchased Gold node
            for (let idx = 0; idx < order.length; idx++) {
                const item = order[idx];
                if (isBuildOrderManaNode(order, item)) continue;
                const itemDefs  = NODE_DEFS[item.tree];
                const itemShort = TREE_SHORT_NAME[item.tree];
                if (!itemDefs || !itemShort) continue;
                const itemDef = itemDefs.find(d => `${itemShort}.${d.tier}.${d.name}` === item.nodeKey);
                if (!itemDef) continue;
                let resolvedNodeKey = item.nodeKey;
                if (itemDef.name === 'focusedtraining' && purchasedSet.has('empowerment.t5.focusedtraining') && !purchasedSet.has('empowerment.t5.focusedtraining2')) {
                    resolvedNodeKey = 'empowerment.t5.focusedtraining2';
                }
                if (itemDef.kind === 'cost' && purchasedSet.has(resolvedNodeKey)) {
                    continue; // already purchased
                }
                const nodeIdx = itemDefs.indexOf(itemDef);
                const treeState = getTreeState(game, game.underControl.team, item.tree);
                if (!isNodeUnlocked(item.tree, nodeIdx, treeState)) {
                    break; // blocked by prerequisites in gold track
                }
                const costData = HARDCODED_COSTS[resolvedNodeKey] || HARDCODED_COSTS[item.nodeKey] || {};
                const amount = costData.use !== undefined ? costData.use : (costData.cost !== undefined ? costData.cost : 0);
                if (amount > 0 && goldAmt >= amount) {
                    flashGold = true;
                }
                break; // Found head of gold track
            }

            // Find next unpurchased Mana node
            for (let idx = 0; idx < order.length; idx++) {
                const item = order[idx];
                if (!isBuildOrderManaNode(order, item)) continue;
                const itemDefs  = NODE_DEFS[item.tree];
                const itemShort = TREE_SHORT_NAME[item.tree];
                if (!itemDefs || !itemShort) continue;
                const itemDef = itemDefs.find(d => `${itemShort}.${d.tier}.${d.name}` === item.nodeKey);
                if (!itemDef) continue;
                if (itemDef.kind === 'cost' && purchasedSet.has(item.nodeKey)) {
                    continue; // already purchased
                }
                const nodeIdx = itemDefs.indexOf(itemDef);
                const treeState = getTreeState(game, game.underControl.team, item.tree);
                const isPollinated = purchasedSet.has('cultivation.t5.manapollinate') && itemDef.tier === 't5' && item.tree !== 'GOALIE_TREE_CULTIVATION';
                if (!isPollinated && !isNodeUnlocked(item.tree, nodeIdx, treeState)) {
                    break; // blocked by prerequisites in mana track
                }
                const costData = HARDCODED_COSTS[item.nodeKey] || {};
                const amount = costData.use !== undefined ? costData.use : (costData.cost !== undefined ? costData.cost : 0);
                if (amount > 0 && manaAmt >= amount) {
                    flashMana = true;
                }
                break; // Found head of mana track
            }

            // Pulse: 0→1→0 on ~1.2 s cycle
            const pulse = 0.55 + 0.45 * Math.sin(Date.now() / 190);

            if (hasMana) {
                ctx.save();
                ctx.fillStyle   = 'rgba(10, 26, 20, 0.8)';
                ctx.strokeStyle = flashMana ? `rgba(220,160,255,${pulse})` : 'violet';
                ctx.lineWidth   = flashMana ? 3 + pulse * 2 : 2;
                if (flashMana) ctx.shadowColor = `rgba(220,160,255,${pulse})`;
                if (flashMana) ctx.shadowBlur  = 14 * pulse;
                ctx.beginPath();
                if (ctx.roundRect) ctx.roundRect(50, CONSTANTS.Y_RES - 220, 220, 50, 8);
                else ctx.rect(50, CONSTANTS.Y_RES - 220, 220, 50);
                ctx.fill();
                ctx.stroke();
                ctx.shadowBlur = 0;

                ctx.fillStyle = 'violet';
                ctx.beginPath();
                ctx.arc(75, CONSTANTS.Y_RES - 195, 12, 0, 2 * Math.PI);
                ctx.fill();

                ctx.fillStyle = '#000000';
                ctx.font = 'bold 14px Arial';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillText('M', 75, CONSTANTS.Y_RES - 195);

                ctx.fillStyle = flashMana ? `rgba(255,220,255,${0.7 + 0.3 * pulse})` : '#ffffff';
                ctx.font = 'bold 20px Arial';
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';
                const manaVal    = Math.floor(isHome ? (game.homeGoalieMana || 0) : (game.awayGoalieMana || 0));
                const maxManaVal = purchasedArray.includes('cultivation.t3.highermanacap') ? 1000 : 250;
                ctx.fillText(`Mana: ${manaVal}/${maxManaVal}`, 100, CONSTANTS.Y_RES - 195);
                ctx.restore();
            }

            ctx.save();
            ctx.fillStyle   = 'rgba(10, 26, 20, 0.8)';
            ctx.strokeStyle = flashGold ? `rgba(255,200,60,${pulse})` : '#ff9f1c';
            ctx.lineWidth   = flashGold ? 3 + pulse * 2 : 2;
            if (flashGold) ctx.shadowColor = `rgba(255,180,0,${pulse})`;
            if (flashGold) ctx.shadowBlur  = 14 * pulse;
            ctx.beginPath();
            if (ctx.roundRect) ctx.roundRect(50, CONSTANTS.Y_RES - 160, 220, 50, 8);
            else ctx.rect(50, CONSTANTS.Y_RES - 160, 220, 50);
            ctx.fill();
            ctx.stroke();
            ctx.shadowBlur = 0;

            ctx.fillStyle = '#ff9f1c';
            ctx.beginPath();
            ctx.arc(75, CONSTANTS.Y_RES - 135, 12, 0, 2 * Math.PI);
            ctx.fill();

            ctx.fillStyle = '#000000';
            ctx.font = 'bold 14px Arial';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('$', 75, CONSTANTS.Y_RES - 135);

            ctx.fillStyle = flashGold ? `rgba(255,230,100,${0.7 + 0.3 * pulse})` : '#ffffff';
            ctx.font = 'bold 20px Arial';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';
            const amt = Math.floor(isHome ? (game.homeGoalieCurrency || 0) : (game.awayGoalieCurrency || 0));
            ctx.fillText(`Gold: ${amt}`, 100, CONSTANTS.Y_RES - 135);
            ctx.restore();
        }
    }

    if (game.underControl && game.underControl.type === 'GOALIE') {
        ctx.save();
        ctx.globalAlpha = 1.0;
        ctx.globalCompositeOperation = "source-over";
        ctx.filter = "none";
        
        hoveredNode = null;

        const totalWidth = tabCount * tabWidth + (tabCount - 1) * spacing;
        const startX = (CONSTANTS.X_RES - totalWidth) / 2;
        const y = CONSTANTS.Y_RES - 60;

        // 1. Draw the Menu Image & Node Overlays
        if (clientUI.goalieTabIndex >= 0 && clientUI.goalieTabIndex < tabCount) {
            const activeKey = tabKeys[clientUI.goalieTabIndex];
            const activeImg = AssetManager.images[activeKey];

            if (activeImg) {
                const ox = (CONSTANTS.X_RES - activeImg.width) / 2;
                const oy = (CONSTANTS.Y_RES - activeImg.height) / 2;

                ctx.drawImage(activeImg, ox, oy);

                const nodes = TREE_NODES[activeKey];
                if (nodes) {
                    const scaleX = activeImg.width / ANALYSIS_IMG_WIDTH;
                    const scaleY = activeImg.height / ANALYSIS_IMG_HEIGHT;

                    if (DEBUG_MODE) {
                        ctx.fillStyle = 'rgba(128, 128, 128, 0.7)';
                        for (const [x1, y1, x2, y2] of nodes) {
                            const boxX = ox + (x1 * scaleX);
                            const boxY = oy + (y1 * scaleY);
                            const boxW = (x2 - x1) * scaleX;
                            const boxH = (y2 - y1) * scaleY;
                            ctx.fillRect(boxX, boxY, boxW, boxH);
                        }
                    } else {
                        const isTabHeld = gameState.controlsHeld && gameState.controlsHeld.TAB;
                        const myTeam = game.underControl ? game.underControl.team : 'HOME';
                        const viewTeam = isTabHeld ? ((myTeam === 'HOME' || myTeam === 0) ? 'AWAY' : 'HOME') : myTeam;
                        const treeState = getTreeState(game, viewTeam, activeKey);

                        nodes.forEach((coords, idx) => {
                            const [x1, y1, x2, y2] = coords;
                            const boxX = ox + (x1 * scaleX);
                            const boxY = oy + (y1 * scaleY);
                            const boxW = (x2 - x1) * scaleX;
                            const boxH = (y2 - y1) * scaleY;

                            const def = getNodeDef(activeKey, idx);
                            if (!def) return; // no definition for this box - draw nothing rather than guess

                            const nodeKey = getNodeConfigKey(activeKey, idx, treeState.purchased);

                            if (state && state.mouseX >= boxX && state.mouseX <= boxX + boxW &&
                                state.mouseY >= boxY && state.mouseY <= boxY + boxH) {
                                hoveredNode = { nodeKey, def, boxX, boxY, boxW, boxH, viewTeam };
                            }

                            // Star: purchased, one-time ('cost' kind) upgrade. 'use'
                            // nodes are repeatable and never starred.
                            if (def.kind === 'cost' && treeState.purchased.has(nodeKey)) {
                                drawOverlayIcon(ctx, 'star', boxX, boxY, boxW, boxH, 1.0);
                                return;
                            }

                            if (!isNodeUnlocked(activeKey, idx, treeState)) {
                                drawOverlayIcon(ctx, 'lock', boxX, boxY, boxW, boxH, 1.0);
                            } else {
                                // Draw tabColors border around the buyable upgrade
                                const tabIdx = tabKeys.indexOf(activeKey);
                                const borderColor = tabColors[tabIdx] || '#ffffff';
                                ctx.save();
                                ctx.strokeStyle = borderColor;
                                ctx.lineWidth = 5;
                                ctx.strokeRect(boxX, boxY, boxW, boxH);
                                ctx.restore();
                            }
                            // else: prereqs met, unpurchased (or repeatable) -> no overlay, still clickable.
                        });

                        // Notice Banner about TAB control to view opponent tech tree
                        ctx.save();
                        ctx.textAlign = 'left';
                        ctx.textBaseline = 'top';
                        const noticeX = ox + 30;
                        const noticeY = oy + (activeImg.height * 0.20);
                        if (isTabHeld) {
                            ctx.font = 'bold 14px Outfit, sans-serif';
                            ctx.fillStyle = '#f43f5e';
                            ctx.fillText('VIEWING OPPONENT', noticeX, noticeY);
                            ctx.fillText('TECH TREE (READ-ONLY)', noticeX, noticeY + 18);
                        } else {
                            ctx.font = '13px Outfit, sans-serif';
                            ctx.fillStyle = '#cbd5e1';
                            ctx.fillText('Hold [TAB] to view', noticeX, noticeY);
                            ctx.fillText('Opponent Tech Tree', noticeX, noticeY + 18);
                        }
                        ctx.restore();
                    }
                }
            }
        }

        // 2. Draw the Tabs
        for (let i = 0; i < tabCount; i++) {
            const x = startX + i * (tabWidth + spacing);
            ctx.save();
            ctx.fillStyle = tabColors[i];
            ctx.globalAlpha = (clientUI.goalieTabIndex === i ? 1.0 : 0.75);
            ctx.beginPath();
            if (ctx.roundRect) {
                ctx.roundRect(x, y, tabWidth, tabHeight, 12);
            } else {
                ctx.rect(x, y, tabWidth, tabHeight);
            }
            ctx.fill();
            ctx.font = "bold 22px Arial";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillStyle = "white";
            ctx.fillText(tabNames[i], x + tabWidth / 2, y + tabHeight / 2);
            ctx.restore();
        }

        ctx.restore();
    }

    // 3. Draw premium tooltip if hover occurs (drawn outside scaling/restores to stay on top of everything!)
    if (hoveredNode) {
        const info = ABILITY_TOOLTIPS[hoveredNode.nodeKey] || { title: hoveredNode.nodeKey, desc: "Upgrade." };
        
        // Look up cost in the hardcoded map
        const costData = HARDCODED_COSTS[hoveredNode.nodeKey] || {};
        let costText = "";
        const isViewHome = (hoveredNode.viewTeam === 0 || hoveredNode.viewTeam === 'HOME');
        const rawUpgrades = isViewHome ? game.homeGoaliePurchasedUpgrades : game.awayGoaliePurchasedUpgrades;
        const purchasedSet = new Set(rawUpgrades || []);

        if (hoveredNode.def.kind === 'cost') {
            const amt = costData.cost !== undefined ? costData.cost : 0;
            const useMana = costData.isMana || canPollinateT5(hoveredNode.nodeKey, purchasedSet);
            costText = useMana ? `Cost: ${amt} Mana` : `Cost: ${amt} Gold`;
        } else {
            const amt = costData.use !== undefined ? costData.use : 0;
            costText = costData.isMana ? `Use: ${amt} Mana` : `Use: ${amt} Gold`;
        }
        
        const typeText = hoveredNode.def.kind === 'cost' ? 'Passive' : 'Active';

        const tooltipWidth = 320;
        const descWords = info.desc.split(' ');
        const lines = [];
        let currentLine = "";
        ctx.font = '14px Outfit, sans-serif';
        for (let word of descWords) {
            let testLine = currentLine + word + " ";
            let metrics = ctx.measureText(testLine);
            if (metrics.width > tooltipWidth - 30 && currentLine !== "") {
                lines.push(currentLine.trim());
                currentLine = word + " ";
            } else {
                currentLine = testLine;
            }
        }
        if (currentLine !== "") {
            lines.push(currentLine.trim());
        }

        const tooltipHeight = 75 + lines.length * 20;

        let tx = state.mouseX - tooltipWidth / 2;
        let ty = state.mouseY - tooltipHeight - 20;

        // Clamp to screen edges
        if (tx < 10) tx = 10;
        if (tx + tooltipWidth > CONSTANTS.X_RES - 10) {
            tx = CONSTANTS.X_RES - tooltipWidth - 10;
        }
        if (ty < 10) {
            ty = state.mouseY + 20;
        }

        ctx.save();
        ctx.shadowColor = 'rgba(0, 0, 0, 0.5)';
        ctx.shadowBlur = 10;
        
        ctx.fillStyle = 'rgba(15, 23, 42, 0.96)';
        ctx.strokeStyle = 'rgba(139, 92, 246, 0.6)';
        ctx.lineWidth = 2.5;

        ctx.beginPath();
        if (ctx.roundRect) {
            ctx.roundRect(tx, ty, tooltipWidth, tooltipHeight, 10);
        } else {
            ctx.rect(tx, ty, tooltipWidth, tooltipHeight);
        }
        ctx.fill();
        ctx.stroke();

        ctx.shadowColor = 'transparent';
        ctx.textAlign = 'center';

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 18px Outfit, sans-serif';
        ctx.fillText(info.title, tx + tooltipWidth / 2, ty + 25);

        ctx.fillStyle = hoveredNode.def.kind === 'cost' ? '#38bdf8' : '#fb7185';
        ctx.font = 'bold 13px Outfit, sans-serif';
        ctx.fillText(`${typeText} • ${costText}`, tx + tooltipWidth / 2, ty + 46);

        ctx.fillStyle = '#e2e8f0';
        ctx.font = '14px Outfit, sans-serif';
        let ly = ty + 68;
        for (let line of lines) {
            ctx.fillText(line, tx + tooltipWidth / 2, ly);
            ly += 20;
        }

        ctx.restore();
    }
}

export function drawTreeProgressGrids(ctx, game) {
    if (!game) return;

    const cols = 4;
    const colWidth = 19;  // +60% wider
    const colGap = 10;
    const rowHeight = 3.4; // +40% taller
    const rowGap = 1.6;
    const stepY = rowHeight + rowGap; // 5.0px per row

    const treeKeys = ["GOALIE_TREE_SIEGE", "GOALIE_TREE_FORTRESS", "GOALIE_TREE_EMPOWERMENT", "GOALIE_TREE_CULTIVATION"];
    const treeColors = ["#ef4444", "#3b82f6", "#eab308", "#a855f7"];

    const teams = [
        { x: 210, y: 14, purchased: game.homeGoaliePurchasedUpgrades || [] },
        { x: CONSTANTS.X_RES - 330, y: 14, purchased: game.awayGoaliePurchasedUpgrades || [] }
    ];

    teams.forEach(({ x: startX, y: startY, purchased }) => {
        const purchasedSet = new Set(purchased);

        treeKeys.forEach((treeKey, colIdx) => {
            const shortName = TREE_SHORT_NAME[treeKey];
            const defs = NODE_DEFS[treeKey] || [];
            const permDefs = defs.filter(def => def.kind === 'cost');
            const treeRows = permDefs.length; // Dynamic rows based on total permanent cost upgrades for this tree
            
            let count = 0;
            permDefs.forEach(def => {
                const nodeKey = `${shortName}.${def.tier}.${def.name}`;
                if (purchasedSet.has(nodeKey)) {
                    count++;
                }
            });

            const colX = startX + colIdx * (colWidth + colGap);
            const color = treeColors[colIdx];

            // Bottom-to-top stacking: starts at bottom and counts UP!
            const colBottomY = startY + treeRows * stepY;

            for (let r = 0; r < treeRows; r++) {
                // r = 0 is bottom row (1st upgrade purchased)
                const rowY = colBottomY - (r + 1) * stepY + rowGap / 2;
                const isPurchased = r < count;

                ctx.save();
                ctx.beginPath();
                ctx.moveTo(colX, rowY);
                ctx.lineTo(colX + colWidth, rowY);

                if (isPurchased) {
                    ctx.strokeStyle = color;
                    ctx.lineWidth = 3.2;
                    ctx.globalAlpha = 1.0;
                } else {
                    ctx.strokeStyle = color;
                    ctx.lineWidth = 1.5;
                    ctx.globalAlpha = 0.25;
                }

                ctx.stroke();
                ctx.restore();
            }
        });
    });
}

export function drawRollingAnnouncements(ctx, game, mainAnnouncementText) {
    if (!game) return;

    const now = Date.now();
    const rawList = game.recentPurchases || [];
    const validPurchases = rawList.filter(p => p && p.timestampMs && (now - p.timestampMs <= 10000));
    
    const recent3 = validPurchases.slice(-3);
    if (recent3.length === 0) return;

    // Shifted up by ~1% screen height (baseY = 38) so 3rd popup doesn't block the field
    const baseY = 38;
    const boxW = 360;
    const boxH = 18;
    const spacingY = 19.5;

    const treeColors = {
        siege: "#ef4444",
        fortress: "#3b82f6",
        empowerment: "#eab308",
        cultivation: "#a855f7"
    };

    recent3.forEach((item, idx) => {
        const boxX = (CONSTANTS.X_RES - boxW) / 2;
        const boxY = baseY + idx * spacingY;

        const isHome = item.team === 'HOME';
        const teamName = isHome ? "Home" : "Away";
        const teamColor = isHome ? "#3b82f6" : "#ffffff";

        const nodeKey = item.nodeKey || "";
        const parts = nodeKey.split('.');
        const treeShort = parts[0] || "siege";

        const tooltip = ABILITY_TOOLTIPS[nodeKey] || {};
        const title = tooltip.title || nodeKey;

        const costData = HARDCODED_COSTS[nodeKey] || {};
        const amt = costData.cost !== undefined ? costData.cost : (costData.use !== undefined ? costData.use : 0);
        const isMana = !!costData.isMana;
        const currency = isMana ? "mana" : "gold";
        const costColor = treeColors[treeShort] || "#ef4444";

        const timeStr = `[${item.gameTime || "0:00"}] `;

        ctx.save();
        ctx.fillStyle = 'rgba(15, 23, 42, 0.85)';
        ctx.strokeStyle = 'rgba(148, 163, 184, 0.25)';
        ctx.lineWidth = 1;

        ctx.beginPath();
        if (ctx.roundRect) ctx.roundRect(boxX, boxY, boxW, boxH, 4);
        else ctx.rect(boxX, boxY, boxW, boxH);
        ctx.fill();
        ctx.stroke();

        ctx.textBaseline = 'middle';

        const seg0 = timeStr;
        const seg1 = teamName;
        const seg2 = ` purchased ${title} `;
        const seg3 = `(${amt} ${currency})`;

        ctx.font = 'bold 11px Outfit, sans-serif';
        const w0 = ctx.measureText(seg0).width;
        const w1 = ctx.measureText(seg1).width;
        ctx.font = '11px Outfit, sans-serif';
        const w2 = ctx.measureText(seg2).width;
        ctx.font = 'bold 11px Outfit, sans-serif';
        const w3 = ctx.measureText(seg3).width;

        const totalW = w0 + w1 + w2 + w3;
        let startX = (CONSTANTS.X_RES - totalW) / 2;
        const centerY = boxY + boxH / 2;

        ctx.save();
        ctx.textAlign = 'left';

        // 1. Purchase Game Time (Green)
        ctx.font = 'bold 11px Outfit, sans-serif';
        ctx.fillStyle = '#22c55e';
        ctx.fillText(seg0, startX, centerY);
        startX += w0;

        // 2. Team Name (Home: Blue, Away: White)
        ctx.fillStyle = teamColor;
        ctx.fillText(seg1, startX, centerY);
        startX += w1;

        // 3. Action (" purchased <Title> ")
        ctx.font = '11px Outfit, sans-serif';
        ctx.fillStyle = '#e2e8f0';
        ctx.fillText(seg2, startX, centerY);
        startX += w2;

        // 4. Cost & Currency (Tree Color)
        ctx.font = 'bold 11px Outfit, sans-serif';
        ctx.fillStyle = costColor;
        ctx.fillText(seg3, startX, centerY);
        ctx.restore();

        ctx.restore();
    });
}

function getHpColor(percent) {
    if (percent > 66) return 'green';
    if (percent > 33) return 'yellow';
    return 'red';
}