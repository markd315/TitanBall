/**
 * Pseudotexture and Pre-indicator Rendering Engine
 *
 * Procedurally draws styled circle grids, 3x3 nine-region barrage warning zones,
 * and visual telegraphs with 50% opacity for pre-indicators.
 */

export const BARRAGE_COLORS = {
    BASE: '#f5b642',
    FIRE: '#f54e42',
    ICE: '#42aaf5',
    PREINDICATOR: '#b8f9ff'
};

/**
 * Calculates world bounds for one of the 9 regions (3x3) in the defensive third.
 *
 * @param {'HOME' | 'AWAY' | 0 | 1} team
 * @param {number} regionIndex - 0 to 8 (row * 3 + col)
 * @returns {{x: number, y: number, width: number, height: number}}
 */
export function getBarrageRegionBounds(team, regionIndex) {
    const row = Math.floor(regionIndex / 3); // 0=top, 1=mid, 2=bot
    const col = regionIndex % 3; // 0, 1, 2

    // Y bounds: Row 0: 232..484, Row 1: 484..736, Row 2: 736..988 (Height 252 each, total 756, zero gaps)
    const y = 232 + row * 252;
    const height = 252;

    let x, width;
    if (team === 'HOME' || team === 0) {
        // HOME third: X in [36, 680] (width 644)
        if (col === 0) {
            x = 36; width = 214;
        } else if (col === 1) {
            x = 250; width = 215;
        } else {
            x = 465; width = 215;
        }
    } else {
        // AWAY third: X in [1368, 2012] (width 644)
        if (col === 0) {
            x = 1368; width = 215;
        } else if (col === 1) {
            x = 1583; width = 215;
        } else {
            x = 1798; width = 214;
        }
    }
    return { x, y, width, height };
}

/**
 * Draws a procedural grid of circles over a rectangular area.
 *
 * @param {CanvasRenderingContext2D} ctx
 * @param {number} x - World X
 * @param {number} y - World Y
 * @param {number} width - Area width
 * @param {number} height - Area height
 * @param {string} colorHex - Hex color string
 * @param {boolean} isPreindicator - True if in pre-indicator phase (#b8f9ff)
 * @param {number} frameCount - Animation frame counter
 * @param {number} camX - Camera X offset
 * @param {number} camY - Camera Y offset
 */
export function drawCircleGrid(ctx, x, y, width, height, colorHex, isPreindicator, frameCount, camX, camY) {
    const rx = Math.floor(x - camX);
    const ry = Math.floor(y - camY);

    ctx.save();

    // Set 50% opacity for all pre-indicators
    if (isPreindicator) {
        ctx.globalAlpha = 0.50;
    }

    // 1. Semi-transparent background area fill
    ctx.fillStyle = isPreindicator ? 'rgba(184, 249, 255, 0.16)' : `${colorHex}1a`;
    ctx.fillRect(rx, ry, width, height);

    // 2. Outer boundary border
    ctx.lineWidth = isPreindicator ? 2.5 : 2;
    ctx.strokeStyle = isPreindicator ? '#b8f9ff' : colorHex;
    if (isPreindicator) {
        ctx.setLineDash([8, 6]);
        ctx.lineDashOffset = -(frameCount * 0.5) % 14;
    }
    ctx.strokeRect(rx, ry, width, height);
    ctx.setLineDash([]);

    // 3. Grid of circles
    const spacingX = 26;
    const spacingY = 26;
    const cols = Math.max(1, Math.floor(width / spacingX));
    const rows = Math.max(1, Math.floor(height / spacingY));
    const startOffsetX = (width - (cols - 1) * spacingX) / 2;
    const startOffsetY = (height - (rows - 1) * spacingY) / 2;

    const baseRadius = isPreindicator ? 5.0 : 6.0;

    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
            const cx = rx + startOffsetX + c * spacingX;
            const cy = ry + startOffsetY + r * spacingY;

            // Shimmer animation
            const wave = Math.sin(frameCount * 0.12 + (r * 0.4 + c * 0.3));
            const radius = Math.max(2, baseRadius + wave * 1.5);

            ctx.beginPath();
            ctx.arc(cx, cy, radius, 0, 2 * Math.PI);

            if (isPreindicator) {
                // Pre-indicator styling in #b8f9ff (with 50% opacity applied)
                const pulseAlpha = 0.70 + 0.30 * Math.sin(frameCount * 0.15 + (r + c) * 0.2);
                ctx.fillStyle = `rgba(184, 249, 255, ${Math.max(0.2, Math.min(1, pulseAlpha))})`;
                ctx.fill();
                ctx.strokeStyle = '#b8f9ff';
                ctx.lineWidth = 1.5;
                ctx.stroke();
            } else {
                // Active effect color styling (#f5b642, #f54e42, #42aaf5)
                const innerAlpha = 0.75 + 0.25 * wave;
                ctx.fillStyle = `${colorHex}${Math.floor(Math.max(0.3, Math.min(1, innerAlpha)) * 255).toString(16).padStart(2, '0')}`;
                ctx.fill();
                ctx.strokeStyle = colorHex;
                ctx.lineWidth = 1.5;
                ctx.stroke();
            }
        }
    }

    ctx.restore();
}

