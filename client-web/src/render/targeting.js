import { gameState } from '../state.js';
import { getAbilityRange } from '../input/mobile.js';

let selectedTargetEntityId = null;

export function getSelectedTargetEntityId() {
    return selectedTargetEntityId;
}

export function setSelectedTargetEntityId(id) {
    selectedTargetEntityId = id;
}

export function getSelectedTargetEntity(game) {
    if (!game || !selectedTargetEntityId) return null;
    let targetEntity = null;
    if (game.players) {
        targetEntity = game.players.find(p => p.id !== undefined && p.id.toString() === selectedTargetEntityId.toString() && p.health > 0);
    }
    if (!targetEntity && game.minions) {
        targetEntity = game.minions.find(m => m.id !== undefined && m.id.toString() === selectedTargetEntityId.toString() && m.health > 0);
    }
    return targetEntity;
}

/**
 * Registry of single-target titan abilities derived from Java backend AbilityStrategy.java.
 * Only abilities that target a specific entity (via MOUSE_CENTER / mouseNear) are single-target.
 * Non-single-target abilities (AoE ground spawns, self-buffs, directional blinks, skillshots) are excluded.
 */
const SINGLE_TARGET_ABILITIES = {
    SUPPORT: { E: true, R: true },     // E: Stun (130px), R: Heal (250px)
    MARKSMAN: { E: true, R: false },   // E: Slow (250px)
    SPIDER: { E: false, R: true },     // R: Cocoon (150px)
    RANGER: { E: true, R: true },      // E: Arrow (320px), R: Kick (120px)
    CAPTAIN: { E: true, R: false },    // E: Pistol Shot (200px)
    MAGE: { E: false, R: true }        // R: Ignite/Flare (250px)
};

export function isSingleTargetAbility(titan, slot = 'E') {
    if (!titan || !titan.type) return false;
    const type = titan.type.toString().toUpperCase();
    const slotKey = (slot === '1' || slot === 'Q' || slot === 'E') ? 'E' : 'R';
    return !!(SINGLE_TARGET_ABILITIES[type] && SINGLE_TARGET_ABILITIES[type][slotKey]);
}

export function hasAnySingleTargetAbility(titan) {
    return isSingleTargetAbility(titan, 'E') || isSingleTargetAbility(titan, 'R');
}

/**
 * Returns the actual server collision box/bounds of an entity (rather than raw 70x70 sprite box).
 */
export function getEntityCollisionBounds(entity) {
    if (!entity) return { x: 0, y: 0, w: 70, h: 70, cx: 35, cy: 35 };

    const type = entity.type;
    const isGoalie = type === 'GOALIE';
    const isTitan = entity.entityClass === 'Titan' || entity.type !== undefined;

    if (isGoalie) {
        const w = 50;
        const h = 30;
        const x = entity.X + ((entity.width || 70) - w) / 2;
        const y = entity.Y + ((entity.height || 70) - h) / 2;
        return { x, y, w, h, cx: x + w / 2, cy: y + h / 2 };
    } else if (isTitan) {
        const w = Math.max(15, (entity.width || 70) - 50);
        const h = Math.max(15, (entity.height || 70) - 18);
        const x = entity.X + 25;
        const y = entity.Y + 9;
        return { x, y, w, h, cx: x + w / 2, cy: y + h / 2 };
    } else {
        const w = entity.width || 70;
        const h = entity.height || 70;
        const x = entity.X;
        const y = entity.Y;
        return { x, y, w, h, cx: x + w / 2, cy: y + h / 2 };
    }
}

/**
 * Checks if an entity matches targeting rules based on caster titan type and ability slot.
 */
export function isValidTarget(caster, entity, slot = 'E') {
    if (!caster || !entity || !isSingleTargetAbility(caster, slot)) return false;

    // Do not target self
    if (entity.id !== undefined && caster.id !== undefined && entity.id.toString() === caster.id.toString()) {
        return false;
    }

    const type = caster.type.toString().toUpperCase();
    const isSameTeam = entity.team !== undefined && caster.team !== undefined && entity.team === caster.team;

    if (type === 'SUPPORT') {
        if (slot === 'R' || slot === 'W' || slot === '2') {
            return isSameTeam; // Heal: friendly only
        }
        return !isSameTeam; // Stun: enemy only
    } else if (type === 'MARKSMAN') {
        if (slot === 'E' || slot === 'Q' || slot === '1') {
            return !isSameTeam; // Slow: enemy only
        }
    } else if (type === 'SPIDER') {
        if (slot === 'R' || slot === 'W' || slot === '2') {
            return true; // Cocoon: can target ANY player of either team
        }
    }

    return !isSameTeam;
}

export function isValidTargetForAnySlot(caster, entity) {
    return isValidTarget(caster, entity, 'E') || isValidTarget(caster, entity, 'R');
}

export function isEntityInAbilityRangeForSlot(caster, entity, dist, slot = 'E') {
    if (!caster || !entity || !isSingleTargetAbility(caster, slot)) return false;
    const range = getAbilityRange(caster, slot);
    return isValidTarget(caster, entity, slot) && range > 0 && dist <= range;
}

export function isEntityInAbilityRange(caster, entity, dist) {
    return isEntityInAbilityRangeForSlot(caster, entity, dist, 'E') || isEntityInAbilityRangeForSlot(caster, entity, dist, 'R');
}

