import { getControlledTitan } from '../input/mobile.js';

export function degreesFromCoords(dx, dy) {
    let angle = Math.atan2(dy, dx) * 180 / Math.PI;
    if (angle < 0) {
        angle += 360;
    }
    return angle;
}

export function distance(x1, y1, x2, y2) {
    return Math.hypot(x2 - x1, y2 - y1);
}

export function updateCamera(game, state) {
    const myTitan = (game && game.underControl) || getControlledTitan(game);
    if (state.camFollow && myTitan) {
        if (myTitan.type === 'GOALIE') {
            state.camX = (myTitan.team === 'AWAY') ? 188 : 0;
            state.camY = 130;
            return;
        }
        // Center player on the screen. Screen resolution is 1920x960.
        // Center of screen is 960 (half of 1920), and 480 (half of 960).
        // Clamp based on 2108x1214 field texture dimensions (2108 - 1920 = 188)
        state.camX = Math.max(0, Math.min(188, Math.floor(myTitan.X + 35 - 960)));
        state.camY = 130; // Set camera Y offset to compress top scorebug header space symmetrically
    }
}
