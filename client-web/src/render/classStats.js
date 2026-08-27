// client-web/src/render/classStats.js
import { getKeysForAction } from '../input/keyboard.js';
import { gameState } from '../state.js';

export let gameConfig = {};

/**
 * Load configuration dynamically from res/game.cfg and res/config.json
 */
export async function loadGameConfig() {
  try {
    const res = await fetch('res/game.cfg');
    const text = await res.text();
    const cfg = {};
    for (const line of text.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('//')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx !== -1) {
        const k = trimmed.substring(0, eqIdx).trim();
        const v = trimmed.substring(eqIdx + 1).trim();
        cfg[k] = isNaN(v) ? v : Number(v);
      }
    }
    gameConfig = cfg;
  } catch (e) {
    try {
      const res = await fetch('res/config.json');
      gameConfig = await res.json();
    } catch (e2) {
      console.warn('Failed to load game config:', e2);
    }
  }
}

// Auto-trigger config loading on module load
loadGameConfig();

export function getCfgNum(key, fallback = 0) {
  if (gameConfig && gameConfig[key] !== undefined) {
    return Number(gameConfig[key]);
  }
  return fallback;
}

/**
 * Calculate ability cooldown applying masteries cooldown reduction:
 * CooldownFactor = 1.0 / Math.pow(masteries.cooldowns.mult, (points - 1))
 */
export function getAbilityCd(ab, customMasteries = null) {
  if (!ab.cdKey) return null;
  const baseVal = getCfgNum(ab.cdKey, ab.fallbackCd);
  const masteries = customMasteries || getActiveMasteries();
  const cdPoints = (masteries && masteries.cooldowns !== undefined) ? Number(masteries.cooldowns) : 1;
  const cdMult = getCfgNum('masteries.cooldowns.mult', 1.1);
  const cdFactor = 1.0 / Math.pow(cdMult, cdPoints - 1);
  
  let baseSec = 0;
  if (ab.cdKey.endsWith('.cdms')) {
    baseSec = baseVal / 1000;
  } else {
    baseSec = baseVal;
  }

  const effectiveSec = baseSec * cdFactor;
  const roundedEff = Math.round(effectiveSec * 10) / 10;
  const roundedBase = Math.round(baseSec * 10) / 10;

  const effStr = roundedEff % 1 === 0 ? `${roundedEff}` : `${roundedEff.toFixed(1)}`;
  const baseStr = roundedBase % 1 === 0 ? `${roundedBase}` : `${roundedBase.toFixed(1)}`;

  if (cdPoints !== 1 && roundedEff !== roundedBase) {
    const diff = roundedEff - roundedBase;
    const diffStr = diff < 0 ? `${diff.toFixed(1)}s` : `+${diff.toFixed(1)}s`;
    return {
      baseSec,
      effectiveSec,
      display: `${effStr}s CD`,
      fullDisplay: `${effStr}s (${diffStr}) CD`,
      bonusStr: diffStr,
      isModified: true
    };
  }

  return {
    baseSec,
    effectiveSec,
    display: `${baseStr}s CD`,
    fullDisplay: `${baseStr}s CD`,
    bonusStr: '',
    isModified: false
  };
}

export const CATEGORY_TAGS = ['Damage', 'Control', 'Support', 'Mobility', 'Scoring'];

export const SKILL_RANKS = {
  Damage: [
    'WARRIOR',
    'RANGER',
    'MAGE',
    'HOUNDMASTER',
    'CAPTAIN',
    'GRENADIER'
  ],
  Control: [
    'GOALIE',
    'SUPPORT',
    'BUILDER',
    'GOLEM',
    'GRENADIER',
    'SPIDER',
    'RANGER',
    'HOUNDMASTER'
  ],
  Support: [
    'GOALIE',
    'SUPPORT',
    'MAGE',
    'ARTISAN',
    'BUILDER'
  ],
  Mobility: [
    'DASHER',
    'SPIDER',
    'STEALTH',
    'WARRIOR',
    'CAPTAIN',
    'SUPPORT'
  ],
  Scoring: [
    'MARKSMAN',
    'DASHER',
    'GOLEM',
    'STEALTH',
    'SPIDER',
    'ARTISAN'
  ]
};

export const TAG_COLORS = {
  Damage: '#ef4444',
  Control: '#f97316',
  Support: '#22c55e',
  Mobility: '#38bdf8',
  Scoring: '#c084fc'
};

export const TAG_ICONS = {
  Damage: '💥',
  Control: '🛑',
  Support: '🛡️',
  Mobility: '⚡',
  Scoring: '🎯'
};

