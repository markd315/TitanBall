import { drawImageCam } from './canvas.js';
import { CONSTANTS } from '../constants.js';
import { AssetManager } from '../assets/sprites.js';
import { clientUI, gameState } from '../state.js';

const DEBUG_MODE = false; // true = flat grey boxes over every node, no lock/star logic.
export const ANALYSIS_IMG_WIDTH = 1024;
export const ANALYSIS_IMG_HEIGHT = 559;

export const TREE_NODES = {
    GOALIE_TREE_FORTRESS: [
        [234,14,292,84], [377,14,435,84], [590,21,645,83], [735,18,790,82], [880,21,936,82],
        [485,118,538,170],
        [278,224,341,246], [405,224,481,254], [546,218,618,237], [681,220,741,237],
        [325,309,415,372], [463,309,549,372], [611,309,702,372],
        [263,389,322,452], [411,410,481,431], [591,410,653,448], [751,410,816,451],
        [147,482,389,545], [389,482,630,545], [653,482,872,545]
    ],
    GOALIE_TREE_EMPOWERMENT: [
        [376,12,439,85], [593,14,652,85], [740,12,798,85],
        [485,120,536,167],
        [285,204,340,253], [418,205,474,253], [555,204,609,253], [688,205,742,253],
        [326,320,406,353], [458,320,556,355], [604,316,709,355],
        [186,406,232,440], [429,400,473,436], [663,399,707,436],
        [146,463,386,533], [386,463,627,533], [658,462,863,531]
    ],
    GOALIE_TREE_SIEGE: [
        [234,16,290,85], [362,16,419,85], [486,16,543,85], [606,15,663,82], [710,15,767,82], [814,15,871,82], [918,15,974,82],
        [485,120,537,170],
        [272,218,354,253], [397,210,492,258], [537,210,616,259], [670,212,746,259],
        [339,308,442,372], [537,317,685,346],
        [187,404,347,447], [420,389,585,451], [650,406,752,447],
        [147,478,399,538], [399,478,629,538], [653,478,870,541]
    ],
    GOALIE_TREE_CULTIVATION: [
        [331,13,391,83], [741,13,796,84], [896,14,952,83],
        [485,119,538,170],
        [302,218,375,240], [481,218,546,247], [656,214,737,250],
        [356,309,440,372], [577,309,653,372],
        [184,406,392,448], [429,412,605,447], [663,409,802,451],
        [147,480,388,547], [388,480,628,547], [654,479,869,547]
    ]
};

