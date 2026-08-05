import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';

export function drawPlayers(ctx, game, camX, camY) {
    if (!game || !game.players) return;

    for (let i = 0; i < game.players.length; i++) {
        const t = game.players[i];
        
        // Skip rendering if stealthed and not on our team (simplified invisible check)
        const invisible = game.underControl && game.underControl.team !== t.team && 
                          game.effectPool && game.effectPool.effects.some(e => e.id === 'STEALTHED' && e.onId === t.id);
        if (invisible) continue;

        let facing = (t.facing >= 90 && t.facing < 270) ? 'L' : 'R';
        let action = 'stand';
        
        if (t.actionState === 'IDLE') {
            if (t.runningFrame === 1) action = 'runA';
            else if (t.runningFrame === 2) action = 'runB';
            else action = 'stand';
        } else if (t.actionState === 'SHOOT') {
            action = 'shot';
        } else if (t.actionState === 'LOB') {
            action = 'pass';
        } else if (t.actionState === 'A1' || t.actionState === 'STEAL') {
            action = 'atk1';
        } else if (t.actionState === 'A2') {
            action = 'atk2';
        }

        const spriteKey = `${t.type}_${action}${facing}`;
        const img = AssetManager.images[spriteKey] || AssetManager.images[`${t.type}_stand${facing}`];

        // Apply team color tint via globalCompositeOperation if desired, or just draw
        // (Vanilla canvas tinting is slow unless pre-rendered, so we'll just draw for now)
        if (img) {
            drawImageCam(ctx, img, t.X, t.Y, camX, camY);
        }
    }
}