export const CLASS_INFO = {
  WARRIOR: {
    name: 'WARRIOR',
    role: 'DAMAGE / DEFENSE',
    tags: ['Damage', 'Mobility'],
    overview: 'High mobility and close-quarters burst damage.',
    rawHp: 135,
    rawSpeed: 5.40,
    rawThrow: 0.80,
    rawSteal: 11,
    hp: '135 HP',
    speed: '5.40',
    throwPower: '0.80 (253px)',
    stealRad: '11px',
    abilities: [
      { slot: 'E', name: 'Whirlwind Slash', label: 'Ability 1', cdKey: 'titan.slash.cdms', fallbackCd: 5000, desc: 'Performs a 360° spinning blade strike dealing damage to nearby enemies.' },
      { slot: 'R', name: 'Flash Dash', label: 'Ability 2', cdKey: 'titan.flash.warrior.cds', fallbackCd: 23, desc: 'Dashes forward in your movement direction to close distance or dodge attacks.' }
    ],
    passive: 'High durability, fast baseline sprint speed, and powerful close-range threat.'
  },
  RANGER: {
    name: 'RANGER',
    role: 'DAMAGE / DEFENSE',
    tags: ['Damage', 'Control'],
    overview: 'Long-range skillshots and defensive spacing.',
    rawHp: 120,
    rawSpeed: 5.00,
    rawThrow: 0.84,
    rawSteal: 9,
    hp: '120 HP',
    speed: '5.00',
    throwPower: '0.84 (265px)',
    stealRad: '9px',
    abilities: [
      { slot: 'E', name: 'Precision Arrow', label: 'Ability 1', cdKey: 'titan.arrow.cdms', fallbackCd: 4000, desc: 'Fires a piercing arrow dealing damage to targets in its path.' },
      { slot: 'R', name: 'Sweeping Kick', label: 'Ability 2', cdKey: 'titan.kick.cdms', fallbackCd: 12000, desc: 'Kicks and knocks back nearby enemy champions away from you.' }
    ],
    passive: 'Zoning toolkit to control distance and counter aggressive rushers.'
  },
  MAGE: {
    name: 'MAGE',
    role: 'DAMAGE / UTILITY',
    tags: ['Damage', 'Support'],
    overview: 'Portals and targeted burn damage.',
    rawHp: 110,
    rawSpeed: 4.84,
    rawThrow: 0.84,
    rawSteal: 11,
    hp: '110 HP',
    speed: '4.84',
    throwPower: '0.84 (265px)',
    stealRad: '11px',
    abilities: [
      { slot: 'E', name: 'Warp Portal', label: 'Ability 1', cdKey: 'titan.portal.cdms', fallbackCd: 5500, desc: 'Places linked portal gateways on the field. Entering one instantly teleports titans to the other.' },
      { slot: 'R', name: 'Ignite', label: 'Ability 2', cdKey: 'titan.ignite.cds', fallbackCd: 20.0, desc: 'Ignites a targeted enemy with immediate fire damage and a lingering burn over time.' }
    ],
    passive: 'Global mobility manipulation and potent single-target burns.'
  },
  MARKSMAN: {
    name: 'MARKSMAN',
    role: 'SCORER / SNIPER',
    tags: ['Scoring'],
    overview: 'Long-range shooting and passing specialist with slowing skillshots.',
    rawHp: 85,
    rawSpeed: 5.08,
    rawThrow: 1.50,
    rawSteal: 9,
    hp: '85 HP',
    speed: '5.08',
    throwPower: '1.50 (474px)',
    stealRad: '9px',
    abilities: [
      { slot: 'E', name: 'Frost Shot', label: 'Ability 1', cdKey: 'titan.slow.cdms', fallbackCd: 15000, desc: 'Fires a freezing projectile that inflicts a movement slow on the target enemy.' },
      { slot: 'R', name: 'Charge Shot', label: 'Ability 2', cdKey: 'titan.shoot.cdms', fallbackCd: 9000, desc: 'Empowers your next throws with significantly increased throw power and ball speed.' }
    ],
    passive: 'Top-tier throw range across all field classes.'
  },
  DASHER: {
    name: 'DASHER',
    role: 'SCORER / SPEEDSTER',
    tags: ['Scoring', 'Mobility'],
    overview: 'High-octane scorer with enhanced boost efficiency.',
    rawHp: 80,
    rawSpeed: 4.76,
    rawThrow: 1.09,
    rawSteal: 15,
    hp: '80 HP',
    speed: '4.76',
    throwPower: '1.09 (344px)',
    stealRad: '15px',
    abilities: [
      { slot: 'E', name: 'Cover Ball', label: 'Ability 1', cdKey: 'titan.hide.cdms', fallbackCd: 9000, desc: 'Hides the ball inside your body while carrying it, preventing enemies from stealing it.' },
      { slot: 'R', name: 'Flare', label: 'Ability 2', cdKey: 'titan.flare.cds', fallbackCd: 5.0, desc: 'Fires a flare at a targeted enemy, dealing immediate damage and applying a lingering burn.' }
    ],
    passive: 'Enhanced boost speed multiplier and the exclusive capability to boost while carrying the ball.'
  },
  GOLEM: {
    name: 'GOLEM',
    role: 'DEFENSE / SCORER',
    tags: ['Scoring', 'Control'],
    overview: 'High-survivability tank with area disruption and deep shot separation.',
    rawHp: 200,
    rawSpeed: 4.60,
    rawThrow: 1.45,
    rawSteal: 20,
    hp: '200 HP',
    speed: '4.60',
    throwPower: '1.45 (458px)',
    stealRad: '20px',
    abilities: [
      { slot: 'E', name: 'Barrier Shield', label: 'Ability 1', cdKey: 'titan.shield.cdms', fallbackCd: 18000, desc: 'Activates fortified defensive armor that absorbs incoming damage.' },
      { slot: 'R', name: 'Shockwave Slam', label: 'Ability 2', cdKey: 'titan.scatter.cdms', fallbackCd: 12000, desc: 'Slams the ground to violently knock back nearby enemy champions.' }
    ],
    passive: 'Massive health pool, wide steal radius, and heavy throw power.'
  },
  BUILDER: {
    name: 'BUILDER',
    role: 'UTILITY / DEFENSE',
    tags: ['Control', 'Support'],
    overview: 'Build field hazards and defensive structures to control enemy pathing.',
    rawHp: 90,
    rawSpeed: 4.80,
    rawThrow: 1.00,
    rawSteal: 11,
    hp: '90 HP',
    speed: '4.80',
    throwPower: '1.00 (316px)',
    stealRad: '11px',
    abilities: [
      { slot: 'E', name: 'Snare Trap', label: 'Ability 1', cdKey: 'titan.trap.cdms', fallbackCd: 15000, desc: 'Places an invisible ground trap that snares and immobilizes the enemy who steps on it.' },
      { slot: 'R', name: 'Barrier Wall', label: 'Ability 2', cdKey: 'titan.wall.cdms', fallbackCd: 3500, desc: 'Erects a solid wall on the pitch that deflects shots, passes, and blocks enemy pathing.' }
    ],
    passive: 'Pitch control with traps and deployable walls.'
  },
  SUPPORT: {
    name: 'SUPPORT',
    role: 'HEALING / UTILITY',
    tags: ['Support', 'Control', 'Mobility'],
    overview: 'Heal allies and stun enemies to create team advantages.',
    rawHp: 85,
    rawSpeed: 5.40,
    rawThrow: 0.80,
    rawSteal: 13,
    hp: '85 HP',
    speed: '5.40',
    throwPower: '0.80 (253px)',
    stealRad: '13px',
    abilities: [
      { slot: 'E', name: 'Shock Stun', label: 'Ability 1', cdKey: 'titan.stun.cdms', fallbackCd: 7000, desc: 'Emits a shockwave that stuns the nearest enemy champion.' },
      { slot: 'R', name: 'Healing Surge', label: 'Ability 2', cdKey: 'titan.heal.cdms', fallbackCd: 8000, desc: 'Casts a healing surge on a targeted ally (or self) to restore health over time.' }
    ],
    passive: 'Top-tier baseline movement speed allowing swift rotations to assist teammates.'
  },
  ARTISAN: {
    name: 'ARTISAN',
    role: 'UTILITY / TRICKSTER',
    tags: ['Scoring', 'Support'],
    overview: 'Manipulates ball physics with ball-portals, magnetic vacuums, and custom spin shots.',
    rawHp: 90,
    rawSpeed: 5.24,
    rawThrow: 1.15,
    rawSteal: 18,
    hp: '90 HP',
    speed: '5.24',
    throwPower: '1.15 (363px)',
    stealRad: '18px',
    abilities: [
      { slot: 'E', name: 'Ball Vacuum / Spin Mode', label: 'Ability 1', cdKey: 'titan.suck.cdms', fallbackCd: 30000, desc: 'When loose: magnetically pulls the ball toward you. When holding ball: cycles curve shot spin direction.' },
      { slot: 'R', name: 'Ball Portal', label: 'Ability 2', cdKey: 'titan.bportal.cdms', fallbackCd: 7000, desc: 'Places linked portals on the field that teleport only the ball when shot or passed through.' }
    ],
    passive: 'Unique curve shot mechanics and remote magnetic ball retrieval.'
  },
  STEALTH: {
    name: 'STEALTH',
    role: 'SCORER / INFILTRATOR',
    tags: ['Mobility', 'Scoring'],
    overview: 'Invisibility and strategic teleportation.',
    rawHp: 80,
    rawSpeed: 4.88,
    rawThrow: 1.09,
    rawSteal: 11,
    hp: '80 HP',
    speed: '4.88',
    throwPower: '1.09 (344px)',
    stealRad: '11px',
    abilities: [
      { slot: 'E', name: 'Vanish', label: 'Ability 1', cdKey: 'titan.stealth.cdms', fallbackCd: 15000, desc: 'Grants invisibility from enemy vision and radar for a duration.' },
      { slot: 'R', name: 'Shadow Blink', label: 'Ability 2', cdKey: 'titan.flash.stealth.cds', fallbackCd: 21, desc: 'Teleports forward in your movement direction to bypass defenders.' }
    ],
    passive: 'Flanking and stealth goal-scoring potential.'
  },
  GRENADIER: {
    name: 'GRENADIER',
    role: 'UTILITY / CROWD CONTROL',
    tags: ['Control', 'Damage'],
    overview: 'Explosive ordnance, blinds, and persistent fire.',
    rawHp: 110,
    rawSpeed: 4.84,
    rawThrow: 1.02,
    rawSteal: 12,
    hp: '110 HP',
    speed: '4.84',
    throwPower: '1.02 (322px)',
    stealRad: '12px',
    abilities: [
      { slot: 'E', name: 'Flashbang', label: 'Ability 1', cdKey: 'titan.flashbang.cdms', fallbackCd: 11000, desc: 'Hurls a blinding grenade that blinds the nearest enemy champion.' },
      { slot: 'R', name: 'Molotov', label: 'Ability 2', cdKey: 'titan.molotov.cdms', fallbackCd: 15000, desc: 'Throws an incendiary canister creating a persistent zone of fire.' }
    ],
    passive: 'Area suppression and tactical vision denial.'
  },
  HOUNDMASTER: {
    name: 'HOUNDMASTER',
    role: 'DAMAGE / SWARM',
    tags: ['Damage', 'Control'],
    overview: 'Deployable kennel cages and biting attack hounds.',
    rawHp: 120,
    rawSpeed: 5.08,
    rawThrow: 0.90,
    rawSteal: 13,
    hp: '120 HP',
    speed: '5.08',
    throwPower: '0.90 (284px)',
    stealRad: '13px',
    abilities: [
      { slot: 'E', name: 'Deploy Kennel', label: 'Ability 1', cdKey: 'titan.cage.cdms', fallbackCd: 10000, desc: 'Places a hound kennel cage on the pitch ready for release.' },
      { slot: 'R', name: 'Unleash Pack', label: 'Ability 2', cdKey: 'titan.wolf.cdms', fallbackCd: 20000, desc: 'Opens all deployed kennels simultaneously, releasing hounds to chase enemies.' }
    ],
    passive: 'Deploys autonomous minion swarms to pressure opposing lines.'
  },
  CAPTAIN: {
    name: 'CAPTAIN',
    role: 'DAMAGE / MOBILITY',
    tags: ['Damage', 'Mobility'],
    overview: 'High-mobility marksman with 8-round burst rifle and slide timebombs.',
    rawHp: 125,
    rawSpeed: 5.15,
    rawThrow: 1.05,
    rawSteal: 16,
    hp: '125 HP',
    speed: '5.15',
    throwPower: '1.05 (332px)',
    stealRad: '16px',
    abilities: [
      { slot: 'E', name: 'Rifle Shot', label: 'Ability 1', cdKey: 'titan.captain.shot.cdms', fallbackCd: 700, desc: 'Fires one shot from an 8-round clip dealing 5 damage (double to minions). Reloading clip takes 7s.' },
      { slot: 'R', name: 'Slide & Timebomb', label: 'Ability 2', cdKey: 'titan.captain.slide.cdms', fallbackCd: 16000, desc: 'Slides to target location (range 120) and leaves a 3-second delayed timebomb dealing 50 damage in a 100x140 area.' }
    ],
    passive: 'High baseline mobility, burst damage clip with ammo pips, and delayed timebomb zoning.'
  },
  SPIDER: {
    name: 'SPIDER',
    role: 'UTILITY / CONTROLLER',
    tags: ['Control', 'Mobility', 'Scoring'],
    overview: 'Sticky web traps and teleporting ambush cocoon.',
    rawHp: 95,
    rawSpeed: 5.12,
    rawThrow: 0.95,
    rawSteal: 14,
    hp: '95 HP',
    speed: '5.12',
    throwPower: '0.95 (300px)',
    stealRad: '14px',
    abilities: [
      { slot: 'E', name: 'Web Trap', label: 'Ability 1', cdKey: 'titan.spider.web.cdms', fallbackCd: 13000, desc: 'Places a sticky web trap that slows enemies inside by 25% and captures/sticks the ball on entry.' },
      { slot: 'R', name: 'Cocoon Shift', label: 'Ability 2', cdKey: 'titan.spider.cocoon.cdms', fallbackCd: 18000, desc: 'Cocoons self for 1 second, then teleports to the opposite side of a targeted hero\'s position at cast time.' }
    ],
    passive: 'Controls ball movement with webs and bypasses defenses via cocoon teleportation.'
  },
  GOALIE: {
    name: 'GOALIE',
    role: 'DEFENSE / RTS COMMANDER',
    tags: ['Control', 'Support'],
    overview: 'Protect your net with high resilience, direct minion lane targeting, and Guardian upgrades.',
    rawHp: 200,
    rawSpeed: 3.86,
    rawThrow: 1.50,
    rawSteal: 25,
    hp: '200 HP',
    speed: '3.86',
    throwPower: '1.50 (474px)',
    stealRad: '25px',
    abilities: [
      { slot: 'CLICK', name: 'Lane Minion Strike', label: 'Click Lane', desc: 'Click in any lane on the pitch to strike enemy minions and neutral dragons directly from your net.' },
      { slot: 'TREE', name: 'Guardian Tech Tree', label: 'Upgrades', desc: 'Purchase and activate tactical upgrades: Reinforcements, Emergency Barriers, Wall Portals, Forward Medics, and Pull Goalie.' }
    ],
    passive: 'Massive health pool, largest steal radius, and ultimate clearance throw power.'
  }
};

