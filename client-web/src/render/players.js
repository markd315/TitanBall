import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';

let _playerLogThrottle = 0;

export function drawPlayers(ctx, game, camX, camY) {
    if (!game || !game.players) {
        const now = Date.now();
        if (now - _playerLogThrottle > 2000) {
            console.warn('[DIAG] drawPlayers: game or game.players is null/undefined. game=', game);
            _playerLogThrottle = now;
        }
        return;
    }

    for (let i = 0; i < game.players.length; i++) {
        const t = game.players[i];
        
        // Skip rendering if stealthed and not on our team (simplified invisible check)
        const invisible = game.underControl && game.underControl.team !== t.team && 
                          game.effectPool && game.effectPool.effects.some(e => e.effect === 'STEALTHED' && e.on && e.on.id === t.id);
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
            ctx.drawImage(img, Math.floor(t.X - camX), Math.floor(t.Y - camY), t.width || 70, t.height || 70);
        } else {
            // Log sprite miss — throttled to avoid spam
            const now = Date.now();
            if (now - _playerLogThrottle > 2000) {
                console.warn(
                    `[DIAG] Sprite MISS for player ${i}: key='${spriteKey}' | type='${t.type}' | action='${action}' | facing='${facing}' | actionState='${t.actionState}'\n` +
                    `  Available keys matching '${t.type}_':`, Object.keys(AssetManager.images).filter(k => k.startsWith(t.type + '_'))
                );
                _playerLogThrottle = now;
            }
            // Draw fallback colored rect so position is visible
            ctx.save();
            ctx.fillStyle = t.team === 'HOME' ? 'rgba(59,130,246,0.7)' : 'rgba(239,68,68,0.7)';
            ctx.fillRect(Math.floor(t.X - camX), Math.floor(t.Y - camY), t.width || 70, t.height || 70);
            ctx.fillStyle = 'white';
            ctx.font = '10px monospace';
            ctx.textAlign = 'center';
            ctx.fillText(t.type || '?', Math.floor(t.X - camX) + 35, Math.floor(t.Y - camY) + 38);
            ctx.restore();
        }
    }
}