/**
 * Draws Barrage pseudotextures and telegraph pre-indicators for HOME and AWAY teams in a 3x3 nine-region layout.
 */
export function drawBarragePseudotextures(ctx, game, camX, camY) {
    if (!game) return;

    const frameCount = game.framesSinceStart || 0;

    const teamConfigs = [
        {
            team: 'HOME',
            activeRegions: game.homeActiveBarrageRegions || (game.homeGoalieAbilities && game.homeGoalieAbilities.activeBarrageRegions) || [],
            activeTypes: game.homeActiveBarrageTypes || (game.homeGoalieAbilities && game.homeGoalieAbilities.activeBarrageTypes) || [],
            pendingRegions: game.homePendingBarrageRegions || (game.homeGoalieAbilities && game.homeGoalieAbilities.pendingBarrageRegions) || [],
            pendingTypes: game.homePendingBarrageTypes || (game.homeGoalieAbilities && game.homeGoalieAbilities.pendingBarrageTypes) || [],
            purchased: game.homeGoaliePurchasedUpgrades ? new Set(game.homeGoaliePurchasedUpgrades) : new Set()
        },
        {
            team: 'AWAY',
            activeRegions: game.awayActiveBarrageRegions || (game.awayGoalieAbilities && game.awayGoalieAbilities.activeBarrageRegions) || [],
            activeTypes: game.awayActiveBarrageTypes || (game.awayGoalieAbilities && game.awayGoalieAbilities.activeBarrageTypes) || [],
            pendingRegions: game.awayPendingBarrageRegions || (game.awayGoalieAbilities && game.awayGoalieAbilities.pendingBarrageRegions) || [],
            pendingTypes: game.awayPendingBarrageTypes || (game.awayGoalieAbilities && game.awayGoalieAbilities.pendingBarrageTypes) || [],
            purchased: game.awayGoaliePurchasedUpgrades ? new Set(game.awayGoaliePurchasedUpgrades) : new Set()
        }
    ];

    for (const tc of teamConfigs) {
        if (!tc.purchased.has('fortress.t4.barrage')) continue;

        // 1. Draw Active Barrage regions in their respective effect colors
        if (tc.activeRegions && tc.activeRegions.length > 0) {
            for (let i = 0; i < tc.activeRegions.length; i++) {
                const regIdx = tc.activeRegions[i];
                const bType = (tc.activeTypes && tc.activeTypes[i]) || 'BASE';
                const bounds = getBarrageRegionBounds(tc.team, regIdx);

                let color = BARRAGE_COLORS.BASE;
                if (bType === 'FIRE') color = BARRAGE_COLORS.FIRE;
                else if (bType === 'ICE') color = BARRAGE_COLORS.ICE;

                drawCircleGrid(ctx, bounds.x, bounds.y, bounds.width, bounds.height, color, false, frameCount, camX, camY);
            }
        }

        // 2. Draw Pending Pre-indicator Barrage regions (#b8f9ff with 50% opacity)
        if (tc.pendingRegions && tc.pendingRegions.length > 0) {
            for (let i = 0; i < tc.pendingRegions.length; i++) {
                const regIdx = tc.pendingRegions[i];
                const bounds = getBarrageRegionBounds(tc.team, regIdx);
                drawCircleGrid(ctx, bounds.x, bounds.y, bounds.width, bounds.height, BARRAGE_COLORS.PREINDICATOR, true, frameCount, camX, camY);
            }
        }
    }
}