export const ROSTER_STATS = {
  hp: [80, 80, 85, 85, 90, 90, 95, 110, 110, 120, 120, 125, 135, 200, 200],
  speed: [3.86, 4.60, 4.76, 4.80, 4.84, 4.84, 4.88, 5.00, 5.08, 5.08, 5.12, 5.15, 5.24, 5.40, 5.40],
  throwPower: [0.80, 0.80, 0.84, 0.84, 0.90, 0.95, 1.00, 1.02, 1.05, 1.09, 1.09, 1.15, 1.45, 1.50, 1.50],
  stealRad: [9, 9, 11, 11, 11, 11, 12, 13, 13, 14, 15, 16, 18, 20, 25]
};

export function getTierColor(percentile) {
  if (percentile < 33) return '#ef4444';
  if (percentile < 66) return '#eab308';
  return '#22c55e';
}

export const MASTERY_COLOR = '#3b82f6';

export function getActiveMasteries() {
  if (gameState && gameState.controlsHeld && gameState.controlsHeld.masteries) {
    return gameState.controlsHeld.masteries;
  }
  const saved = sessionStorage.getItem('titanMasteries');
  if (saved) {
    try { return JSON.parse(saved); } catch (e) {}
  }
  return { health: 1, speed: 1, shot: 1, stealRadius: 1, cooldowns: 1 };
}

