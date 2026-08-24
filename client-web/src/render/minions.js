import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';

let staticFrame = 0;

export function drawMinions(ctx, game, camX, camY) {
    if (!game || !game.entityPool) return;
    
    // Draw No-Fly Zone and Deep Freeze defensive aura overlays
    ctx.save();
    const homePurchased = game.homeGoaliePurchasedUpgrades ? Array.from(game.homeGoaliePurchasedUpgrades) : [];
    const awayPurchased = game.awayGoaliePurchasedUpgrades ? Array.from(game.awayGoaliePurchasedUpgrades) : [];

    // HOME defensive third: X from 36 to 680, Y from 36 to 1182
    if (game.homeNoFlyZoneActive) {
        ctx.fillStyle = "rgba(239, 68, 68, 0.08)";
        ctx.strokeStyle = "rgba(239, 68, 68, 0.35)";
        ctx.lineWidth = 3;
        ctx.fillRect(36 - camX, 36 - camY, 644, 1146);
        ctx.strokeRect(36 - camX, 36 - camY, 644, 1146);
    }
    // AWAY defensive third: X from 1368 to 2012, Y from 36 to 1182
    if (game.awayNoFlyZoneActive) {
        ctx.fillStyle = "rgba(239, 68, 68, 0.08)";
        ctx.strokeStyle = "rgba(239, 68, 68, 0.35)";
        ctx.lineWidth = 3;
        ctx.fillRect(1368 - camX, 36 - camY, 644, 1146);
        ctx.strokeRect(1368 - camX, 36 - camY, 644, 1146);
    }

    // HOME Deep Freeze
    if (game.homeDeepFreezeActive) {
        ctx.fillStyle = "rgba(59, 130, 246, 0.08)";
        ctx.strokeStyle = "rgba(59, 130, 246, 0.35)";
        ctx.lineWidth = 3;
        ctx.fillRect(36 - camX, 36 - camY, 644, 1146);
        ctx.strokeRect(36 - camX, 36 - camY, 644, 1146);
    }
    // AWAY Deep Freeze
    if (game.awayDeepFreezeActive) {
        ctx.fillStyle = "rgba(59, 130, 246, 0.08)";
        ctx.strokeStyle = "rgba(59, 130, 246, 0.35)";
        ctx.lineWidth = 3;
        ctx.fillRect(1368 - camX, 36 - camY, 644, 1146);
        ctx.strokeRect(1368 - camX, 36 - camY, 644, 1146);
    }
    ctx.restore();

    staticFrame = (staticFrame + 1) % 60; // 60 frames per cycle roughly
    const isAltFrame = staticFrame > 30;

    const isGoalie = game.underControl && game.underControl.type === 'GOALIE';

    // For field players, pre-calculate the frontmost minion X per lane per team to only render lead groups
    const homeLeadX = [-Infinity, -Infinity, -Infinity];
    const awayLeadX = [Infinity, Infinity, Infinity];
    if (!isGoalie) {
        for (let i = 0; i < game.entityPool.length; i++) {
            const e = game.entityPool[i];
            if (e.entityClass === 'LaneMinion' && e.health > 0) {
                const lane = e.laneIndex >= 0 && e.laneIndex < 3 ? e.laneIndex : 0;
                if (e.team === 'HOME') {
                    if (e.X > homeLeadX[lane]) homeLeadX[lane] = e.X;
                } else if (e.team === 'AWAY') {
                    if (e.X < awayLeadX[lane]) awayLeadX[lane] = e.X;
                }
            }
        }
    }

    for (let i = 0; i < game.entityPool.length; i++) {
        const e = game.entityPool[i];
        let imgKey = null;

        const isCooldown = e.cdUntilEpochMs && game.nowEpochMs < e.cdUntilEpochMs;

        if (e.entityClass === 'Wall') imgKey = 'wall';
        else if (e.entityClass === 'Parapet') {
            imgKey = (e.team === 'HOME' || e.team === 0) ? 'parapet_home' : 'parapet_away';
        }
        else if (e.entityClass === 'Trap') {
            const isVines = (e.width === 80 && e.height === 200) || e.width === 80;
            imgKey = isVines ? 'vines' : (isAltFrame ? 'trap2' : 'trap1');
        }
        else if (e.entityClass === 'BallPortal') imgKey = isCooldown ? 'bportalcd' : (isAltFrame ? 'bportal2' : 'bportal1');
        else if (e.entityClass === 'Portal') imgKey = isCooldown ? 'portalcd' : (isAltFrame ? 'portal2' : 'portal1');
        else if (e.entityClass === 'Fire') {
            const isBarrage = (e.height === 252 || e.height === 260 || (e.width === 150 && e.height === 280));
            if (!isBarrage) {
                const isHome = (e.team === 'HOME' || e.team === 0);
                const prefix = isHome ? 'fireH' : 'fireA';
                imgKey = isAltFrame ? `${prefix}2` : `${prefix}1`;
            }
        }
        else if (e.entityClass === 'Cage') imgKey = 'cage';
        else if (e.entityClass === 'Wolf') {
            const face = e.facingRight ? 'R' : 'L';
            let pwr = e.wolfPower;
            if (pwr > 2 && pwr < 5) pwr = 3;
            if (pwr >= 5) pwr = 5;
            imgKey = `wolf${pwr}${face}`;
        }
        else if (e.entityClass === 'Dragon') {
            imgKey = 'dragon';
        }
        else if (e.entityClass === 'SecondBall') {
            const isFrameB = (staticFrame % 20) > 10;
            const anyPoss = game.players && game.players.some(p => p.possession === 1);
            imgKey = anyPoss ? (isFrameB ? 'ballB' : 'ballA') : (isFrameB ? 'ballFB' : 'ballFA');
        }

        else if (e.entityClass === 'LaneMinion') {
            if (!isGoalie) {
                const lane = e.laneIndex >= 0 && e.laneIndex < 3 ? e.laneIndex : 0;
                const leadX = e.team === 'HOME' ? homeLeadX[lane] : awayLeadX[lane];
                // Do not draw minions that aren't in the lead group of minions
                if (e.team === 'HOME' && (leadX - e.X > 65.0)) continue;
                if (e.team === 'AWAY' && (e.X - leadX > 65.0)) continue;
            }

            ctx.save();
            ctx.globalAlpha = isGoalie ? 0.7 : 0.30;

            const radius = isGoalie ? 20 : 4;
            const mx = Math.floor(e.X - camX);
            const my = Math.floor(e.Y - camY);

            // Fill color: blue for HOME, white for AWAY
            ctx.beginPath();
            ctx.arc(mx, my, radius, 0, 2 * Math.PI);
            ctx.fillStyle = e.team === 'HOME' ? '#3b82f6' : '#ffffff';
            ctx.fill();

            // Border arc representing health (only for Guardians)
            if (isGoalie) {
                ctx.beginPath();
                const ratio = Math.max(0, Math.min(1, e.health / e.maxHealth));
                ctx.arc(mx, my, radius, -Math.PI / 2, -Math.PI / 2 + 2 * Math.PI * ratio);
                ctx.strokeStyle = '#000000';
                ctx.lineWidth = 3;
                ctx.stroke();
            }

            ctx.restore();
        }

        if (imgKey && AssetManager.images[imgKey]) {
            const isPhasedWall = (e.entityClass === 'Wall' && e.solid === false);
            if (isPhasedWall) {
                ctx.save();
                ctx.globalAlpha = 0.20;
            }
            const drawW = (e.entityClass === 'Parapet') ? 100 : (e.width || 70);
            const drawH = (e.entityClass === 'Parapet') ? 100 : (e.height || 70);
            ctx.drawImage(AssetManager.images[imgKey], Math.floor(e.X - camX), Math.floor(e.Y - camY), drawW, drawH);
            if (isPhasedWall) {
                ctx.restore();
            }

            // Draw cooldown progress bar for portals
            if ((e.entityClass === 'Portal' || e.entityClass === 'BallPortal') && isCooldown) {
                const totalCd = e.cooldownMs || (e.entityClass === 'Portal' ? 10000 : 2000);
                const msLeft = e.cdUntilEpochMs - game.nowEpochMs;
                const percent = Math.max(0, Math.min(100, (msLeft / totalCd) * 100));

                const barWidth = 33;
                const barHeight = 4;
                const xOffset = -5;
                const bx = Math.floor(e.X + xOffset - camX);
                const by = Math.floor(e.Y - 1 - camY);

                ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
                ctx.fillRect(bx, by, barWidth, barHeight);

                ctx.fillStyle = percent > 66 ? 'green' : (percent > 33 ? 'yellow' : 'red');
                ctx.fillRect(bx, by, Math.floor(barWidth * (percent / 100)), barHeight);
            }
        }
    }

    // Draw lane advantage indicators
    const LANE_YS = [354, 583, 790];
    const INDICATOR_X = 40; // fixed at far left of screen, behind the goals — not camera-relative

    function nearestLane(y) {
        let best = 0;
        let bestDist = Infinity;
        for (let L = 0; L < LANE_YS.length; L++) {
            const d = Math.abs(y - LANE_YS[L]);
            if (d < bestDist) {
                bestDist = d;
                best = L;
            }
        }
        return best;
    }

    const playerTeam = game.underControl ? game.underControl.team : 'HOME';
    const playerLane = game.underControl ? nearestLane(game.underControl.Y) : -1;

    let opposingGoalieLane = -1;
    for (let i = 0; i < game.players.length; i++) {
        const p = game.players[i];
        if (p && p.team !== playerTeam && p.type === 'GOALIE' && p.health > 0) {
            opposingGoalieLane = nearestLane(p.Y);
            break;
        }
    }

    for (let L = 0; L < 3; L++) {
        let homeCount = 0;
        let awayCount = 0;
        for (let i = 0; i < game.entityPool.length; i++) {
            const e = game.entityPool[i];
            if (e.entityClass === 'LaneMinion' && e.laneIndex === L && e.health > 0) {
                if (e.team === 'HOME') homeCount++;
                else if (e.team === 'AWAY') awayCount++;
            }
        }
        const homeBonus = game.homeLaneBonusValue ? game.homeLaneBonusValue[L] : 0;
        const awayBonus = game.awayLaneBonusValue ? game.awayLaneBonusValue[L] : 0;
        
        let netMinions = 0;
        let netBonus = 0;
        if (playerTeam === 'AWAY') {
            netMinions = awayCount - homeCount;
            netBonus = awayBonus - homeBonus;
        } else {
            netMinions = homeCount - awayCount;
            netBonus = homeBonus - awayBonus;
        }

        // Clamp base minion difference at soft-cap (10)
        if (netMinions > 10) netMinions = 10;
        if (netMinions < -10) netMinions = -10;

        let net = netMinions + netBonus;

        // Clamp total at hard-cap (20)
        if (net > 20) net = 20;
        if (net < -20) net = -20;

        const text = net >= 0 ? `+${net}` : `${net}`;

        ctx.save();
        ctx.fillStyle = 'rgba(10, 26, 20, 0.7)';
        const displayX = INDICATOR_X;
        const displayY = LANE_YS[L] - camY;
        ctx.beginPath();
        ctx.arc(displayX, displayY, 22, 0, 2 * Math.PI);
        ctx.fill();

        // Outline: gold for the player's own lane, red for the opposing goalie's lane (if different)
        if (L === playerLane) {
            ctx.lineWidth = 3;
            ctx.strokeStyle = '#ffd700';
            ctx.stroke();
        } else if (L === opposingGoalieLane) {
            ctx.lineWidth = 3;
            ctx.strokeStyle = '#ef4444';
            ctx.stroke();
        }

        // Display a small, scaled down fire sprite behind the number if abs(net) > 10
        if (Math.abs(net) > 10) {
            const isHomeDominating = playerTeam === 'AWAY' ? (net < 0) : (net > 0);
            const prefix = isHomeDominating ? 'fireH' : 'fireA';
            const fireImg = AssetManager.images[isAltFrame ? `${prefix}2` : `${prefix}1`];
            if (fireImg) {
                ctx.save();
                ctx.globalAlpha = 0.55;
                ctx.drawImage(fireImg, displayX - 16, displayY - 16, 32, 32);
                ctx.restore();
            }
        }

        // Flashing numbers if it is exactly 10 or -10
        let fillCol = net > 0 ? '#10b981' : (net < 0 ? '#ef4444' : '#ffffff');
        if (net === 10 || net === -10) {
            const flash = Math.floor(staticFrame / 8) % 2 === 0;
            if (flash) {
                fillCol = '#fbbf24'; // bright yellow amber flash
            }
        }

        ctx.fillStyle = fillCol;
        ctx.font = 'bold 18px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(text, displayX, displayY);
        ctx.restore();
    }
}