// Every node has a real tier (t1-t6) and a purchase kind. "header" was never
// a real tier - visually it's just row 1, which happens to contain the t2
// node(s) and the repeatable t5 node(s) for that tree, mixed with the
// occasional one-time t5 node (fortress.noflyzone) that's just drawn there.
// kind: 'cost' = one-time unlock, stays purchased forever, gets the star.
//       'use'  = repeatable single-use charge, no star, re-clickable forever
//                as long as its tier prereq still holds.
// currency: 'gold' (default, omitted) or 'mana' - drives which balance field
// gets checked/deducted. Only cultivation uses mana.
export const NODE_DEFS = {
    GOALIE_TREE_FORTRESS: [
        // row 1 (visual header row)
        { tier: 't2', name: 'reinforce',         kind: 'use'  },
        { tier: 't2', name: 'healingburst',       kind: 'use'  },
        { tier: 't5', name: 'noflyzonetmp',          kind: 'cost' }, // one-time, just drawn in row 1
        { tier: 't5', name: 'emergencybarrier',   kind: 'use'  },
        { tier: 't5', name: 'repairdrone',        kind: 'use'  },
        // row 2
        { tier: 't1', name: 'homeward',           kind: 'cost' },
        // row 3
        { tier: 't3', name: 'snaretrap',          kind: 'cost' },
        { tier: 't3', name: 'homehealamp',        kind: 'cost' },
        { tier: 't3', name: 'fastbreakinsurance', kind: 'cost' },
        { tier: 't3', name: 'biggermodels',       kind: 'cost' },
        // row 4
        { tier: 't4', name: 'bastionprotocol',          kind: 'cost' },
        { tier: 't4', name: 'deadwalls',    kind: 'cost' },
        { tier: 't4', name: 'barrage',            kind: 'cost' },
        // row 5 - TODO: TREE_NODES has 4 boxes here, only 3 named t5-cost entries exist
        { tier: 't5', name: 'noflyzoneperm',           kind: 'cost' },
        { tier: 't5', name: 'dilators',           kind: 'cost' },
        { tier: 't5', name: 'icebarrage',         kind: 'cost' },
        { tier: 't5', name: 'firebarrage',        kind: 'cost' },
        // row 6
        { tier: 't6', name: 'deepfreeze',         kind: 'cost' },
        { tier: 't6', name: 'impenetrable',       kind: 'cost' },
        { tier: 't6', name: 'hemmedin',           kind: 'cost' },
    ],
    GOALIE_TREE_SIEGE: [
        // row 1
        { tier: 't2', name: 'overchargeminion',   kind: 'use'  },
        { tier: 't2', name: 'lowgravity',          kind: 'use'  },
        { tier: 't2', name: 'energyrush',          kind: 'use'  },
        { tier: 't5', name: 'callsiegeminion',     kind: 'use'  },
        { tier: 't5', name: 'anchor',              kind: 'use'  },
        { tier: 't5', name: 'shockgrenade',        kind: 'use'  },
        { tier: 't5', name: 'wallsdown',           kind: 'use'  },
        // row 2
        { tier: 't1', name: 'siegedoctrine',       kind: 'cost' },
        // row 3
        { tier: 't3', name: 'rushlane',            kind: 'cost' },
        { tier: 't3', name: 'forwardmines',        kind: 'cost' },
        { tier: 't3', name: 'ballportal_rough',    kind: 'cost' },
        { tier: 't3', name: 'vanguards',           kind: 'cost' },
        // row 4
        { tier: 't4', name: 'accumulators',        kind: 'cost' },
        { tier: 't4', name: 'parapet',             kind: 'cost' },
        // row 5
        { tier: 't5', name: 'saveprogress',        kind: 'cost' },
        { tier: 't5', name: 'forwardoutpost',      kind: 'cost' },
        { tier: 't5', name: 'phalanx',             kind: 'cost' },
        // row 6
        { tier: 't6', name: 'forwardmedics',       kind: 'cost' },
        { tier: 't6', name: 'maximumpressure',     kind: 'cost' },
        { tier: 't6', name: 'multiball',           kind: 'cost' },
    ],
    // TODO: row 5 has 3 boxes, only 1 named cfg entry (focusedtraining) -
    // need the other 2 names or confirmation 2 boxes are stray in TREE_NODES.
    GOALIE_TREE_EMPOWERMENT: [
        // row 1
        { tier: 't2', name: 'sharpshooter',        kind: 'use'  },
        // row 2
        { tier: 't1', name: 'combinecontract',     kind: 'cost' },
        // row 3
        { tier: 't3', name: 'grit',                kind: 'cost' },
        { tier: 't3', name: 'marksmanship',        kind: 'cost' },
        { tier: 't3', name: 'footwork',            kind: 'cost' },
        { tier: 't3', name: 'discipline',          kind: 'cost' },
        // row 4
        { tier: 't4', name: 'forecheck',           kind: 'cost' },
        { tier: 't4', name: 'fuelreserves',        kind: 'cost' },
        { tier: 't4', name: 'heroportals',         kind: 'cost' },
        // row 5 - TODO see above
        { tier: 't5', name: 'focusedtraining',     kind: 'cost' },
        { tier: 't5', name: 'energysurge',         kind: 'use'  },
        { tier: 't5', name: 'secondwind',          kind: 'use'  },
        // row 6
        { tier: 't6', name: 'dragonsbreath',       kind: 'cost' },
        { tier: 't6', name: 'apexform',            kind: 'cost' },
        { tier: 't6', name: 'bannerofcommand',     kind: 'cost' },
    ],
    // TODO: "manainfusion" key/cost unconfirmed - using .mana by assumption.
    GOALIE_TREE_CULTIVATION: [
        // row 1
        { tier: 't2', name: 'manainfusion',        kind: 'use',  currency: 'mana' }, // UNCONFIRMED
        { tier: 't5', name: 'manasurge',           kind: 'use',  currency: 'mana' },
        { tier: 't5', name: 'manasummon',          kind: 'use',  currency: 'mana' },
        // row 2
        { tier: 't1', name: 'manawell',            kind: 'cost' }, // gold
        // row 3
        { tier: 't3', name: 'manacompounding',     kind: 'cost' }, // gold
        { tier: 't3', name: 'highermanacap',       kind: 'cost' }, // gold
        { tier: 't3', name: 'tollcollector',       kind: 'cost' }, // gold
        // row 4
        { tier: 't4', name: 'manavines',           kind: 'cost', currency: 'mana' },
        { tier: 't4', name: 'manafrenzy',          kind: 'cost', currency: 'mana' },
        // row 5
        { tier: 't5', name: 'manapollinate',       kind: 'cost', currency: 'mana' },
        { tier: 't5', name: 'riskadjustedreturn',  kind: 'cost', currency: 'mana' },
        { tier: 't5', name: 'tripledown',          kind: 'cost', currency: 'mana' },
        // row 6
        { tier: 't6', name: 'wallportals',         kind: 'cost', currency: 'mana' },
        { tier: 't6', name: 'uninhibitedportal',   kind: 'cost', currency: 'mana' },
        { tier: 't6', name: 'iceportal',           kind: 'cost', currency: 'mana' },
    ]
};

