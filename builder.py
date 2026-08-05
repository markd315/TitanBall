import os
import sys

files = {}

files['client-web/src/constants.js'] = """export const GamePhase = {
    CREDITS: 'CREDITS',
    CONTROLS: 'CONTROLS',
    SHOW_GAME_MODES: 'SHOW_GAME_MODES',
    DRAW_CLASS_SCREEN: 'DRAW_CLASS_SCREEN',
    SET_MASTERIES: 'SET_MASTERIES',
    TRANSITIONAL: 'TRANSITIONAL',
    WAIT_FOR_GAME: 'WAIT_FOR_GAME',
    CANNOT_JOIN: 'CANNOT_JOIN',
    COUNTDOWN: 'COUNTDOWN',
    INGAME: 'INGAME',
    SCORE_FREEZE: 'SCORE_FREEZE',
    ENDED: 'ENDED'
};

export const CONSTANTS = {
    X_RES: 1920,
    Y_RES: 1080
};"""

files['client-web/src/state.js'] = """import { GamePhase } from './constants.js';

export const gameState = {
    phase: GamePhase.CREDITS,
    game: null,
    controlsHeld: {
        gameID: null,
        token: null,
        camX: 0,
        camY: 0,
        posX: 0,
        posY: 0,
        keys: [],
        classSelection: null,
        masteries: null
    },
    camX: 0,
    camY: 0,
    token: null,
    gameID: null
};"""

files['client-web/src/main.js'] = """import { gameState } from './state.js';
import { initCanvas, clearScreen } from './render/canvas.js';
import { drawCredits } from './screens/credits.js';
import { initKeyboard } from './input/keyboard.js';
import { initMouse } from './input/mouse.js';
import { GamePhase } from './constants.js';

let ctx;
let lastTime = 0;

function gameLoop(timestamp) {
    const dt = timestamp - lastTime;
    lastTime = timestamp;

    clearScreen(ctx);

    switch (gameState.phase) {
        case GamePhase.CREDITS:
            drawCredits(ctx);
            break;
        default:
            ctx.fillStyle = 'white';
            ctx.font = '30px Arial';
            ctx.fillText('Phase: ' + gameState.phase, 100, 100);
            break;
    }

    requestAnimationFrame(gameLoop);
}

export function start() {
    ctx = initCanvas();
    initKeyboard();
    initMouse();
    requestAnimationFrame(gameLoop);
}

start();"""

files['client-web/src/render/canvas.js'] = """import { CONSTANTS } from '../constants.js';

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
}"""

files['client-web/src/screens/credits.js'] = """import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';

export function drawCredits(ctx) {
    ctx.fillStyle = 'white';
    ctx.font = '65px Verdana';
    ctx.fillText('Space to proceed', 370, 640);
}"""

files['client-web/src/input/keyboard.js'] = """import { gameState } from '../state.js';
import { GamePhase } from '../constants.js';

export function initKeyboard() {
    window.addEventListener('keydown', (e) => {
        if (e.code === 'Space') {
            if (gameState.phase === GamePhase.CREDITS) {
                gameState.phase = GamePhase.SHOW_GAME_MODES;
            }
        }
    });
}"""

files['client-web/src/input/mouse.js'] = """import { gameState } from '../state.js';

export function initMouse() {
    const canvas = document.getElementById('gameCanvas');
    canvas.addEventListener('mousemove', (e) => {
        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const scaleY = canvas.height / rect.height;
        gameState.controlsHeld.posX = (e.clientX - rect.left) * scaleX;
        gameState.controlsHeld.posY = (e.clientY - rect.top) * scaleY;
    });
}"""

files['client-web/src/network/auth.js'] = """export async function login() {
    console.log("Login stub");
}"""

files['client-web/src/network/socket.js'] = """export function connectGame() {
    console.log("Connect stub");
}"""

for k, v in files.items():
    with open(k, 'w') as f:
        f.write(v)