/**
 * Performs raycast from caster along aim vector to detect hovered targets and update selection memory.
 * Selection memory PERSISTS continuously across all distances until another valid entity is raycast-hovered.
 */
export function updateRaycastTargeting(game, caster, rayOriginX, rayOriginY, aimDirX, aimDirY, maxRaySearch = 1200) {
    if (!game || !caster || !hasAnySingleTargetAbility(caster)) return null;

    const candidates = [];
    if (game.players) {
        for (const p of game.players) {
            if (p.health > 0 && p.id !== caster.id && isValidTargetForAnySlot(caster, p)) {
                candidates.push({ entity: p, bounds: getEntityCollisionBounds(p) });
            }
        }
    }
    if (game.minions) {
        for (const m of game.minions) {
            if (m.health > 0 && isValidTargetForAnySlot(caster, m)) {
                candidates.push({ entity: m, bounds: getEntityCollisionBounds(m) });
            }
        }
    }

    let closestDist = maxRaySearch;
    let hoveredEntity = null;

    for (const cand of candidates) {
        const ex = cand.bounds.cx;
        const ey = cand.bounds.cy;
        const radius = Math.max(cand.bounds.w, cand.bounds.h) / 2 + 15;

        const dx = ex - rayOriginX;
        const dy = ey - rayOriginY;

        const proj = dx * aimDirX + dy * aimDirY;
        if (proj < 0 || proj > maxRaySearch) continue;

        const perpDistSq = (dx * dx + dy * dy) - (proj * proj);
        if (perpDistSq <= radius * radius) {
            if (proj < closestDist) {
                closestDist = proj;
                hoveredEntity = cand.entity;
            }
        }
    }

    // Update selection memory ONLY when a valid entity is raycast-hovered.
    // Selection NEVER clears automatically on empty space or distance; it persists until overridden by another titan.
    if (hoveredEntity && hoveredEntity.id !== undefined) {
        selectedTargetEntityId = hoveredEntity.id.toString();
    }

    return selectedTargetEntityId;
}

/**
 * Renders the outline highlight around the currently selected target entity's actual collision box.
 * Selection memory PERSISTS at all distances:
 * - In range of any single-target ability: BLUE outline (#3b82f6)
 * - Out of range: RED outline (#ef4444)
 */
export function drawTargetingOverlay(ctx, game, camX, camY) {
    if (!game || !selectedTargetEntityId || !game.underControl || !hasAnySingleTargetAbility(game.underControl)) return;

    const caster = game.underControl;
    const targetEntity = getSelectedTargetEntity(game);
    // Clear selection ONLY if the target entity is dead or no longer in game state
    if (!targetEntity || targetEntity.health <= 0) {
        selectedTargetEntityId = null;
        return;
    }

    const casterBounds = getEntityCollisionBounds(caster);
    const targetBounds = getEntityCollisionBounds(targetEntity);

    const dx = targetBounds.cx - casterBounds.cx;
    const dy = targetBounds.cy - casterBounds.cy;
    const dist = Math.sqrt(dx * dx + dy * dy);

    const isInRange = isEntityInAbilityRange(caster, targetEntity, dist);

    const mainColor = isInRange ? '#3b82f6' : '#ef4444';
    const shadowColor = isInRange ? 'rgba(59, 130, 246, 0.85)' : 'rgba(239, 68, 68, 0.85)';
    const accentColor = isInRange ? '#60a5fa' : '#f87171';

    const padding = 5;
    const rx = Math.floor(targetBounds.x - camX) - padding;
    const ry = Math.floor(targetBounds.y - camY) - padding;
    const rw = Math.floor(targetBounds.w) + padding * 2;
    const rh = Math.floor(targetBounds.h) + padding * 2;
    const cornerRadius = 8;

    ctx.save();
    ctx.beginPath();

    if (ctx.roundRect) {
        ctx.roundRect(rx, ry, rw, rh, cornerRadius);
    } else {
        ctx.rect(rx, ry, rw, rh);
    }

    ctx.strokeStyle = mainColor;
    ctx.lineWidth = 3.5;
    ctx.shadowColor = shadowColor;
    ctx.shadowBlur = 10;
    ctx.stroke();

    ctx.lineWidth = 2;
    ctx.strokeStyle = accentColor;
    const reticleLen = 8;
    // Top-Left
    ctx.beginPath();
    ctx.moveTo(rx - 2, ry + reticleLen);
    ctx.lineTo(rx - 2, ry - 2);
    ctx.lineTo(rx + reticleLen, ry - 2);
    ctx.stroke();
    // Top-Right
    ctx.beginPath();
    ctx.moveTo(rx + rw - reticleLen, ry - 2);
    ctx.lineTo(rx + rw + 2, ry - 2);
    ctx.lineTo(rx + rw + 2, ry + reticleLen);
    ctx.stroke();
    // Bottom-Left
    ctx.beginPath();
    ctx.moveTo(rx - 2, ry + rh - reticleLen);
    ctx.lineTo(rx - 2, ry + rh + 2);
    ctx.lineTo(rx + reticleLen, ry + rh + 2);
    ctx.stroke();
    // Bottom-Right
    ctx.beginPath();
    ctx.moveTo(rx + rw - reticleLen, ry + rh + 2);
    ctx.lineTo(rx + rw + 2, ry + rh + 2);
    ctx.lineTo(rx + rw + 2, ry + rh - reticleLen);
    ctx.stroke();

    ctx.restore();
}