// Single source of truth for tab geometry - hud.js (drawing) and mouse.js
// (hit-testing) BOTH import this, never redeclare it.
export const tabNames  = ["Siege", "Fortress", "Empowerment", "Cultivation"];
export const tabColors = ["#ff6b6b", "#3b82f6", "Goldenrod", "Violet"];
export const tabKeys   = ["GOALIE_TREE_SIEGE", "GOALIE_TREE_FORTRESS", "GOALIE_TREE_EMPOWERMENT", "GOALIE_TREE_CULTIVATION"];
export const tabCount  = tabKeys.length;
export const tabWidth  = 180;
export const tabHeight = 48;
export const spacing   = 14;

const TREE_SHORT_NAME = {
    GOALIE_TREE_SIEGE: "siege",
    GOALIE_TREE_FORTRESS: "fortress",
    GOALIE_TREE_EMPOWERMENT: "empowerment",
    GOALIE_TREE_CULTIVATION: "cultivation"
};


const REQUIRED_TECHS = {
    siege:       [1, 0, 2, 1, 1],
    fortress:    [1, 0, 2, 1, 1],
    empowerment: [1, 0, 2, 1, 1],
    cultivation: [1, 0, 2, 1, 1],
};  

function tierUnlocked(shortName, tier, treeState) {
    const n = parseInt(tier.slice(1), 10); // "t3" -> 3
    if (n <= 1) return true;

    const prevTier = 't' + (n - 1);
    if (!tierUnlocked(shortName, prevTier, treeState)) return false;

    const required = REQUIRED_TECHS[shortName][n - 2];
    return (treeState.tierProgress[prevTier] || 0) >= required;
}

export function isNodeUnlocked(activeKey, idx, treeState) {
    const def = getNodeDef(activeKey, idx);
    if (!def) return false;
    return tierUnlocked(TREE_SHORT_NAME[activeKey], def.tier, treeState);
}

export function getNodeDef(activeKey, idx) {
    return NODE_DEFS[activeKey] && NODE_DEFS[activeKey][idx];
}

