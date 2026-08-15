import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';

let _playerLogThrottle = 0;
let playersTintCanvas = null;

export function drawPlayers(ctx, game, camX, camY) {
    if (!game || !game.players) {
        const now = Date.now();
        if (now - _playerLogThrottle > 2000) {
            console.warn('[DIAG] drawPlayers: game or game.players is null/undefined. game=', game);
            _playerLogThrottle = now;
        }
        return;
    }

    for (let i = 0; i < game.players.length; i++) {
        const t = game.players[i];
        
        // Skip rendering if stealthed and not on our team (simplified invisible check)
        const invisible = game.underControl && game.underControl.team !== t.team && 
                          game.effectPool && game.effectPool.effects.some(e => e.effect === 'STEALTHED' && e.on && e.on.id === t.id);
        if (invisible) continue;

        let facing = (t.facing >= 90 && t.facing < 270) ? 'L' : 'R';
        let action = 'stand';
        
        if (t.actionState === 'IDLE') {
            if (t.runningFrame === 1) action = 'runA';
            else if (t.runningFrame === 2) action = 'runB';
            else action = 'stand';
        } else if (t.actionState === 'SHOOT') {
            action = 'shot';
        } else if (t.actionState === 'LOB') {
            action = 'pass';
        } else if (t.actionState === 'A1' || t.actionState === 'STEAL') {
            action = 'atk1';
        } else if (t.actionState === 'A2') {
            action = 'atk2';
        }

        let spriteKey = `${t.type}_${action}${facing}`;
        
        if (t.type === 'GOALIE') {
            const goalieCdEffect = game.effectPool && game.effectPool.effects.find(ef => ef.effect === 'COOLDOWN_GOALIE' && ef.on && ef.on.id === t.id);
            if (goalieCdEffect) {
                if (goalieCdEffect.percentLeft > 90) {
                    spriteKey = `GOALIE_atk1${facing}`;   // was GOALIE_shoot
                } else {
                    spriteKey = `GOALIE_atk2${facing}`;   // was GOALIE_reload
                }
            } else {
                spriteKey = `GOALIE_stand${facing}`;
            }
        }
        const img = AssetManager.images[spriteKey] || AssetManager.images[`${t.type}_stand${facing}`];

        // Apply team color tint via globalCompositeOperation if desired, or just draw
        // (Vanilla canvas tinting is slow unless pre-rendered, so we'll just draw for now)
        if (img) {
            const isControlled = game.underControl && game.underControl.id === t.id;
            if (isControlled) {
                // Determine controlled player's color based on state:
                // Make it green if they are healing. Make it red if they are taking painHoop damage. Make it yellow normally.
                
                // 1. Check if they have the active HEAL effect
                const hasHealEffect = game.effectPool && game.effectPool.effects.some(e => e.effect === 'HEAL' && e.on && e.on.id === t.id);
                
                // 2. Check painHoop status
                let isPainHoopHealing = false;
                let isPainHoopDamaging = false;
                
                const enemyHiGoal = game.hiGoals && game.hiGoals.find(g => g.team !== t.team);
                if (enemyHiGoal) {
                    const cx = t.X + 35;
                    const cy = t.Y + 35;
                    const gx = enemyHiGoal.x + enemyHiGoal.w / 2;
                    const gy = enemyHiGoal.y + enemyHiGoal.h / 2;
                    const dx = cx - gx;
                    const dy = cy - gy;
                    const d = Math.sqrt(dx * dx + dy * dy);
                    const delta = (-3.96893 / 100000000.0) * Math.pow(d, 3)
                                + Math.pow(d, 2) * 0.0000603779
                                - (0.0326137) * d
                                + 6.92514;
                    
                    if (delta > 0) {
                        isPainHoopDamaging = true;
                    } else {
                        const teamHasPossession = game.players && game.players.some(p => p.possession === 1 && p.team === t.team);
                        if (!teamHasPossession) {
                            isPainHoopHealing = true;
                        }
                    }
                }
                
                let color = 'yellow';
                if (hasHealEffect || isPainHoopHealing) {
                    color = 'green';
                } else if (isPainHoopDamaging) {
                    color = 'red';
                }
                
                // Create/reuse offscreen canvas for tinting
                if (!playersTintCanvas) {
                    playersTintCanvas = document.createElement('canvas');
                }
                const width = t.width || 70;
                const height = t.height || 70;
                playersTintCanvas.width = width;
                playersTintCanvas.height = height;
                const tCtx = playersTintCanvas.getContext('2d');
                tCtx.clearRect(0, 0, width, height);
                tCtx.drawImage(img, 0, 0, width, height);
                tCtx.globalCompositeOperation = 'source-in';
                tCtx.fillStyle = color;
                tCtx.fillRect(0, 0, width, height);
                
                // Draw original with pulsing outer glow drop-shadow
                ctx.save();
                const time = Date.now() / 200;
                const glowSize = 4 + 4 * Math.sin(time);
                ctx.filter = `drop-shadow(0px 0px ${glowSize}px ${color})`;
                ctx.drawImage(img, Math.floor(t.X - camX), Math.floor(t.Y - camY), width, height);
                ctx.restore();
                
                // Draw pulsing tinted image on top to modify sprite texture color/hue
                const pulse = 0.3 + 0.2 * Math.sin(time); // ranges between 0.1 and 0.5
                ctx.save();
                ctx.globalAlpha = pulse;
                ctx.drawImage(playersTintCanvas, Math.floor(t.X - camX), Math.floor(t.Y - camY), width, height);
                ctx.restore();
            } else {
                ctx.drawImage(img, Math.floor(t.X - camX), Math.floor(t.Y - camY), t.width || 70, t.height || 70);
            }
        } else {
            // Log sprite miss — throttled to avoid spam
            const now = Date.now();
            if (now - _playerLogThrottle > 2000) {
                console.warn(
                    `[DIAG] Sprite MISS for player ${i}: key='${spriteKey}' | type='${t.type}' | action='${action}' | facing='${facing}' | actionState='${t.actionState}'\n` +
                    `  Available keys matching '${t.type}_':`, Object.keys(AssetManager.images).filter(k => k.startsWith(t.type + '_'))
                );
                _playerLogThrottle = now;
            }
            // Draw fallback colored rect so position is visible
            ctx.save();
            ctx.fillStyle = t.team === 'HOME' ? 'rgba(59,130,246,0.7)' : 'rgba(239,68,68,0.7)';
            ctx.fillRect(Math.floor(t.X - camX), Math.floor(t.Y - camY), t.width || 70, t.height || 70);
            ctx.fillStyle = 'white';
            ctx.font = '10px monospace';
            ctx.textAlign = 'center';
            ctx.fillText(t.type || '?', Math.floor(t.X - camX) + 35, Math.floor(t.Y - camY) + 38);
            ctx.restore();
        }
    }
}
