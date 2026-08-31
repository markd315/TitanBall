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
    if (!targetEntity && game.entityPool) {
        targetEntity = game.entityPool.find(e => e.id !== undefined && e.id.toString() === selectedTargetEntityId.toString() && e.health > 0);
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

export function getMaxSingleTargetAbilityRange(caster) {
    if (!caster) return 0;
    const rangeE = isSingleTargetAbility(caster, 'E') ? getAbilityRange(caster, 'E') : 0;
    const rangeR = isSingleTargetAbility(caster, 'R') ? getAbilityRange(caster, 'R') : 0;
    return Math.max(rangeE, rangeR);
}

const UNKILLABLE_CLASSES = new Set([
    'Fire', 'Portal', 'BallPortal', 'Trap', 'Parapet',
    'SecondBall', 'Web', 'Bomb'
]);

/**
 * Returns true if an entity can be killed (damaged/slain), and false for unkillable entities
 * (barrages, permanent ballportals, hero portals, hemmed in, snare traps, bastion protocol, etc.).
 */
export function isKillableEntity(entity) {
    if (!entity) return false;
    if (entity.health <= 0) return false;

    // Entities with unkillable high-HP thresholds (e.g. Bastion Protocol walls, Hemmed In walls, Barrages)
    if ((entity.maxHealth !== undefined && entity.maxHealth >= 99999) || (entity.health !== undefined && entity.health >= 99999)) {
        return false;
    }

    // Utility/structure entity classes that cannot be attacked or killed by abilities
    if (entity.entityClass && UNKILLABLE_CLASSES.has(entity.entityClass)) {
        return false;
    }

    return true;
}

/**
 * Checks if a Titan has any single-target ability capable of targeting enemy minions.
 */
export function canSelectMinions(titan) {
    if (!titan || !titan.type) return false;
    const dummyMinion = {
        entityClass: 'LaneMinion',
        health: 10,
        maxHealth: 22.5,
        team: (titan.team === 'HOME' || titan.team === 0) ? 'AWAY' : 'HOME'
    };
    return isValidTargetForAnySlot(titan, dummyMinion);
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
    } else if (entity.entityClass === 'LaneMinion') {
        const myTitan = (gameState.game && gameState.game.underControl) ? gameState.game.underControl : null;
        const tType = (myTitan && myTitan.type) ? String(myTitan.type).toUpperCase() : null;
        const isPlayerGoalie = tType === 'GOALIE';
        const r = isPlayerGoalie ? 20 : 10;
        const cx = entity.X;
        const cy = entity.Y;
        const w = r * 2;
        const h = r * 2;
        return { x: cx - r, y: cy - r, w, h, cx, cy };
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
 * Based on AbilityStrategy.java backend contracts:
 * - RANGER E (Arrow), CAPTAIN E (Pistol), MAGE R (Ignite) target Enemy Titans AND Minions in entityPool.
 * - SUPPORT E (Stun), SUPPORT R (Heal), MARKSMAN E (Slow), SPIDER R (Cocoon), RANGER R (Kick) target TITANS ONLY.
 */
export function isValidTarget(caster, entity, slot = 'E') {
    if (!caster || !entity || !isSingleTargetAbility(caster, slot)) return false;

    // Do not target self
    if (entity.id !== undefined && caster.id !== undefined && entity.id.toString() === caster.id.toString()) {
        return false;
    }

    const type = caster.type.toString().toUpperCase();
    const slotKey = (slot === '1' || slot === 'Q' || slot === 'E') ? 'E' : 'R';
    const isTitan = entity.entityClass === 'Titan' || entity.type !== undefined;
    const isSameTeam = entity.team !== undefined && caster.team !== undefined && entity.team === caster.team;

    // Do not target unkillable entities (barrages, permanent portals, hemmed in, snare traps, etc.)
    if (!isTitan && !isKillableEntity(entity)) {
        return false;
    }

    // Abilities that can target minions as well as Titans: Ranger Arrow, Captain Pistol, Mage Ignite
    const allowsMinions = (type === 'RANGER' && slotKey === 'E') ||
                          (type === 'CAPTAIN' && slotKey === 'E') ||
                          (type === 'MAGE' && slotKey === 'R');

    // If ability does not allow minions, target MUST be a Titan player!
    if (!allowsMinions && !isTitan) {
        return false;
    }

    if (type === 'SUPPORT') {
        if (slotKey === 'R') {
            return isSameTeam && isTitan; // Heal: friendly titan only
        }
        return !isSameTeam && isTitan; // Stun: enemy titan only
    } else if (type === 'MARKSMAN') {
        return !isSameTeam && isTitan; // Slow: enemy titan only
    } else if (type === 'SPIDER') {
        return isTitan; // Cocoon: any titan of either team except self
    } else if (type === 'RANGER') {
        if (slotKey === 'R') {
            return !isSameTeam && isTitan; // Kick: enemy titan only
        }
        return !isSameTeam; // Arrow: enemy titan or minion
    } else if (type === 'CAPTAIN') {
        return !isSameTeam; // Pistol: enemy titan or minion
    } else if (type === 'MAGE') {
        return !isSameTeam; // Ignite: enemy titan or minion
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
 * Performs target detection from caster to update selection memory.
 * - New selections can ONLY occur on valid targets WITHIN the caster's maximum single-target ability range.
 * - Out-of-range entities (> maxAbilityRange) cannot be selected as new targets.
 * - Once an entity is selected, selection memory PERSISTS continuously when out of range (rendered RED)
 *   until another valid target WITHIN range is raycast-hovered.
 */
export function updateRaycastTargeting(game, caster, rayOriginX, rayOriginY, aimDirX, aimDirY) {
    if (!game || !caster || !hasAnySingleTargetAbility(caster)) return null;

    const maxAbilityRange = getMaxSingleTargetAbilityRange(caster);
    if (maxAbilityRange <= 0) return selectedTargetEntityId;

    const casterBounds = getEntityCollisionBounds(caster);
    const mouseFieldX = (gameState.mouseX || 0) + (gameState.camX || 0);
    const mouseFieldY = (gameState.mouseY || 0) + (gameState.camY || 0);

    const candidates = [];
    const checkCandidate = (candEntity) => {
        if (!candEntity || candEntity.health <= 0 || candEntity.id === caster.id) return;
        if (!isValidTargetForAnySlot(caster, candEntity)) return;

        const bounds = getEntityCollisionBounds(candEntity);
        const dx = bounds.cx - casterBounds.cx;
        const dy = bounds.cy - casterBounds.cy;
        const dist = Math.hypot(dx, dy);

        // ONLY entities WITHIN maximum single-target ability range are valid new selection candidates
        if (dist <= maxAbilityRange) {
            candidates.push({ entity: candEntity, bounds, dist });
        }
    };

    if (game.players) {
        for (const p of game.players) checkCandidate(p);
    }
    if (game.entityPool) {
        for (const e of game.entityPool) checkCandidate(e);
    }

    // 1. Direct Touch/Mouse Hover Check (within maxAbilityRange)
    for (const cand of candidates) {
        const b = cand.bounds;
        if (mouseFieldX >= b.x && mouseFieldX <= b.x + b.w && mouseFieldY >= b.y && mouseFieldY <= b.y + b.h) {
            if (cand.entity && cand.entity.id !== undefined) {
                selectedTargetEntityId = cand.entity.id.toString();
                return selectedTargetEntityId;
            }
        }
    }

    // 2. Aim Ray Search (closest in-range candidate along ray)
    let closestProj = maxAbilityRange;
    let hoveredEntity = null;

    for (const cand of candidates) {
        const ex = cand.bounds.cx;
        const ey = cand.bounds.cy;
        const radius = Math.max(cand.bounds.w, cand.bounds.h) / 2 + 15;

        const dx = ex - rayOriginX;
        const dy = ey - rayOriginY;

        const proj = dx * aimDirX + dy * aimDirY;
        if (proj < 0 || proj > maxAbilityRange) continue;

        const perpDistSq = (dx * dx + dy * dy) - (proj * proj);
        if (perpDistSq <= radius * radius) {
            if (proj < closestProj) {
                closestProj = proj;
                hoveredEntity = cand.entity;
            }
        }
    }

    if (hoveredEntity && hoveredEntity.id !== undefined) {
        selectedTargetEntityId = hoveredEntity.id.toString();
    }

    return selectedTargetEntityId;
}

/**
 * Renders the outline highlight around the currently selected target entity's (Titan or Minion) actual collision box.
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

    const isMinion = targetEntity.entityClass === 'LaneMinion';
    const padding = isMinion ? 2 : 5;
    const rx = Math.floor(targetBounds.x - camX) - padding;
    const ry = Math.floor(targetBounds.y - camY) - padding;
    const rw = Math.floor(targetBounds.w) + padding * 2;
    const rh = Math.floor(targetBounds.h) + padding * 2;
    const cornerRadius = isMinion ? 4 : 8;

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