export function getNodeConfigKey(activeKey, idx) {
    const def = getNodeDef(activeKey, idx);
    if (!def) return null;
    const shortName = TREE_SHORT_NAME[activeKey];
    return `${shortName}.${def.tier}.${def.name}`;
}


export function getTreeState(game, team, activeKey) {
    // 1. Resolve the correct server array based on the player's team representation
    const isHome = team === 0 || team === 'HOME';
    const rawUpgrades = isHome ? game.homeGoaliePurchasedUpgrades : game.awayGoaliePurchasedUpgrades;
    
    // 2. Convert the array to a Set for the UI's .has() lookups
    const purchased = new Set(rawUpgrades || []);

    // 3. Calculate how many nodes have been bought in each tier for THIS specific tree
    const tierProgress = {};
    const shortName = TREE_SHORT_NAME[activeKey];
    const defs = NODE_DEFS[activeKey];

    if (defs) {
        defs.forEach(def => {
            const nodeKey = `${shortName}.${def.tier}.${def.name}`;
            // Only 'cost' upgrades count toward unlocking the next tier.
            // 'use' nodes are repeatable abilities, not structural unlocks.
            if (def.kind === 'cost' && purchased.has(nodeKey)) {
                tierProgress[def.tier] = (tierProgress[def.tier] || 0) + 1;
            }
        });
    }

    return { purchased, tierProgress };
}

function drawOverlayIcon(ctx, src, x, y, w, h, alpha) {
    const img = AssetManager.images[src];
    if (!img) return;
    const size = Math.min(w, h) * 0.6;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.drawImage(img, x + (w - size) / 2, y + (h - size) / 2, size, size);
    ctx.restore();
}

