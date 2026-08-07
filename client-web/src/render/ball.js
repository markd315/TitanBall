import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';
import { CONSTANTS } from '../constants.js';

let ballFrameCounter = 0;

function ballLobMode(game) {
    if (!game || !game.players) return false;
    for (const p of game.players) {
        if (p.actionState === 'LOB' && p.actionFrame >= 3 && p.actionFrame <= 8) {
            return true;
        }
    }
    return false;
}

export function drawBall(ctx, game, camX, camY) {
    if (!game || !game.ballVisible) return;

    ballFrameCounter = (ballFrameCounter + 1) % 20;
    const isFrameB = ballFrameCounter > 10;

    const anyPoss = game.players && game.players.some(p => p.possession === 1);
    const isLob = ballLobMode(game);
    
    let imgKey = 'ballA';
    if (anyPoss) {
        imgKey = isFrameB ? 'ballB' : 'ballA';
    } else {
        imgKey = isFrameB ? 'ballFB' : 'ballFA';
    }

    if (AssetManager.images[imgKey]) {
        const img = AssetManager.images[imgKey];
        const size = isLob ? 45 : 30;
        const offset = isLob ? -7.5 : 0;
        ctx.drawImage(img, Math.floor(game.ball.X + offset - camX), Math.floor(game.ball.Y + offset - camY), size, size);
    }
}

export function displayBallArrow(ctx, game, camX, camY) {
    if (!game || !game.ballVisible) return;

    const x = game.ball.X + game.ball.width / 2 - camX;
    const y = game.ball.Y + game.ball.height / 2 - camY;
    
    const ptrImg = game.players && game.players.some(p => p.possession === 1) 
        ? AssetManager.images['ballPtr'] 
        : AssetManager.images['ballFPtr'];

    if (!ptrImg) return;

    let rot = null;
    let drawX = x, drawY = y;

    // Check if off-screen
    if (x < 0) {
        rot = 180;
        if (y < 0) { rot = 225; drawY = 20; }
        else if (y > CONSTANTS.Y_RES) { rot = 135; drawY = CONSTANTS.Y_RES - 20; }
        drawX = 20;
    } else if (x > CONSTANTS.X_RES) {
        rot = 0;
        if (y < 0) { rot = 315; drawY = 20; }
        else if (y > CONSTANTS.Y_RES) { rot = 45; drawY = CONSTANTS.Y_RES - 20; }
        drawX = CONSTANTS.X_RES - 20;
    } else if (y < 0) {
        rot = 270;
        drawY = 20;
    } else if (y > CONSTANTS.Y_RES) {
        rot = 90;
        drawY = CONSTANTS.Y_RES - 20;
    }

    if (rot !== null) {
        ctx.save();
        ctx.translate(drawX, drawY);
        ctx.rotate(rot * Math.PI / 180);
        ctx.drawImage(ptrImg, -ptrImg.width/2, -ptrImg.height/2);
        ctx.restore();
    }
}
