import { AssetManager } from '../assets/sprites.js';

export function drawEffectIcons(ctx, game, camX, camY) {
  if (!game || !game.effectPool || !game.effectPool.effects) return;
  const effects = game.effectPool.effects;
  const onEntities = game.effectPool.on; // array of entities corresponding to effects
  
  // Group effects by target entity ID to space them out
  const offsetMap = {};
  
  for (let i = 0; i < effects.length; i++) {
    const e = effects[i];
    const en = onEntities[i];
    if (!en || !e) continue;
    
    // Skip dead, attacked, or internal castlag effects to keep visual clean
    if (e.effect === 'DEAD' || e.effect === 'ATTACKED' || e.effect === 'CAST_LAG') {
      continue;
    }
    
    // Skip rendering if stealthed (and not on fire) and on enemy team
    const invisible = game.underControl && game.underControl.team !== en.team &&
                      game.effectPool.effects.some(ef => ef.effect === 'STEALTHED' && ef.on && ef.on.id === en.id) &&
                      !game.effectPool.effects.some(ef => ef.effect === 'FLARE' && ef.on && ef.on.id === en.id);
    if (invisible) continue;
    
    const isCooldown = e.effect.startsWith('COOLDOWN');
    
    if (!offsetMap[en.id]) {
      offsetMap[en.id] = -22; // starting x offset relative to entity center
    }
    
    const spriteKey = `EFFECT_${e.effect}`;
    const img = AssetManager.images[spriteKey];
    
    const x = Math.floor(en.X + offsetMap[en.id] - camX);
    const y = Math.floor(en.Y - 35 - camY); // above the health bar
    
    if (img) {
      // Draw the icon
      ctx.drawImage(img, x, y, 16, 16);
      
      // Draw the "expiration circle" (radial progress overlay/circle around the icon)
      const percentLeft = e.percentLeft !== undefined ? e.percentLeft : 100;
      const angle = (percentLeft / 100) * 2 * Math.PI;
      
      ctx.beginPath();
      ctx.arc(x + 8, y + 8, 10, -Math.PI / 2, -Math.PI / 2 + angle);
      ctx.strokeStyle = isCooldown ? 'rgba(255, 127, 17, 0.85)' : 'rgba(45, 200, 120, 0.85)';
      ctx.lineWidth = 2;
      ctx.stroke();
      
      offsetMap[en.id] += 20; // advance offset for next effect icon
    }
  }

  // Handle boost effect icons
  if (game.players) {
    for (const t of game.players) {
      if (t.isBoosting && t.health > 0) {
        const invisible = game.underControl && game.underControl.team !== t.team &&
                          game.effectPool.effects.some(ef => ef.effect === 'STEALTHED' && ef.on && ef.on.id === t.id) &&
                          !game.effectPool.effects.some(ef => ef.effect === 'FLARE' && ef.on && ef.on.id === t.id);
        if (invisible) continue;

        if (!offsetMap[t.id]) {
          offsetMap[t.id] = -22;
        }
        const img = AssetManager.images['EFFECT_FAST'];
        const x = Math.floor(t.X + offsetMap[t.id] - camX);
        const y = Math.floor(t.Y - 35 - camY);
        if (img) {
          ctx.drawImage(img, x, y, 16, 16);
          offsetMap[t.id] += 20;
        }
      }
    }
  }
}
