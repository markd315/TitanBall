export function drawGoals(ctx, game, camX, camY) {
    if (!game) return;

    ctx.lineWidth = 6;
    
    const drawHoop = (hoops, isHigh) => {
        if (!hoops) return;
        for (const goal of hoops) {
            ctx.strokeStyle = goal.team === 'HOME' ? 'blue' : 'red';
            if (!goal.checkReady) {
                ctx.strokeStyle = goal.frozen ? 'skyblue' : 'red';
            }
            if (isHigh) {
                ctx.fillStyle = 'darkgray';
            }
            
            // Draw ellipse
            ctx.beginPath();
            ctx.ellipse(
                goal.X - camX, 
                goal.Y - camY, 
                goal.width / 2, 
                goal.height / 2, 
                0, 0, Math.PI * 2
            );
            if (isHigh) ctx.fill();
            ctx.stroke();
        }
    };

    drawHoop(game.lowGoals, false);
    drawHoop(game.hiGoals, true);
}
