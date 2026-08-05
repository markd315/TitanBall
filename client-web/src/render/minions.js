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

        if (e.type === 'Wall') imgKey = 'wall';
        else if (e.type === 'Trap') imgKey = isAltFrame ? 'trap2' : 'trap1';
        else if (e.type === 'BallPortal') imgKey = e.cooldown ? 'bportalcd' : (isAltFrame ? 'bportal2' : 'bportal1');
        else if (e.type === 'Portal') imgKey = e.cooldown ? 'portalcd' : (isAltFrame ? 'portal2' : 'portal1');
        else if (e.type === 'Fire') imgKey = isAltFrame ? 'fire2' : 'fire1';
        else if (e.type === 'Cage') imgKey = 'cage';
        else if (e.type === 'Wolf') {
            const face = e.facingRight ? 'R' : 'L';
            let pwr = e.wolfPower;
            if (pwr > 2 && pwr < 5) pwr = 3;
            if (pwr >= 5) pwr = 5;
            imgKey = `wolf${pwr}${face}`;
        }

        if (imgKey && AssetManager.images[imgKey]) {
            drawImageCam(ctx, AssetManager.images[imgKey], e.X, e.Y, camX, camY);
        }
    }
}
