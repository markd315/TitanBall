import { CONSTANTS } from '../constants.js';

export function initCanvas() {
    const canvas = document.getElementById('gameCanvas');
    canvas.width = CONSTANTS.X_RES;
    canvas.height = CONSTANTS.Y_RES;
    return canvas.getContext('2d');
}

export function clearScreen(ctx) {
    ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    ctx.fillStyle = 'black';
    ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
}

export function drawImageCam(ctx, img, x, y, camX, camY) {
    if (img) {
        ctx.drawImage(img, Math.floor(x - camX), Math.floor(y - camY));
    }
}