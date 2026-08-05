export function degreesFromCoords(dx, dy) {
    let angle = Math.atan2(dy, dx) * 180 / Math.PI;
    if (angle < 0) {
        angle += 360;
    }
    return angle;
}

export function distance(x1, y1, x2, y2) {
    return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
}

export function updateCamera(game, state) {
    if (state.camFollow && game && game.underControl) {
        // scl is 1.5, scaling factor from legacy code. 1920/3/1.5*1.5 = 640
        state.camX = game.underControl.X + 35 - 640;
        if (state.camX < 0) state.camX = 0;
        state.camY = game.underControl.Y + 35 - 360;
        if (state.camY < 0) state.camY = 0;
    }
}
