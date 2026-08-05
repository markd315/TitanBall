import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';

export function drawCredits(ctx) {
    ctx.fillStyle = 'white';
    ctx.font = '65px Verdana';
    ctx.fillText('Space to proceed', 370, 640);
}