export function getStatPercentile(statType, value) {
  const list = ROSTER_STATS[statType];
  if (!list || list.length === 0) return 50;

  const min = list[0];
  const max = list[list.length - 1];
  if (value <= min) return 6;
  if (value >= max) return 96;

  let idx = 0;
  while (idx < list.length - 1 && list[idx + 1] <= value) {
    idx++;
  }

  if (idx >= list.length - 1) return 96;

  const lowVal = list[idx];
  const highVal = list[idx + 1];
  const segmentFraction = (highVal === lowVal) ? 0 : (value - lowVal) / (highVal - lowVal);
  const rank = (idx + segmentFraction) / (list.length - 1);
  const pct = Math.round(rank * 90 + 6);
  return Math.max(6, Math.min(99, pct));
}

const STAT_MASTERY_CONFIG_MAP = {
  hp: 'masteries.health.mult',
  speed: 'masteries.speed.mult',
  throwPower: 'masteries.throw.mult',
  stealRad: 'masteries.stealRadius.mult',
  damage: 'masteries.damage.mult',
  cooldowns: 'masteries.cooldowns.mult',
  effectDuration: 'masteries.effectDuration.mult',
  abilityRange: 'masteries.range.mult',
  painReduction: 'masteries.painReduction.mult'
};

export function computeStatWithMastery(statType, baseVal, masteryPoints = 0) {
  const points = Number(masteryPoints) || 0;
  let totalVal, bonusVal;

  if (statType === 'stealRad') {
    const flatBonus = getCfgNum('masteries.stealRadius.flat', 1);
    bonusVal = points * flatBonus;
    totalVal = baseVal + bonusVal;
  } else {
    const cfgKey = STAT_MASTERY_CONFIG_MAP[statType];
    const stepMult = cfgKey ? getCfgNum(cfgKey, 1.04) : 1.04;
    // Apply the specific mastery multiplier from game.cfg
    const mult = points > 0 ? (1 + points * (stepMult - 1.0)) : 1.0;
    totalVal = baseVal * mult;
    bonusVal = totalVal - baseVal;
  }

  const basePct = getStatPercentile(statType, baseVal);
  const totalPct = getStatPercentile(statType, totalVal);
  return {
    baseVal,
    totalVal,
    bonusVal,
    basePct,
    totalPct,
    baseColor: getTierColor(basePct),
    masteryColor: MASTERY_COLOR
  };
}

