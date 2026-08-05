import { drawImageCam } from './canvas.js';
import { CONSTANTS } from '../constants.js';
import { updateCamera } from '../util/math.js';
import { drawPlayers } from './players.js';
import { drawMinions } from './minions.js';
import { drawBall, displayBallArrow } from './ball.js';
import { drawGoals } from './goals.js';

export function drawHud(ctx, game, state) {
    if (!game) return;
    
    // Draw Health bars above entities
    const entities = [...(game.players || []), ...(game.entityPool || [])];
    
    for (const e of entities) {
        if (e.health > 0) {
            const invisible = state.underControl && state.underControl.team !== e.team && 
                              game.effectPool && game.effectPool.effects.some(ef => ef.id === 'STEALTHED' && ef.onId === e.id);
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
            
            if (e.type !== 'Wall' && e.type !== 'Trap') {
                if (e.fuel !== undefined) {
                    ctx.fillStyle = e.fuel > 25 ? 'rgb(128,128,255)' : 'darkred';
                    ctx.fillRect(x, Math.floor(e.Y - 4 - state.camY), Math.floor(e.fuel), 3);
                }
            }
        }
    }

    // Draw Scores
    ctx.fillStyle = 'white';
    ctx.font = '30px Arial';
    if (game.home) {
        ctx.fillText(`HOME: ${game.home.score}`, 50, 50);
    }
    if (game.away) {
        ctx.fillText(`AWAY: ${game.away.score}`, CONSTANTS.X_RES - 200, 50);
    }
}

function getHpColor(percent) {
    if (percent > 66) return 'green';
    if (percent > 33) return 'yellow';
    return 'red';
}
