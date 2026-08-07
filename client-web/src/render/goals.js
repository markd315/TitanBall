import { CONSTANTS } from '../constants.js';

export function drawGoals(ctx, game, camX, camY) {
    if (!game) return;

    ctx.save();
    ctx.lineWidth = 6;
    
    const drawHoop = (hoops, isHigh) => {
        if (!hoops) return;
        for (const goal of hoops) {
            let enemy = goal.team === 'HOME' ? game.away : game.home;
            
            // Set Stroke Color
            ctx.strokeStyle = 'lightgray';
            if (isHigh) {
                ctx.fillStyle = 'darkgray';
            }

            if (goal.onCooldown) {
                ctx.strokeStyle = goal.frozen ? 'skyblue' : 'red';
            } else {
                if (!isHigh) {
                    if (checkSuddenDeath('L', enemy, game)) {
                        ctx.strokeStyle = 'goldenrod';
                    } else if (enemy && (enemy.score % 1.0 === 0.75)) {
                        ctx.strokeStyle = 'darkviolet';
                    }
                } else {
                    if (checkSuddenDeath('H', enemy, game)) {
                        ctx.strokeStyle = 'goldenrod';
                    } else if (enemy && (enemy.score % 1.0 === 0.75)) {
                        ctx.strokeStyle = 'green';
                    }
                }
            }
            
            // Draw ellipse
            const cx = goal.x + goal.w / 2 - camX;
            const cy = goal.y + goal.h / 2 - camY;
            const rx = goal.w / 2;
            const ry = goal.h / 2;
            
            ctx.beginPath();
            ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
            if (isHigh) ctx.fill();
            ctx.stroke();
        }
    };

    drawHoop(game.lowGoals, false);
    drawHoop(game.hiGoals, true);

    // Draw Portal Ranges
    if (game.underControl && game.entityPool) {
        for (const e of game.entityPool) {
            if ((e.entityClass === 'Portal' || e.entityClass === 'BallPortal') && e.createdById === game.underControl.id) {
                if (e.rangeCircle) {
                    const radius = e.rangeCircle.radius;
                    const color = e.rangeCircle.colorArray || [0.5, 0.5, 0.5, 1.0];
                    
                    const cx = Math.floor(e.X + e.width / 2 - camX);
                    const cy = Math.floor(e.Y + e.height / 2 - camY);
                    
                    ctx.beginPath();
                    ctx.arc(cx, cy, radius, 0, Math.PI * 2);
                    ctx.strokeStyle = `rgba(${Math.floor(color[0]*255)}, ${Math.floor(color[1]*255)}, ${Math.floor(color[2]*255)}, ${color[3] * 0.4})`;
                    ctx.lineWidth = 2;
                    ctx.setLineDash([8, 8]); // dashed ring
                    ctx.stroke();
                    ctx.setLineDash([]); // restore solid lines
                }
            }
        }
    }
    
    ctx.restore();
}

function checkSuddenDeath(lOrH, enemy, game) {
    if (!enemy || !game || !game.options) return false;
    const SOFT_WIN = game.options.playToIndex || 5;
    const WIN_BY = game.options.winByIndex || 2;
    const HARD_WIN = game.options.hardWinIndex || 9999;
    
    let diff = 0.25;
    if (lOrH === 'H') {
        const fPart = enemy.score - Math.floor(enemy.score);
        diff = (fPart * 4 + 1) - fPart;
    }
    
    const simEnemyScore = enemy.score + diff;
    const simHomeScore = game.home ? (enemy === game.home ? simEnemyScore : game.home.score) : 0;
    const simAwayScore = game.away ? (enemy === game.away ? simEnemyScore : game.away.score) : 0;
    
    if (!game.suddenDeath) {
        if ((simHomeScore >= SOFT_WIN && simHomeScore - simAwayScore >= WIN_BY) || simHomeScore >= HARD_WIN) {
            return true;
        }
        if ((simAwayScore >= SOFT_WIN && simAwayScore - simHomeScore >= WIN_BY) || simAwayScore >= HARD_WIN) {
            return true;
        }
    } else {
        if (game.tieAble) {
            return true;
        } else {
            if (game.extremeSuddenDeath) {
                if (simHomeScore !== simAwayScore) return true;
            } else {
                if (Math.floor(simHomeScore) !== Math.floor(simAwayScore)) return true;
            }
        }
    }
    return false;
}