/**
 * Draws the Dragon's Breath pre-indicator square (#b8f9ff) with 50% opacity 2 seconds prior to spawn.
 * Only renders the clean square boundary and label (no circle grid particles).
 */
export function drawDragonPreIndicator(ctx, game, camX, camY) {
    if (!game) return;

    const hasBreath = (game.homeGoaliePurchasedUpgrades && Array.from(game.homeGoaliePurchasedUpgrades).includes('empowerment.t6.dragonsbreath')) ||
                      (game.awayGoaliePurchasedUpgrades && Array.from(game.awayGoaliePurchasedUpgrades).includes('empowerment.t6.dragonsbreath'));

    if (!hasBreath) return;

    // Check if dragon is currently alive
    const dragonAlive = game.dragonSpawned || (game.entityPool && game.entityPool.some(e => e.entityClass === 'Dragon' && e.health > 0));
    if (dragonAlive) return;

    // If pre-indicator is active (2 seconds before spawn)
    if (game.dragonPreIndicatorActive) {
        const dx = 1024 - 60; // 964
        const dy = 843 - 60;  // 783 (centered on bottom hoop Y=843.5)
        const dSize = 120;
        const frameCount = game.framesSinceStart || 0;

        const rx = Math.floor(dx - camX);
        const ry = Math.floor(dy - camY);

        ctx.save();
        ctx.globalAlpha = 0.50;

        // Semi-transparent background box
        ctx.fillStyle = 'rgba(184, 249, 255, 0.16)';
        ctx.fillRect(rx, ry, dSize, dSize);

        // Dashed border in #b8f9ff
        ctx.lineWidth = 2.5;
        ctx.strokeStyle = '#b8f9ff';
        ctx.setLineDash([8, 6]);
        ctx.lineDashOffset = -(frameCount * 0.5) % 14;
        ctx.strokeRect(rx, ry, dSize, dSize);
        ctx.setLineDash([]);

        // Label text in #b8f9ff
        const cx = rx + dSize / 2;
        const cy = ry + dSize / 2;
        ctx.fillStyle = '#b8f9ff';
        ctx.font = 'bold 13px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('DRAGON SPAWN', cx, cy);

        ctx.restore();
    }
}

/**
 * Draws pre-indicators for other telegraphed effects with 50% opacity (Portals opening, etc.)
 */
export function drawOtherPreIndicators(ctx, game, camX, camY) {
    if (!game || !game.entityPool) return;

    const frameCount = game.framesSinceStart || 0;

    // Portals ready in < 2 seconds
    for (const e of game.entityPool) {
        if ((e.entityClass === 'Portal' || e.entityClass === 'BallPortal') && e.cdUntilEpochMs) {
            const msLeft = e.cdUntilEpochMs - game.nowEpochMs;
            if (msLeft > 0 && msLeft <= 2000) {
                // Draw #b8f9ff pre-indicator ring with 50% opacity
                const px = Math.floor(e.X + (e.width || 70) / 2 - camX);
                const py = Math.floor(e.Y + (e.height || 70) / 2 - camY);
                const radius = ((e.width || 70) / 2) + 6 + 3 * Math.sin(frameCount * 0.2);

                ctx.save();
                ctx.globalAlpha = 0.50;
                ctx.beginPath();
                ctx.arc(px, py, Math.max(10, radius), 0, 2 * Math.PI);
                ctx.strokeStyle = '#b8f9ff';
                ctx.lineWidth = 2.5;
                ctx.setLineDash([4, 4]);
                ctx.stroke();
                ctx.restore();
            }
        }
    }
}

/**
 * Master rendering function for all pseudotextures and pre-indicators.
 */
export function drawAllPseudotextures(ctx, game, camX, camY) {
    drawBarragePseudotextures(ctx, game, camX, camY);
    drawDragonPreIndicator(ctx, game, camX, camY);
    drawOtherPreIndicators(ctx, game, camX, camY);
}
