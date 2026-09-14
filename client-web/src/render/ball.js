import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';
import { CONSTANTS } from '../constants.js';
import { getControlledTitan } from '../input/mobile.js';

let ballFrameCounter = 0;
let prevBallX = null;
let prevBallY = null;
let lastBallMoveTime = 0;

function isBallActive(game) {
    if (!game || !game.ball) return false;

    // 1. Being held by any player
    if (game.players && game.players.some(p => p.possession === 1)) {
        return true;
    }

    // 2. Active shot or lob action state on any player
    if (game.players && game.players.some(p => 
        p.actionState === 'SHOOT' || 
        p.actionState === 'LOB' || 
        p.actionState === 'CURVE_LEFT' || 
        p.actionState === 'CURVE_RIGHT'
    )) {
        return true;
    }

    // 3. Ball lob mode
    if (ballLobMode(game)) {
        return true;
    }

    // 4. Non-zero kick power from shot/lob
    if ((game.xKickPow && Math.abs(game.xKickPow) > 0.01) || (game.yKickPow && Math.abs(game.yKickPow) > 0.01)) {
        return true;
    }

    // 5. Ball is moving across coordinates (in flight or rolling)
    const now = Date.now();
    if (prevBallX !== null && prevBallY !== null) {
        const distMoved = Math.hypot(game.ball.X - prevBallX, game.ball.Y - prevBallY);
        if (distMoved > 0.5) {
            lastBallMoveTime = now;
        }
    }
    prevBallX = game.ball.X;
    prevBallY = game.ball.Y;

    if (now - lastBallMoveTime < 150) {
        return true;
    }

    return false;
}

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

    const active = isBallActive(game);
    if (active) {
        ballFrameCounter = (ballFrameCounter + 1) % 20;
    } else {
        ballFrameCounter = 0;
    }
    const isFrameB = ballFrameCounter > 10;
    const imgKey = isFrameB ? 'ballB' : 'ballA';

    const holder = game.players && game.players.find(p => p.possession === 1);
    const isLob = ballLobMode(game);

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
    if (!game || !game.ballVisible || !game.ball) return;

    const holder = game.players && game.players.find(p => p.possession === 1);
    const myTitan = game.underControl || getControlledTitan(game);
    const isOpponentPossession = Boolean(holder && myTitan && !isSameTeam(holder, myTitan));

    const ptrImg = isOpponentPossession 
        ? AssetManager.images['ballFPtr'] 
        : AssetManager.images['ballPtr'];

    if (!ptrImg || !ptrImg.width || !ptrImg.height) return;

    const isGoalie = Boolean(myTitan && myTitan.type === 'GOALIE');
    const ballCenterX = game.ball.X + (game.ball.width || 30) / 2;
    const ballCenterY = game.ball.Y + (game.ball.height || 30) / 2;

    const cx = camX || 0;
    const cy = camY || 0;
    const screenX = isGoalie ? (ballCenterX - cx) * 0.9375 : (ballCenterX - cx);
    const screenY = ballCenterY - cy;

    const halfW = ptrImg.width / 2;
    const halfH = ptrImg.height / 2;

    // Keep indicators visible on screen and avoid corner overlap
    const margin = halfW + 10;
    const clampedX = Math.max(margin, Math.min(CONSTANTS.X_RES - margin, screenX));
    const clampedY = Math.max(margin, Math.min(CONSTANTS.Y_RES - margin, screenY));

    function drawPointer(x, y, angleDeg) {
        ctx.save();
        ctx.translate(x, y);
        if (angleDeg !== 0) {
            ctx.rotate(angleDeg * Math.PI / 180);
        }
        ctx.drawImage(ptrImg, -halfW, -halfH);
        ctx.restore();
    }

    // 1. Left border: unaltered (points right, tracks ball Y)
    drawPointer(halfW, clampedY, 0);

    // 2. Right border: rotated 180 deg (points left, tracks ball Y)
    drawPointer(CONSTANTS.X_RES - halfW, clampedY, 180);

    // 3. Top border: rotated 90 deg (points down, tracks ball X)
    drawPointer(clampedX, halfW, 90);

    // 4. Bottom border: rotated 270 deg (points up, tracks ball X)
    drawPointer(clampedX, CONSTANTS.Y_RES - halfW, 270);
}
