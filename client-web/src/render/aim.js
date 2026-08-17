import { CONSTANTS } from '../constants.js';

export function drawAimAndRangeIndicators(ctx, game, controlsHeld, camX, camY) {
    if (!game || !game.underControl) return;

    const t = game.underControl;
    ctx.save();

    // 1. If player has possession of the ball, draw aiming lines and curves
    if (t.possession === 1 && game.ball) {
        const pow = t.throwPower || 0.1;
        
        // Cursor position in field space
        const mx = controlsHeld.posX + camX;
        const my = controlsHeld.posY + camY;
        
        // Ball center in field space
        const bx = game.ball.X + game.ball.width / 2;
        const by = game.ball.Y + game.ball.height / 2;
        
        // Angle from ball center to cursor
        const angle = Math.atan2(my - by, mx - bx);
        
        // Screen coordinates of ball center
        const ox = bx - camX;
        const oy = by - camY;
        
        const LOB_DIST = 230;
        const SHOT_DIST = 316;
        const BALL_HALF = 15;
        
        const drawLineSegment = (startVal, endVal, color) => {
            const sx = ox + startVal * Math.cos(angle);
            const sy = oy + startVal * Math.sin(angle);
            const ex = ox + endVal * Math.cos(angle);
            const ey = oy + endVal * Math.sin(angle);
            
            ctx.beginPath();
            ctx.moveTo(sx, sy);
            ctx.lineTo(ex, ey);
            ctx.strokeStyle = color;
            ctx.lineWidth = 30; // SHOT_WIDTH
            ctx.lineCap = 'round';
            ctx.stroke();
        };

        let gravityMult = 1.0;
        if (t.team === 'HOME' && game.homeLowGravityActive) {
            gravityMult = 1.5;
        } else if (t.team === 'AWAY' && game.awayLowGravityActive) {
            gravityMult = 1.5;
        }

        let noFlyMult = 1.0;
        if (t.team === 'HOME') {
            if (game.awayNoFlyZoneActive && t.X >= 1368.0 && t.X <= 2012.0) {
                noFlyMult = 0.5;
            }
        } else if (t.team === 'AWAY') {
            if (game.homeNoFlyZoneActive && t.X >= 36.0 && t.X <= 680.0) {
                noFlyMult = 0.5;
            }
        }

        let parapetMult = 1.0;
        if (game.entityPool) {
            for (const e of game.entityPool) {
                if (e.entityClass === 'Parapet') {
                    const px = t.X;
                    const py = t.Y;
                    const pw = t.width || 70;
                    const ph = t.height || 70;
                    const ex = e.X;
                    const ey = e.Y;
                    const ew = e.width || 100;
                    const eh = e.height || 100;
                    if (px < ex + ew && px + pw > ex && py < ey + eh && py + ph > ey) {
                        parapetMult = 1.5;
                        break;
                    }
                }
            }
        }

        const lobDistVal = LOB_DIST * pow * gravityMult * noFlyMult * parapetMult;
        
        // Yellow lob block: 0 to 0.2 * lobDistVal + BALL_HALF
        drawLineSegment(0, 0.2 * lobDistVal + BALL_HALF, 'rgba(255, 230, 0, 0.65)');
        
        // Blue lob fly: 0.2 * lobDistVal + BALL_HALF to 0.75 * lobDistVal - BALL_HALF
        drawLineSegment(0.2 * lobDistVal + BALL_HALF, 0.75 * lobDistVal - BALL_HALF, 'rgba(0, 80, 255, 0.65)');
        
        // Yellow lob catch: 0.75 * lobDistVal - BALL_HALF to lobDistVal
        drawLineSegment(0.75 * lobDistVal - BALL_HALF, lobDistVal, 'rgba(255, 230, 0, 0.65)');

        // Dark red shot line (drawn if not Artisan, or if Artisan artisanShot is SHOT)
        const isArtisan = t.type === 'ARTISAN';
        const artisanShot = controlsHeld.artisanShot || 'SHOT';
        
        if (artisanShot === 'SHOT' || !isArtisan) {
            drawLineSegment(lobDistVal, SHOT_DIST * pow, 'rgba(180, 0, 0, 0.65)');
        }

        // Artisan QuadCurves
        if (isArtisan) {
            const Q_CURVE_A = 310;
            const Q_CURVE_B = 186;
            const qCurveAVal = Q_CURVE_A * pow;
            const qCurveBVal = Q_CURVE_B * pow;
            
            const drawArtisanCurve = (curveAngleOffset, color) => {
                const cx = ox + qCurveAVal * Math.cos(angle + curveAngleOffset);
                const cy = oy + qCurveAVal * Math.sin(angle + curveAngleOffset);
                const ex = ox + qCurveBVal * Math.cos(angle);
                const ey = oy + qCurveBVal * Math.sin(angle);
                
                ctx.beginPath();
                ctx.moveTo(ox, oy);
                ctx.quadraticCurveTo(cx, cy, ex, ey);
                ctx.strokeStyle = color;
                ctx.lineWidth = 30;
                ctx.lineCap = 'round';
                ctx.stroke();
            };

            if (artisanShot === 'LEFT') {
                drawArtisanCurve(-0.97, 'rgba(0, 180, 80, 0.65)'); // Green
            } else if (artisanShot === 'RIGHT') {
                drawArtisanCurve(0.97, 'rgba(160, 0, 160, 0.65)'); // Purple
            }
        }
    } else {
        // 2. If player does NOT possess the ball, draw their turquoise steal radius circle
        const stealRad = t.stealRad || 70;
        const cx = t.X + t.width / 2 - camX;
        const cy = t.Y + t.height / 2 - camY;
        
        ctx.beginPath();
        ctx.arc(cx, cy, stealRad, 0, Math.PI * 2);
        ctx.strokeStyle = 'rgba(64, 224, 208, 0.4)'; // Turquoise
        ctx.lineWidth = 3;
        ctx.stroke();
    }

    // 3. Draw active range indicator circles from the server
    if (t.rangeIndicators) {
        for (const ri of t.rangeIndicators) {
            if (ri.radius > 0) {
                const color = ri.colorArray || [0.5, 0.5, 0.5, 1.0];
                
                // Don't show Artisan Suck circle if player has the ball
                const isArtisanSuck = t.type === 'ARTISAN' && t.possession === 1 && color[1] > 0.98;
                if (!isArtisanSuck) {
                    const radius = ri.radius * (t.rangeFactor || 1.0);
                    const cx = t.X + t.width / 2 - camX;
                    const cy = t.Y + t.height / 2 - camY;
                    
                    ctx.beginPath();
                    ctx.arc(cx, cy, radius, 0, Math.PI * 2);
                    ctx.strokeStyle = `rgba(${Math.floor(color[0]*255)}, ${Math.floor(color[1]*255)}, ${Math.floor(color[2]*255)}, ${color[3] * 0.45})`;
                    ctx.lineWidth = 3;
                    ctx.stroke();
                }
            }
        }
    }

    ctx.restore();
}
