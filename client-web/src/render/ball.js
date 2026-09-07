import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';
import { CONSTANTS } from '../constants.js';
import { getControlledTitan } from '../input/mobile.js';

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

function isHome(entity) {
    return Boolean(entity && (entity.team === 'HOME' || entity.team === 0));
}

function isAway(entity) {
    return Boolean(entity && (entity.team === 'AWAY' || entity.team === 1));
}

function isSameTeam(a, b) {
    if (!a || !b) return false;
    if (a.team !== undefined && b.team !== undefined) {
        if (a.team === b.team) return true;
        return (isHome(a) && isHome(b)) || (isAway(a) && isAway(b));
    }
    return false;
}

function isLocalPlayer(player, myTitan) {
    if (!player || !myTitan) return false;
    if (player.id !== undefined && myTitan.id !== undefined) {
        return player.id.toString() === myTitan.id.toString();
    }
    return player === myTitan;
}

export function drawBall(ctx, game, camX, camY) {
    if (!game || !game.ballVisible || !game.ball) return;

    ballFrameCounter = (ballFrameCounter + 1) % 20;
    const isFrameB = ballFrameCounter > 10;

    const holder = game.players && game.players.find(p => p.possession === 1);
    const anyPoss = Boolean(holder);
    const isLob = ballLobMode(game);
    
    let imgKey = 'ballA';
    if (anyPoss) {
        imgKey = isFrameB ? 'ballB' : 'ballA';
    } else {
        imgKey = isFrameB ? 'ballFB' : 'ballFA';
    }

    const size = isLob ? 45 : 30;
    const offset = isLob ? -7.5 : 0;
    const drawX = Math.floor(game.ball.X + offset - camX);
    const drawY = Math.floor(game.ball.Y + offset - camY);

    if (AssetManager.images[imgKey]) {
        const img = AssetManager.images[imgKey];
        ctx.drawImage(img, drawX, drawY, size, size);
    }

    if (holder) {
        const myTitan = game.underControl || getControlledTitan(game);
        let borderColor = null;
        if (myTitan) {
            if (isLocalPlayer(holder, myTitan)) {
                borderColor = '#ffff00'; // yellow when you have it
            } else if (isSameTeam(holder, myTitan)) {
                borderColor = '#22c55e'; // green when an ally has it
            } else {
                borderColor = '#ef4444'; // red when an enemy has it
            }
        }

        if (borderColor) {
            ctx.save();
            ctx.globalAlpha = 1.0;
            ctx.lineWidth = 4;
            ctx.strokeStyle = borderColor;
            ctx.beginPath();
            const radius = size / 2;
            const centerX = drawX + radius;
            const centerY = drawY + radius;
            ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
            ctx.stroke();
            ctx.restore();
        }
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
