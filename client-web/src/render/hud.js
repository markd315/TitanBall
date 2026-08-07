import { drawImageCam } from './canvas.js';
import { CONSTANTS } from '../constants.js';
import { AssetManager } from '../assets/sprites.js';

export function drawHud(ctx, game, state) {
    if (!game) return;
    
    // Draw Health bars above entities
    const entities = [...(game.players || []), ...(game.entityPool || [])];
    
    for (const e of entities) {
        if (e.health > 0) {
            const invisible = game.underControl && game.underControl.team !== e.team && 
                              game.effectPool && game.effectPool.effects.some(ef => ef.effect === 'STEALTHED' && ef.on && ef.on.id === e.id);
            if (invisible) continue;

            const hpPercent = e.health / e.maxHealth;
            let xOffset = (e.team === 'AWAY') ? -21 : -25;
            
            ctx.fillStyle = e.team === 'HOME' ? 'blue' : 'white';
            const x = Math.floor(e.X + xOffset - state.camX);
            const y = Math.floor(e.Y - 13 - state.camY);
            
            // Background
            ctx.fillRect(x, y, 100, 15);
            
            // Foreground
            ctx.fillStyle = getHpColor(hpPercent * 100);
            ctx.fillRect(x, Math.floor(e.Y - 10 - state.camY), Math.floor(hpPercent * 100), 9);
            
            if (e.entityClass !== 'Wall' && e.entityClass !== 'Trap') {
                if (e.fuel !== undefined) {
                    ctx.fillStyle = e.fuel > 25 ? 'rgb(128,128,255)' : 'darkred';
                    ctx.fillRect(x, Math.floor(e.Y - 4 - state.camY), Math.floor(e.fuel), 3);
                }
            }
        }
    }

    // Draw Scores
    ctx.font = 'bold 30px Arial';
    if (game.home) {
        ctx.fillStyle = '#3b82f6'; // Home team blue
        ctx.fillText(`HOME: ${game.home.score}`, 50, 50);
    }
    if (game.away) {
        ctx.fillStyle = '#ffffff'; // Away team white
        ctx.fillText(`AWAY: ${game.away.score}`, CONSTANTS.X_RES - 200, 50);
    }

    // Draw Game Timer (Counting down to Sudden Death, then counting up in red)
    const fps = 1000 / (game.GAMETICK_MS || 25);
    const timeSec = game.framesSinceStart / fps;
    const sdTime = (game.options && game.options.suddenDeathIndex ? game.options.suddenDeathIndex * 60 : 240);
    
    let displayTime = 0;
    let timerColor = '#00ff00'; // Green normal timer
    let isOvertime = false;
    
    if (timeSec < sdTime) {
        displayTime = sdTime - timeSec;
    } else {
        displayTime = timeSec - sdTime;
        timerColor = '#ff3b30'; // Red overtime timer
        isOvertime = true;
    }
    
    const timeRounded = Math.floor(displayTime * 10) / 10;
    const minutes = Math.floor(timeRounded / 60);
    const seconds = Math.floor(timeRounded % 60);
    const tenths = Math.floor((timeRounded * 10) % 10);
    let timeStr = `${minutes}:${seconds.toString().padStart(2, '0')}.${tenths}`;
    if (isOvertime) {
        timeStr = "SD " + timeStr;
    }
    
    ctx.save();
    ctx.textAlign = 'center';
    ctx.font = 'bold 36px Courier New';
    ctx.fillStyle = timerColor;
    ctx.fillText(timeStr, CONSTANTS.X_RES / 2, 50);
    ctx.restore();

    // Draw Timer Warnings (e.g. goalies vanished, sudden death)
    let bottomText = "";
    let warningColor = "rgba(0, 255, 0, 0.4)";
    const WARN = 30, FWARN = 10;
    const gTime = game.GOALIE_DISABLE_TIME || 120;
    const tieTime = (game.options && game.options.tieIndex ? game.options.tieIndex * 60 : 300);
    
    const timer = Math.floor(timeSec);
    const CHWARN = 10; // Show active rule changes for 10 seconds
    
    if (timer >= gTime - WARN && timer < gTime - FWARN) {
        warningColor = "rgba(230, 230, 0, 0.8)";
        bottomText = "GOALIES VANISHING WARNING";
    } else if (timer >= gTime - FWARN && timer < gTime) {
        warningColor = "rgba(255, 0, 0, 0.9)";
        bottomText = "GOALIES VANISHING WARNING";
    } else if (timer >= gTime && timer < gTime + CHWARN) {
        warningColor = "rgba(255, 0, 0, 1.0)";
        bottomText = "GOALIES VANISHED";
    } else if (timer >= sdTime - WARN && timer < sdTime - FWARN) {
        warningColor = "rgba(230, 230, 0, 0.8)";
        bottomText = "SUDDEN DEATH WARNING";
    } else if (timer >= sdTime - FWARN && timer < sdTime) {
        warningColor = "rgba(255, 0, 0, 0.9)";
        bottomText = "SUDDEN DEATH WARNING";
    } else if (timer >= sdTime && timer < sdTime + CHWARN) {
        warningColor = "rgba(255, 0, 0, 1.0)";
        bottomText = "SUDDEN DEATH ENABLED";
    } else if (timer >= tieTime - WARN && timer < tieTime - FWARN) {
        warningColor = "rgba(230, 230, 0, 0.9)";
        bottomText = "TIE GAME WARNING";
    } else if (timer >= tieTime - FWARN && timer < tieTime) {
        warningColor = "rgba(255, 0, 0, 0.9)";
        bottomText = "TIE GAME WARNING";
    } else if (timer >= tieTime && timer < tieTime + CHWARN) {
        warningColor = "rgba(255, 0, 0, 1.0)";
        bottomText = "TIE GAME";
    }
    
    if (bottomText) {
        ctx.save();
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        
        const isActiveState = (bottomText === "GOALIES VANISHED" || bottomText === "SUDDEN DEATH ENABLED" || bottomText === "TIE GAME");
        
        if (isActiveState) {
            ctx.font = 'bold 36px Verdana';
            // Subtle dark horizontal banner
            ctx.fillStyle = 'rgba(0, 0, 0, 0.65)';
            ctx.fillRect(0, 160, CONSTANTS.X_RES, 80);
            
            // Text stroke
            ctx.strokeStyle = 'black';
            ctx.lineWidth = 6;
            ctx.strokeText(bottomText, CONSTANTS.X_RES / 2, 200);
            
            // Text fill
            ctx.fillStyle = warningColor;
            ctx.fillText(bottomText, CONSTANTS.X_RES / 2, 200);
        } else {
            // Warning text below the timer
            ctx.font = 'bold 24px Verdana';
            ctx.fillStyle = warningColor;
            ctx.fillText(bottomText, CONSTANTS.X_RES / 2, 90);
        }
        ctx.restore();
    }

    // Draw Personal Cooldown / Status Effects in Bottom Left
    if (game.underControl && game.effectPool && game.effectPool.effects) {
        let xOffset = 50;
        const yOffset = CONSTANTS.Y_RES - 80;
        
        let bannerText = "";
        let bannerColor = "";
        
        game.effectPool.effects.forEach((eff) => {
            if (eff.on && eff.on.id === game.underControl.id) {
                // Determine screen banner
                if (eff.effect === 'ROOT') {
                    bannerText = "Rooted!";
                    bannerColor = 'rgba(92, 130, 71, 0.9)';
                } else if (eff.effect === 'SLOW') {
                    bannerText = "Slowed!";
                    bannerColor = 'rgba(115, 230, 191, 0.9)';
                } else if (eff.effect === 'STUN') {
                    bannerText = "Stunned!";
                    bannerColor = 'rgba(255, 189, 0, 0.9)';
                } else if (eff.effect === 'STEAL') {
                    bannerText = "Stolen!";
                    bannerColor = 'rgba(200, 50, 50, 0.9)';
                }
                
                // Render icon in Bottom Left HUD bar
                if (eff.effect !== 'ATTACKED') {
                    const iconImg = AssetManager.images[`EFFECT_${eff.effect}`];
                    if (iconImg) {
                        ctx.save();
                        // Dark border/background
                        ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
                        ctx.fillRect(xOffset - 4, yOffset - 4, 48, 48);
                        
                        // Draw image
                        ctx.drawImage(iconImg, xOffset, yOffset, 40, 40);
                        
                        // Cooldown overlay circle
                        const percent = eff.percentLeft !== undefined ? eff.percentLeft : 100;
                        if (percent > 0 && percent < 100) {
                            ctx.fillStyle = 'rgba(255, 255, 255, 0.55)';
                            ctx.beginPath();
                            ctx.moveTo(xOffset + 20, yOffset + 20);
                            ctx.arc(
                                xOffset + 20, 
                                yOffset + 20, 
                                20, 
                                -Math.PI / 2, 
                                -Math.PI / 2 + ((100 - percent) / 100) * Math.PI * 2, 
                                false
                            );
                            ctx.closePath();
                            ctx.fill();
                        }
                        ctx.restore();
                        xOffset += 56;
                    }
                }
            }
        });
        
        // Draw Banner Text
        if (bannerText) {
            ctx.save();
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.font = 'bold 54px Verdana';
            
            // Text shadow/glow
            ctx.fillStyle = 'black';
            ctx.fillText(bannerText, CONSTANTS.X_RES / 2 + 3, CONSTANTS.Y_RES / 2 - 100 + 3);
            
            ctx.fillStyle = bannerColor;
            ctx.fillText(bannerText, CONSTANTS.X_RES / 2, CONSTANTS.Y_RES / 2 - 100);
            ctx.restore();
        }
    }
}

function getHpColor(percent) {
    if (percent > 66) return 'green';
    if (percent > 33) return 'yellow';
    return 'red';
}