function _getAbilityKeyBadge(ab) {
  if (ab.slot === 'E') {
    const k = getKeysForAction('E');
    return `[${k}]`;
  }
  if (ab.slot === 'R') {
    const k = getKeysForAction('R');
    return `[${k}]`;
  }
  if (ab.slot === 'CLICK') return '[CLICK]';
  if (ab.slot === 'TREE') return '[UPGRADES]';
  return `[${ab.label || ab.slot}]`;
}

/**
 * Draw class details card/overlay.
 */
export function drawClassStatsOverlay(ctx, game, isCompact = false) {
  if (!game || !game.underControl) return;

  const titan = game.underControl;
  const typeKey = (titan.type || 'RANGER').toUpperCase();
  const info = CLASS_INFO[typeKey] || CLASS_INFO.RANGER;
  const masteries = getActiveMasteries();

  ctx.save();

  const hpData = computeStatWithMastery('hp', info.rawHp, masteries.health);
  const spdData = computeStatWithMastery('speed', info.rawSpeed, masteries.speed);
  const thrData = computeStatWithMastery('throwPower', info.rawThrow, masteries.shot);
  const stlData = computeStatWithMastery('stealRad', info.rawSteal, masteries.stealRadius);

  if (isCompact) {
    // ─── Compact Loading Screen Overlay Card (Bottom Center) ───
    const panelW = 1080;
    const panelH = 190;
    const panelX = (1920 - panelW) / 2;
    const panelY = 960 - panelH - 18;

    ctx.fillStyle = 'rgba(10, 24, 20, 0.94)';
    ctx.fillRect(panelX, panelY, panelW, panelH);
    ctx.strokeStyle = '#ffd700';
    ctx.lineWidth = 2;
    ctx.strokeRect(panelX, panelY, panelW, panelH);

    ctx.fillStyle = '#ffd700';
    ctx.font = 'bold 18px Outfit, Arial, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText(`${info.name}  —  ${info.role}`, panelX + 24, panelY + 28);

    const miniStats = [
      { label: 'HP', data: hpData, display: `${Math.round(hpData.totalVal)}` },
      { label: 'SPD', data: spdData, display: `${spdData.totalVal.toFixed(2)}` },
      { label: 'RNG', data: thrData, display: `${thrData.totalVal.toFixed(2)}` },
      { label: 'STL', data: stlData, display: `${Math.round(stlData.totalVal)}px` }
    ];

    const startStatX = panelX + 380;
    miniStats.forEach((st, i) => {
      const sx = startStatX + i * 170;
      const sy = panelY + 16;
      ctx.fillStyle = '#94c2b5';
      ctx.font = 'bold 11px Outfit, Arial, sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(`${st.label}: ${st.display}`, sx, sy + 10);

      const barW = 85;
      const barH = 10;
      const bx = sx + 72;
      const by = sy + 1;

      ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
      ctx.fillRect(bx, by, barW, barH);

      const baseFillW = Math.min(barW, (barW * st.data.basePct) / 100);
      ctx.fillStyle = st.data.baseColor;
      ctx.fillRect(bx, by, baseFillW, barH);

      if (st.data.totalPct > st.data.basePct) {
        const bonusFillW = Math.min(barW - baseFillW, (barW * (st.data.totalPct - st.data.basePct)) / 100);
        ctx.fillStyle = MASTERY_COLOR;
        ctx.fillRect(bx + baseFillW, by, bonusFillW, barH);
      }

      ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
      ctx.strokeRect(bx, by, barW, barH);
    });

    ctx.strokeStyle = 'rgba(255, 215, 0, 0.35)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(panelX + 24, panelY + 42);
    ctx.lineTo(panelX + panelW - 24, panelY + 42);
    ctx.stroke();

    const colW = (panelW - 60) / 2;
    info.abilities.forEach((ab, idx) => {
      const colX = panelX + 24 + idx * (colW + 12);
      const colY = panelY + 66;

      const badgeText = _getAbilityKeyBadge(ab);
      const cdInfo = getAbilityCd(ab, masteries);
      ctx.font = 'bold 13px Outfit, Arial, sans-serif';
      const badgeW = Math.max(50, ctx.measureText(badgeText).width + 14);

      ctx.fillStyle = 'rgba(255, 215, 0, 0.2)';
      ctx.fillRect(colX, colY - 14, badgeW, 20);
      ctx.strokeStyle = '#ffd700';
      ctx.strokeRect(colX, colY - 14, badgeW, 20);
      ctx.fillStyle = '#ffd700';
      ctx.textAlign = 'center';
      ctx.fillText(badgeText, colX + badgeW / 2, colY);

      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 14px Outfit, Arial, sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(`${ab.label}: ${ab.name}`, colX + badgeW + 10, colY);

      if (cdInfo) {
        const titleW = ctx.measureText(`${ab.label}: ${ab.name}`).width;
        ctx.fillStyle = cdInfo.isModified ? '#60a5fa' : '#67e8f9';
        ctx.font = 'bold 12px Outfit, Arial, sans-serif';
        ctx.fillText(`(${cdInfo.display})`, colX + badgeW + 16 + titleW, colY);
      }

      ctx.fillStyle = '#c5d8d0';
      ctx.font = '12px Outfit, Arial, sans-serif';
      _wrapText(ctx, ab.desc, colX, colY + 18, colW, 16, 3);
    });

    ctx.fillStyle = '#8abcb0';
    ctx.font = 'italic 12px Outfit, Arial, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('Hold [TAB] in-match for full guide  |  Stats: 🔴 <33%  🟡 33-66%  🟢 ≥66%  |  🔵 Mastery Add-on', 1920 / 2, panelY + panelH - 8);

  } else {
    // ─── Full Tab-Held Detailed Ingame Overlay ───
    const panelW = 1080;
    const panelH = 590;
    const panelX = (1920 - panelW) / 2;
    const panelY = (960 - panelH) / 2;

    ctx.fillStyle = 'rgba(0, 0, 0, 0.65)';
    ctx.fillRect(0, 0, 1920, 960);

    ctx.fillStyle = 'rgba(12, 28, 24, 0.96)';
    ctx.fillRect(panelX, panelY, panelW, panelH);
    ctx.strokeStyle = '#ffd700';
    ctx.lineWidth = 3;
    ctx.strokeRect(panelX, panelY, panelW, panelH);

    ctx.fillStyle = 'rgba(20, 48, 40, 0.9)';
    ctx.fillRect(panelX, panelY, panelW, 70);
    ctx.fillStyle = '#ffd700';
    ctx.font = 'bold 30px Outfit, Arial, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText(info.name, panelX + 30, panelY + 45);

    ctx.fillStyle = '#4deeea';
    ctx.font = 'bold 18px Outfit, Arial, sans-serif';
    ctx.fillText(`//  ${info.role}`, panelX + 30 + ctx.measureText(info.name).width + 25, panelY + 44);

    ctx.fillStyle = '#ffd700';
    ctx.font = '14px Outfit, Arial, sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText('RELEASE [TAB] TO CLOSE', panelX + panelW - 30, panelY + 44);

    ctx.fillStyle = '#e0f0ec';
    ctx.font = '16px Outfit, Arial, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText(info.overview, panelX + 30, panelY + 102);

    const statsBoxY = panelY + 122;
    const statsBoxH = 82;
    ctx.fillStyle = 'rgba(16, 38, 32, 0.85)';
    ctx.fillRect(panelX + 30, statsBoxY, panelW - 60, statsBoxH);
    ctx.strokeStyle = 'rgba(255, 215, 0, 0.4)';
    ctx.lineWidth = 1;
    ctx.strokeRect(panelX + 30, statsBoxY, panelW - 60, statsBoxH);

    const statMeters = [
      {
        label: 'HEALTH',
        data: hpData,
        valStr: hpData.bonusVal > 0.01 ? `${hpData.baseVal} (+${hpData.bonusVal.toFixed(1)}) HP` : `${hpData.baseVal} HP`
      },
      {
        label: 'SPEED',
        data: spdData,
        valStr: spdData.bonusVal > 0.001 ? `${spdData.baseVal.toFixed(2)} (+${spdData.bonusVal.toFixed(2)})` : `${spdData.baseVal.toFixed(2)}`
      },
      {
        label: 'THROW POWER',
        data: thrData,
        valStr: thrData.bonusVal > 0.001 ? `${thrData.baseVal.toFixed(2)} (+${thrData.bonusVal.toFixed(2)})` : `${thrData.baseVal.toFixed(2)}`
      },
      {
        label: 'STEAL RADIUS',
        data: stlData,
        valStr: stlData.bonusVal > 0.01 ? `${stlData.baseVal} (+${stlData.bonusVal.toFixed(1)})px` : `${stlData.baseVal}px`
      }
    ];

    const meterColW = (panelW - 80) / statMeters.length;
    statMeters.forEach((sm, i) => {
      const mx = panelX + 40 + i * meterColW;
      const my = statsBoxY + 10;

      ctx.fillStyle = '#94c2b5';
      ctx.font = 'bold 11px Outfit, Arial, sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(sm.label, mx, my + 10);

      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 12px Outfit, Arial, sans-serif';
      ctx.textAlign = 'right';
      ctx.fillText(sm.valStr, mx + meterColW - 15, my + 10);

      const barW = meterColW - 15;
      const barH = 14;
      const barY = my + 20;

      ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
      ctx.fillRect(mx, barY, barW, barH);

      const baseFillW = Math.min(barW, Math.max(4, (barW * sm.data.basePct) / 100));
      ctx.fillStyle = sm.data.baseColor;
      ctx.fillRect(mx, barY, baseFillW, barH);

      if (sm.data.totalPct > sm.data.basePct) {
        const bonusFillW = Math.min(barW - baseFillW, (barW * (sm.data.totalPct - sm.data.basePct)) / 100);
        ctx.fillStyle = MASTERY_COLOR;
        ctx.fillRect(mx + baseFillW, barY, bonusFillW, barH);
      }

      ctx.strokeStyle = 'rgba(255, 255, 255, 0.25)';
      ctx.lineWidth = 1;
      ctx.strokeRect(mx, barY, barW, barH);

      ctx.fillStyle = '#e0f0ec';
      ctx.font = '10px Outfit, Arial, sans-serif';
      ctx.textAlign = 'right';
      ctx.fillText(`${sm.data.basePct}th %ile`, mx + barW, barY + barH + 13);
    });

    ctx.fillStyle = '#ffd700';
    ctx.font = 'bold 20px Outfit, Arial, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('CLASS ABILITIES & PASSIVES', panelX + 30, panelY + 235);

    const cardW = (panelW - 80) / 2;
    const cardH = 195;
    const cardY = panelY + 250;

    info.abilities.forEach((ab, idx) => {
      const cx = panelX + 30 + idx * (cardW + 20);
      ctx.fillStyle = 'rgba(16, 36, 30, 0.85)';
      ctx.fillRect(cx, cardY, cardW, cardH);
      ctx.strokeStyle = '#ffd700';
      ctx.lineWidth = 1.5;
      ctx.strokeRect(cx, cardY, cardW, cardH);

      const badgeText = _getAbilityKeyBadge(ab);
      const cdInfo = getAbilityCd(ab, masteries);
      ctx.font = 'bold 15px Outfit, Arial, sans-serif';
      const badgeW = Math.max(54, ctx.measureText(badgeText).width + 16);

      ctx.fillStyle = '#ffd700';
      ctx.fillRect(cx + 16, cardY + 16, badgeW, 26);
      ctx.fillStyle = '#0a1a14';
      ctx.textAlign = 'center';
      ctx.fillText(badgeText, cx + 16 + badgeW / 2, cardY + 35);

      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 17px Outfit, Arial, sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(`${ab.label}: ${ab.name}`, cx + 24 + badgeW, cardY + 35);

      if (cdInfo) {
        const titleW = ctx.measureText(`${ab.label}: ${ab.name}`).width;
        ctx.fillStyle = cdInfo.isModified ? '#60a5fa' : '#67e8f9';
        ctx.font = 'bold 14px Outfit, Arial, sans-serif';
        ctx.fillText(`(${cdInfo.display})`, cx + 32 + badgeW + titleW, cardY + 35);
      }

      ctx.fillStyle = '#d0e5dd';
      ctx.font = '14px Outfit, Arial, sans-serif';
      _wrapText(ctx, ab.desc, cx + 16, cardY + 65, cardW - 32, 22, 5);
    });

    const passY = panelY + 465;
    ctx.fillStyle = 'rgba(20, 50, 40, 0.7)';
    ctx.fillRect(panelX + 30, passY, panelW - 60, 95);
    ctx.strokeStyle = '#4deeea';
    ctx.lineWidth = 1;
    ctx.strokeRect(panelX + 30, passY, panelW - 60, 95);

    ctx.fillStyle = '#4deeea';
    ctx.font = 'bold 15px Outfit, Arial, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('PASSIVE / SPECIAL TRAITS', panelX + 46, passY + 28);

    ctx.fillStyle = '#e8f8f4';
    ctx.font = '14px Outfit, Arial, sans-serif';
    _wrapText(ctx, info.passive, panelX + 46, passY + 52, panelW - 92, 20, 2);
  }

  ctx.restore();
}