export function drawHud(ctx, game, state) {
    if (!game) return;
    ctx.globalCompositeOperation = "source-over";

    // Draw Health bars above entities
    const entities = [...(game.players || []), ...(game.entityPool || [])];

    for (const e of entities) {
        if (e.health > 0 && e.entityClass !== 'LaneMinion') {
            const invisible = game.underControl && game.underControl.team !== e.team &&
                              game.effectPool && game.effectPool.effects.some(ef => ef.effect === 'STEALTHED' && ef.on && ef.on.id === e.id);
            if (invisible) continue;

            const hpPercent = e.health / e.maxHealth;
            let xOffset = (e.team === 'AWAY') ? -21 : -25;

            ctx.fillStyle = e.team === 'HOME' ? 'blue' : 'white';
            const x = Math.floor(e.X + xOffset - state.camX);
            const y = Math.floor(e.Y - 13 - state.camY);

            // Background
            ctx.fillRect(x, y, 100, 15);

            // Foreground
            ctx.fillStyle = getHpColor(hpPercent * 100);
            ctx.fillRect(x, Math.floor(e.Y - 10 - state.camY), Math.floor(hpPercent * 100), 9);

            if (e.entityClass !== 'Wall' && e.entityClass !== 'Trap') {
                if (e.fuel !== undefined) {
                    ctx.fillStyle = e.fuel > 25 ? 'rgb(128,128,255)' : 'darkred';
                    ctx.fillRect(x, Math.floor(e.Y - 4 - state.camY), Math.floor(e.fuel), 3);
                }
            }
        }
    }

    // Draw Scores
    ctx.font = 'bold 30px Arial';
    if (game.home) {
        ctx.fillStyle = '#3b82f6'; // Home team blue
        ctx.fillText(`HOME: ${game.home.score}`, 50, 50);
    }
    if (game.away) {
        ctx.fillStyle = '#ffffff'; // Away team white
        ctx.fillText(`AWAY: ${game.away.score}`, CONSTANTS.X_RES - 200, 50);
    }

    // Draw Game Timer
    const fps = 1000 / (game.GAMETICK_MS || 25);
    const timeSec = game.framesSinceStart / fps;
    const sdTime = (game.options && game.options.suddenDeathIndex ? game.options.suddenDeathIndex * 60 : 240);

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
    ctx.fillText(timeStr, CONSTANTS.X_RES / 2, 50);
    ctx.restore();

    // Timer Warnings
    let bottomText = "";
    let warningColor = "rgba(0, 255, 0, 0.4)";
    const WARN = 30, FWARN = 10;
    const gTime = game.GOALIE_DISABLE_TIME || 120;
    const tieTime = (game.options && game.options.tieIndex ? game.options.tieIndex * 60 : 300);

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
                }

                if (eff.effect !== 'ATTACKED') {
                    const iconImg = AssetManager.images[`EFFECT_${eff.effect}`];
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

            ctx.fillStyle = 'black';
            ctx.fillText(bannerText, CONSTANTS.X_RES / 2 + 3, CONSTANTS.Y_RES / 2 - 100 + 3);

            ctx.fillStyle = bannerColor;
            ctx.fillText(bannerText, CONSTANTS.X_RES / 2, CONSTANTS.Y_RES / 2 - 100);
            ctx.restore();
        }

        // Draw Goalie Currency (Gold)
        if (game.underControl && game.underControl.type === 'GOALIE') {
            ctx.save();
            ctx.fillStyle = 'rgba(10, 26, 20, 0.8)';
            ctx.strokeStyle = '#ff9f1c';
            ctx.lineWidth = 2;
            ctx.beginPath();
            if (ctx.roundRect) {
                ctx.roundRect(50, CONSTANTS.Y_RES - 160, 220, 50, 8);
            } else {
                ctx.rect(50, CONSTANTS.Y_RES - 160, 220, 50);
            }
            ctx.fill();
            ctx.stroke();

            ctx.fillStyle = '#ff9f1c';
            ctx.beginPath();
            ctx.arc(75, CONSTANTS.Y_RES - 135, 12, 0, 2 * Math.PI);
            ctx.fill();

            ctx.fillStyle = '#000000';
            ctx.font = 'bold 14px Arial';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('$', 75, CONSTANTS.Y_RES - 135);

            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold 20px Arial';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';
            const amt = Math.floor(game.underControl.team === 'HOME' ? (game.homeGoalieCurrency || 0) : (game.awayGoalieCurrency || 0));
            ctx.fillText(`Gold: ${amt}`, 100, CONSTANTS.Y_RES - 135);
            ctx.restore();
        }
    }

    if (game.underControl && game.underControl.type === 'GOALIE') {
        ctx.save();
        ctx.globalAlpha = 1.0;
        ctx.globalCompositeOperation = "source-over";
        ctx.filter = "none";

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
                        const treeState = getTreeState(game, game.underControl.team, activeKey);

                        nodes.forEach((coords, idx) => {
                            const [x1, y1, x2, y2] = coords;
                            const boxX = ox + (x1 * scaleX);
                            const boxY = oy + (y1 * scaleY);
                            const boxW = (x2 - x1) * scaleX;
                            const boxH = (y2 - y1) * scaleY;

                            const def = getNodeDef(activeKey, idx);
                            if (!def) return; // no definition for this box - draw nothing rather than guess

                            const nodeKey = getNodeConfigKey(activeKey, idx);

                            // Star: purchased, one-time ('cost' kind) upgrade. 'use'
                            // nodes are repeatable and never starred.
                            if (def.kind === 'cost' && treeState.purchased.has(nodeKey)) {
                                drawOverlayIcon(ctx, 'star', boxX, boxY, boxW, boxH, 1.0);
                                return;
                            }

                            if (!isNodeUnlocked(activeKey, idx, treeState)) {
                                drawOverlayIcon(ctx, 'lock', boxX, boxY, boxW, boxH, 1.0);
                            }
                            // else: prereqs met, unpurchased (or repeatable) -> no overlay, still clickable.
                        });
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
}

function getHpColor(percent) {
    if (percent > 66) return 'green';
    if (percent > 33) return 'yellow';
    return 'red';
}