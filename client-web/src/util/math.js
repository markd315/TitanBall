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
        // Center player on the screen. Screen resolution is 1920x1080.
        // Center of screen is 960 (half of 1920), and 540 (half of 1080).
        state.camX = Math.floor(game.underControl.X + 35 - 960);
        if (state.camX < 0) state.camX = 0;
        if (state.camX > 128) state.camX = 128; // Clamp to avoid showing black space on right
        
        state.camY = Math.floor(game.underControl.Y + 35 - 540);
        if (state.camY < 0) state.camY = 0;
        if (state.camY > 0) state.camY = 0; // Since screen height matches field height
    }
}