function _wrapText(ctx, text, x, y, maxWidth, lineHeight, maxLines = 10) {
  if (!text) return;
  const words = text.split(' ');
  let line = '';
  let lineCount = 0;

  for (let n = 0; n < words.length; n++) {
    const testLine = line + words[n] + ' ';
    const metrics = ctx.measureText(testLine);
    if (metrics.width > maxWidth && n > 0) {
      ctx.fillText(line, x, y);
      line = words[n] + ' ';
      y += lineHeight;
      lineCount++;
      if (lineCount >= maxLines - 1 && n < words.length - 1) {
        ctx.fillText(line.trim() + '...', x, y);
        return;
      }
    } else {
      line = testLine;
    }
  }
  ctx.fillText(line, x, y);
}

export function formatClassTooltip(classKey) {
  if (!classKey) return '';
  const info = CLASS_INFO[classKey.toUpperCase()];
  if (!info) return '';
  const masteries = getActiveMasteries();

  const hpData = computeStatWithMastery('hp', info.rawHp, masteries.health);
  const spdData = computeStatWithMastery('speed', info.rawSpeed, masteries.speed);
  const thrData = computeStatWithMastery('throwPower', info.rawThrow, masteries.shot);
  const stlData = computeStatWithMastery('stealRad', info.rawSteal, masteries.stealRadius);

  const abilitiesText = info.abilities.map(ab => {
    const cdInfo = getAbilityCd(ab, masteries);
    const cdStr = cdInfo ? cdInfo.display : '';
    return `${ab.label} [${ab.name}${cdStr ? ` - ${cdStr}` : ''}]: ${ab.desc}`;
  }).join('\n');

  return `${info.name} [${info.role}]\n${info.overview}\n\nAbilities:\n${abilitiesText}\n\nPassive:\n${info.passive}\n\nPercentile Stats (🔴<33% 🟡33-66% 🟢≥66% | 🔵Mastery):\n• Health: ${hpData.baseVal}${hpData.bonusVal > 0.01 ? ` (+${hpData.bonusVal.toFixed(1)})` : ''} HP [${hpData.basePct}%ile]\n• Speed: ${spdData.baseVal.toFixed(2)}${spdData.bonusVal > 0.001 ? ` (+${spdData.bonusVal.toFixed(2)})` : ''} [${spdData.basePct}%ile]\n• Throw Range: ${thrData.baseVal.toFixed(2)}${thrData.bonusVal > 0.001 ? ` (+${thrData.bonusVal.toFixed(2)})` : ''} [${thrData.basePct}%ile]\n• Steal Radius: ${stlData.baseVal}${stlData.bonusVal > 0.01 ? ` (+${stlData.bonusVal.toFixed(1)})` : ''}px [${stlData.basePct}%ile]`;
}
