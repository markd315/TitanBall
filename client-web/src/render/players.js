import { gameState } from '../state.js';
import { drawImageCam } from './canvas.js';
import { AssetManager } from '../assets/sprites.js';

let _playerLogThrottle = 0;
let playersTintCanvas = null;

// Track visual states across ticks (death persistence and action animation hold)
const playerVisualStates = new Map();
const ACTION_HOLD_MS = 300; // Hold action animations for 300ms so they are clearly visible
const DEATH_PERSIST_MS = 1800; // Render dead sprite at death location for 1.8 seconds

export function drawPlayers(ctx, game, camX, camY) {
    if (!game || !game.players) {
        const now = Date.now();
        if (now - _playerLogThrottle > 2000) {
            console.warn('[DIAG] drawPlayers: game or game.players is null/undefined. game=', game);
            _playerLogThrottle = now;
        }
        return;
    }

    const now = Date.now();

    for (let i = 0; i < game.players.length; i++) {
        const t = game.players[i];
        const playerId = t.id ? t.id.toString() : `p_${i}`;
        
        let pState = playerVisualStates.get(playerId);
        if (!pState) {
            pState = {
                lastAliveX: t.X,
                lastAliveY: t.Y,
                lastFacing: (t.facing >= 90 && t.facing < 270) ? 'L' : 'R',
                lastType: t.type || (i === 0 || i === 1 ? 'GOALIE' : 'WARRIOR'),
                lastWidth: t.width || 70,
                lastHeight: t.height || 70,
                activeAction: null,
                actionStartTime: 0,
                deathTimestamp: 0,
                wasDead: false,
            };
            playerVisualStates.set(playerId, pState);
        }

        // Check if player is currently dead (server sets X/Y to 9499999 or health <= 0 or DEAD effect)
        const hasDeadEffect = game.effectPool && game.effectPool.effects.some(e => e.effect === 'DEAD' && e.on && e.on.id === t.id);
        const isDead = t.actionState === 'DEAD' || (t.health !== undefined && t.health <= 0 && (t.maxHealth === undefined || t.maxHealth > 0)) || t.X > 500000 || hasDeadEffect;

        if (isDead) {
            if (!pState.wasDead) {
                pState.wasDead = true;
                pState.deathTimestamp = now;
                pState.activeAction = null;
            }

            const elapsed = now - pState.deathTimestamp;
            if (elapsed < DEATH_PERSIST_MS) {
                const deathSpriteKey = `${pState.lastType}_die${pState.lastFacing}`;
                const deathImg = AssetManager.images[deathSpriteKey] || 
                                 AssetManager.images[`${pState.lastType}_death${pState.lastFacing}`] || 
                                 AssetManager.images[`${pState.lastType}_stand${pState.lastFacing}`];

                if (deathImg) {
                    const isUnified = deathImg.isUnified === true;
                    const baseW = pState.lastWidth || 70;
                    const baseH = pState.lastHeight || 70;
                    const width = isUnified ? Math.round(baseW * 1.3) : baseW;
                    const height = baseH;
                    const drawX = isUnified ? Math.floor(pState.lastAliveX - camX - (width - baseW) / 2) : Math.floor(pState.lastAliveX - camX);
                    const drawY = Math.floor(pState.lastAliveY - camY);

                    ctx.save();
                    if (elapsed > 1200) {
                        ctx.globalAlpha = Math.max(0, 1 - (elapsed - 1200) / 600);
                    }
                    ctx.drawImage(
                        deathImg,
                        drawX,
                        drawY,
                        width,
                        height
                    );
                    ctx.restore();
                }
            }
            continue;
        }

        // If entity is offscreen due to anticheat censoring (e.g. X = 99999 for stealthed enemy), skip without death animation
        if (t.X > 50000) {
            continue;
        }

        // Player is alive - update last known on-field coordinates and facing
        pState.wasDead = false;
        pState.deathTimestamp = 0;
        pState.lastAliveX = t.X;
        pState.lastAliveY = t.Y;
        pState.lastFacing = (t.facing >= 90 && t.facing < 270) ? 'L' : 'R';
        pState.lastType = t.type || (i === 0 || i === 1 ? 'GOALIE' : 'WARRIOR');
        pState.lastWidth = t.width || 70;
        pState.lastHeight = t.height || 70;

        // Check if stealthed
        const isStealthed = Boolean(
            game.effectPool && Array.isArray(game.effectPool.effects) &&
            t.id &&
            game.effectPool.effects.some(e => e && e.effect === 'STEALTHED' && e.on && e.on.id && e.on.id.toString() === t.id.toString()) &&
            !game.effectPool.effects.some(e => e && e.effect === 'FLARE' && e.on && e.on.id && e.on.id.toString() === t.id.toString())
        );

        // Skip rendering if stealthed and on enemy team
        const isEnemy = Boolean(game.underControl && game.underControl.team && t.team && game.underControl.team !== t.team);
        if (isStealthed && isEnemy) continue;

        let facing = pState.lastFacing;

        const isControlled = game.underControl && game.underControl.id === t.id;

        // Determine action with animation hold window so quick actions (steal, abilities) remain visible
        let triggerAction = null;
        if (t.actionState === 'SHOOT' || t.actionState === 'CURVE_LEFT' || t.actionState === 'CURVE_RIGHT') {
            triggerAction = 'shot';
        } else if (t.actionState === 'LOB') {
            triggerAction = 'pass';
        } else if (t.actionState === 'STEAL') {
            triggerAction = 'steal';
        } else if (t.actionState === 'A1') {
            triggerAction = 'atk1';
        } else if (t.actionState === 'A2') {
            triggerAction = 'atk2';
        }
 
         // Check for newly applied cooldown effects on ALL players (enemies, allies, remote clients)
         if (game.effectPool && game.effectPool.effects) {
             for (let eIdx = 0; eIdx < game.effectPool.effects.length; eIdx++) {
                 const ef = game.effectPool.effects[eIdx];
                 if (ef.on && ef.on.id === t.id) {
                     if (ef.effect === 'COOLDOWN_Q') {
                         const cdKey = ef.id || ef.duration || 1;
                         if (pState.lastCdQ !== cdKey && (ef.percentLeft === undefined || ef.percentLeft > 80)) {
                             pState.lastCdQ = cdKey;
                             triggerAction = 'atk1';
                         }
                     } else if (ef.effect === 'COOLDOWN_W') {
                         const cdKey = ef.id || ef.duration || 1;
                         if (pState.lastCdW !== cdKey && (ef.percentLeft === undefined || ef.percentLeft > 80)) {
                             pState.lastCdW = cdKey;
                             triggerAction = 'atk2';
                         }
                     } else if (ef.effect === 'COOLDOWN_STEAL') {
                         const cdKey = ef.id || ef.duration || 1;
                         if (pState.lastCdSteal !== cdKey && (ef.percentLeft === undefined || ef.percentLeft > 80)) {
                             pState.lastCdSteal = cdKey;
                             triggerAction = 'steal';
                         }
                     }
                 }
             }
         }
 
         // Clean up lastCd tracking when effects expire so repeated casts trigger animations
         if (pState.lastCdQ && (!game.effectPool || !game.effectPool.effects.some(e => e.effect === 'COOLDOWN_Q' && e.on && e.on.id === t.id))) {
             pState.lastCdQ = null;
         }
         if (pState.lastCdW && (!game.effectPool || !game.effectPool.effects.some(e => e.effect === 'COOLDOWN_W' && e.on && e.on.id === t.id))) {
             pState.lastCdW = null;
         }
         if (pState.lastCdSteal && (!game.effectPool || !game.effectPool.effects.some(e => e.effect === 'COOLDOWN_STEAL' && e.on && e.on.id === t.id))) {
             pState.lastCdSteal = null;
         }
 
         // Check if player just released the ball (throw/shot/pass)
         if (pState.prevPossession === 1 && t.possession === 0 && t.actionState !== 'DEAD' && t.health > 0) {
             triggerAction = 'shot';
         }
         pState.prevPossession = t.possession;
 
         if (isControlled && gameState.controlsHeld) {
             // Client-side local prediction for controlled player
             if (gameState.controlsHeld.E) {
                 triggerAction = 'atk1';
             } else if (gameState.controlsHeld.R) {
                 triggerAction = 'atk2';
             } else if (gameState.controlsHeld.STEAL) {
                 triggerAction = 'steal';
             } else if (gameState.controlsHeld.shotBtn) {
                 triggerAction = 'shot';
             } else if (gameState.controlsHeld.lobBtn) {
                 triggerAction = 'pass';
             }
         }
 
         if (triggerAction) {
             pState.activeAction = triggerAction;
             pState.actionStartTime = now;
         } else if (pState.activeAction && (now - pState.actionStartTime >= ACTION_HOLD_MS)) {
             pState.activeAction = null;
         }
 
         if (pState.prevX === undefined) {
             pState.prevX = t.X;
             pState.prevY = t.Y;
             pState.lastMovedTime = 0;
         }

        const isRootedOrActing = (t.actionState && t.actionState !== 'IDLE') || 
            (game.effectPool && game.effectPool.effects && game.effectPool.effects.some(e => (e.effect === 'ROOT' || e.effect === 'STUN' || e.effect === 'STEAL' || e.effect === 'CAST_LAG') && e.on && e.on.id === t.id));

        const distMoved = Math.hypot(t.X - pState.prevX, t.Y - pState.prevY);
        if (distMoved > 0.5 && distMoved < 50 && !isRootedOrActing) {
            pState.lastMovedTime = now;
        }
        pState.prevX = t.X;
        pState.prevY = t.Y;

        // Maintain moving state across the 25-50ms packet interval (150ms window) so 60fps rendering is seamless
        const isMoving = !isRootedOrActing && ((now - pState.lastMovedTime < 150) || (t.runningFrame === 1 || t.runningFrame === 2));

        let action = 'stand';
        if (pState.activeAction) {
            action = pState.activeAction;
        } else if (isStealthed) {
            action = 'atk1'; // Phasing / stealth sprite frame
        } else if (t.actionState === 'IDLE' && !isRootedOrActing) {
            if (isMoving && (now - pState.lastMovedTime < 200)) {
                // Purely client-side smooth alternation between runA and runB (180ms each)
                const runStep = Math.floor(now / 180) % 2;
                action = runStep === 0 ? 'runA' : 'runB';
            } else {
                action = 'stand';
            }
        }

        const playerType = pState.lastType;
        let spriteKey = `${playerType}_${action}${facing}`;
        
        if (playerType === 'GOALIE') {
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
        
        let img = AssetManager.images[spriteKey];
        if (!img) {
            if (action === 'steal') {
                img = AssetManager.images[`${playerType}_atk1${facing}`];
            } else if (action === 'pass') {
                img = AssetManager.images[`${playerType}_shot${facing}`];
            } else if (action === 'die') {
                img = AssetManager.images[`${playerType}_stand${facing}`];
            }
        }
        if (!img) {
            img = AssetManager.images[`${playerType}_stand${facing}`];
        }

        // Apply team color tint via globalCompositeOperation if desired, or just draw
        // (Vanilla canvas tinting is slow unless pre-rendered, so we'll just draw for now)
        if (img) {
            const isUnified = img.isUnified === true;
            const baseW = t.width || 70;
            const baseH = t.height || 70;
            const width = isUnified ? Math.round(baseW * 1.3) : baseW;
            const height = baseH;
            const drawX = isUnified ? Math.floor(t.X - camX - (width - baseW) / 2) : Math.floor(t.X - camX);
            const drawY = Math.floor(t.Y - camY);

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
                playersTintCanvas.width = width;
                playersTintCanvas.height = height;
                const tCtx = playersTintCanvas.getContext('2d');
                tCtx.globalCompositeOperation = 'source-over';
                tCtx.clearRect(0, 0, width, height);
                tCtx.drawImage(img, 0, 0, width, height);
                tCtx.globalCompositeOperation = 'source-in';
                
                const rgbMap = {
                    yellow: '255, 235, 59',
                    green: '34, 197, 94',
                    red: '239, 68, 68'
                };
                const rgb = rgbMap[color] || '255, 235, 59';
                
                // Gradient tint: 70% strength at bottom, fading to 20% at top
                const grad = tCtx.createLinearGradient(0, 0, 0, height);
                grad.addColorStop(0, `rgba(${rgb}, 0.20)`);
                grad.addColorStop(1, `rgba(${rgb}, 0.70)`);
                tCtx.fillStyle = grad;
                tCtx.fillRect(0, 0, width, height);
                tCtx.globalCompositeOperation = 'source-over';
                
                // Draw base player sprite
                ctx.save();
                if (isStealthed) {
                    ctx.globalAlpha = 0.55;
                }
                ctx.drawImage(img, drawX, drawY, width, height);
                ctx.restore();
                
                // Draw pulsing gradient tint on top to subtly highlight controlled character
                const time = Date.now() / 200;
                const pulse = (0.3 + 0.2 * Math.sin(time)) * (isStealthed ? 0.6 : 1.0);
                ctx.save();
                ctx.globalAlpha = pulse;
                ctx.drawImage(playersTintCanvas, drawX, drawY, width, height);
                ctx.restore();
            } else {
                ctx.save();
                if (isStealthed) {
                    ctx.globalAlpha = 0.55;
                }
                ctx.drawImage(img, drawX, drawY, width, height);
                ctx.restore();
            }
        } else {
            // Log sprite miss — throttled to avoid spam
            const now = Date.now();
            if (now - _playerLogThrottle > 2000) {
                console.warn(
                    `[DIAG] Sprite MISS for player ${i}: key='${spriteKey}' | type='${playerType}' | action='${action}' | facing='${facing}' | actionState='${t.actionState}'\n` +
                    `  Available keys matching '${playerType}_':`, Object.keys(AssetManager.images).filter(k => k.startsWith(playerType + '_'))
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
            ctx.fillText(playerType, Math.floor(t.X - camX) + 35, Math.floor(t.Y - camY) + 38);
            ctx.restore();
        }
    }
}
