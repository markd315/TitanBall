import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';

let staticFrame = 0;

export function drawMinions(ctx, game, camX, camY) {
    if (!game || !game.entityPool) return;
    
    staticFrame = (staticFrame + 1) % 60; // 60 frames per cycle roughly
    const isAltFrame = staticFrame > 30;

    for (let i = 0; i < game.entityPool.length; i++) {
        const e = game.entityPool[i];
        let imgKey = null;

        const isCooldown = e.cdUntilEpochMs && game.nowEpochMs < e.cdUntilEpochMs;

        if (e.entityClass === 'Wall') imgKey = 'wall';
        else if (e.entityClass === 'Trap') imgKey = isAltFrame ? 'trap2' : 'trap1';
        else if (e.entityClass === 'BallPortal') imgKey = isCooldown ? 'bportalcd' : (isAltFrame ? 'bportal2' : 'bportal1');
        else if (e.entityClass === 'Portal') imgKey = isCooldown ? 'portalcd' : (isAltFrame ? 'portal2' : 'portal1');
        else if (e.entityClass === 'Fire') imgKey = isAltFrame ? 'fire2' : 'fire1';
        else if (e.entityClass === 'Cage') imgKey = 'cage';
        else if (e.entityClass === 'Wolf') {
            const face = e.facingRight ? 'R' : 'L';
            let pwr = e.wolfPower;
            if (pwr > 2 && pwr < 5) pwr = 3;
            if (pwr >= 5) pwr = 5;
            imgKey = `wolf${pwr}${face}`;
        }

        if (imgKey && AssetManager.images[imgKey]) {
            ctx.drawImage(AssetManager.images[imgKey], Math.floor(e.X - camX), Math.floor(e.Y - camY), e.width || 70, e.height || 70);

            // Draw cooldown progress bar for portals
            if ((e.entityClass === 'Portal' || e.entityClass === 'BallPortal') && isCooldown) {
                const totalCd = e.entityClass === 'Portal' ? 10000 : 6000;
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
}
