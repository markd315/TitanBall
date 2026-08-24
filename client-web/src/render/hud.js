import { drawImageCam } from './canvas.js';
import { CONSTANTS } from '../constants.js';
import { AssetManager } from '../assets/sprites.js';
import { clientUI, gameState } from '../state.js';

export const ABILITY_TOOLTIPS = {
    // Fortress
    "fortress.t1.homeward": { title: "Home Ward Charter", desc: "Unlock base defense mechanics. Required for all Fortress upgrades." },
    "fortress.t2.reinforce": { title: "Reinforce", desc: "Active (80g). Spawns a solid wall near your base in a random Y location. Despawns and decays health over 10 seconds." },
    "fortress.t2.healingburst": { title: "Healing Burst", desc: "Active (30g). Applies a healing zone to all allied entities in your defensive third." },
    "fortress.t3.snaretrap": { title: "Snare Trap", desc: "Passive. Spawns a permanent trap at a random location in your third that slows enemy units on contact." },
    "fortress.t3.homehealamp": { title: "Amplified Healing", desc: "Passive. Enhances the potency of all defensive third heal effects." },
    "fortress.t3.fastbreakinsurance": { title: "Fast-break Insurance", desc: "Passive. Nullifies uphill speed penalty for 5s after a turnover." },
    "fortress.t3.biggermodels": { title: "Bigger Character Models", desc: "Passive. Increases the hitbox size and collision radius of all allied units." },
    "fortress.t4.bastionprotocol": { title: "Bastion Protocol", desc: "Passive. Spawns two permanent lane walls to block key choke points." },
    "fortress.t4.deadwalls": { title: "Dead Walls", desc: "Passive. Sets the bounce bounce-back factor of back and side walls to zero." },
    "fortress.t4.barrage": { title: "Barrage", desc: "Passive. Spawns a permanent AoE hazard zone in your defensive third." },
    "fortress.t5.emergencybarrier": { title: "Emergency Barrier", desc: "Active (50g). Spawns a short-lived solid wall at your location." },
    "fortress.t5.repairdrone": { title: "Repair Drone", desc: "Active (50g). Heals all friendly minions passively over time." },
    "fortress.t5.icebarrage": { title: "Ice Barrage", desc: "Passive. Upgrades Barrage zone to continuously apply SLOW to enemies." },
    "fortress.t5.firebarrage": { title: "Fire Barrage", desc: "Passive. Upgrades Barrage zone to continuously apply BURN damage to enemies." },
    "fortress.t5.noflyzonetmp": { title: "Temporary No-Fly Zone", desc: "Active (60g). Spawns a temporary endzone aura cutting enemy lob distance by 50% for 10 seconds." },
    "fortress.t5.noflyzoneperm": { title: "Permanent No-Fly Zone", desc: "Passive. Spawns a permanent endzone aura cutting enemy lob distance by 50%." },
    "fortress.t5.dilators": { title: "Dilators", desc: "Passive. Slower game (entire game speed reduced by 10%)." },
    "fortress.t6.impenetrable": { title: "Impenetrable", desc: "Passive. Clamps the maximum speed penalty from enemy pressure at -3." },
    "fortress.t6.hemmedin": { title: "Hemmed In", desc: "Passive. Enlarges lane walls and spawns a new wall behind the center goal." },
    "fortress.t6.deepfreeze": { title: "Deep Freeze", desc: "Passive. Base zone aura continuously applies SLOW to enemies inside." },
    // Siege
    "siege.t1.siegedoctrine": { title: "Siege Doctrine", desc: "Unlock offensive breaching mechanics. Required for all Siege upgrades." },
    "siege.t2.overchargeminion": { title: "Overcharge Minion", desc: "Active (15g). Grants a permanent 1.5x stats multiplier to the next minion wave." },
    "siege.t2.lowgravity": { title: "Low Gravity", desc: "Active (15g). Temporarily reduces ball gravity for 3s, extending lob trajectories." },
    "siege.t2.energyrush": { title: "Energy Rush", desc: "Active (30g). Injects the FAST effect (+35% speed) to a random friendly hero." },
    "siege.t3.rushlane": { title: "Air Support", desc: "Passive. Permanently empowers minions in your goalie's current lane (+40% damage) and grants +40% Guardian click damage in that lane." },
    "siege.t3.forwardmines": { title: "Forward Mines", desc: "Passive. Spawns a compact hazard zone in the opponent's third." },
    "siege.t3.ballportal": { title: "Ball Portal", desc: "Passive. Spawns two portals that teleport the ball between the enemy top and bottom lanes." },
    "siege.t3.vanguards": { title: "Vanguards", desc: "Passive. Spawns 1 extra minion per wave when your team crosses the midfield." },
    "siege.t3.pullgoalie": { title: "Pull Goalie", desc: "Passive (10g). Sets all titan stats to 0.8 with 60 health, and permits the goalie to exit its bounding box." },
    "siege.t4.accumulators": { title: "Accumulators", desc: "Passive. Expands opponent goal size when friendly minions overlap them." },
    "siege.t4.parapet": { title: "Parapet", desc: "Passive. Spawns an elevated battlement in the enemy top lane near the blueline (solid for enemies). Friendly heroes enter with a 1s root, teleporting to center for +20% Defense, +20% Shot/Lob Power, and Steal Protection. Moving roots 1s and teleports you back outside." },
    "siege.t5.saveprogress": { title: "Save Progress", desc: "Passive. Disables scoring decay, making banked sidegoal progress persistent." },
    "siege.t5.incendiarymines": { title: "Incendiary Mines", desc: "Passive. Expands Forward Mines hazard zone and continuously applies BURN damage to enemies." },
    "siege.t5.forwardoutpost": { title: "Incendiary Mines", desc: "Passive. Expands Forward Mines hazard zone and continuously applies BURN damage to enemies." },
    "siege.t5.phalanx": { title: "Phalanx", desc: "Passive. Damage from other minions is reduced by 10% for each adjacent friendly minion." },
    "siege.t5.callsiegeminion": { title: "Call Siege Minion", desc: "Active (55g). Spawns a heavy minion wave at spawn locations." },
    "siege.t5.anchor": { title: "Anchor", desc: "Active (70g). Tethers two enemy heroes together, constraining their distance." },
    "siege.t5.shockgrenade": { title: "Shock Grenade", desc: "Active (40g). Instantly injects the STUN effect to a random enemy hero." },
    "siege.t5.wallsdown": { title: "Walls Down", desc: "Active (120g). Disables collision on all enemy walls for 1000ms (rendered at 20% opacity)." },
    "siege.t6.forwardmedics": { title: "Forward Medics", desc: "Passive. Spawns a permanent zone in the enemy third that heals allied units." },
    "siege.t6.maximumpressure": { title: "Maximum Pressure", desc: "Passive. Doubles lane pressure speed boost when base pressure exceeds +5." },
    "siege.t6.multiball": { title: "Multiball", desc: "Passive. Spawns a second ball at the midline that can only be kicked, not picked up or thrown." },
    // Empowerment
    "empowerment.t1.combinecontract": { title: "Combine Contract", desc: "Unlock roster stat upgrades. Required for all Empowerment upgrades." },
    "empowerment.t2.sharpshooter": { title: "Sharpshooter", desc: "Active (30g). Temporarily grants +20% Throw Power and +20% Range to a random hero." },
    "empowerment.t3.grit": { title: "Grit", desc: "Passive. Grants a permanent +1 Health and +1 Pain Reduction to all friendly heroes." },
    "empowerment.t3.marksmanship": { title: "Marksmanship", desc: "Passive. Grants a permanent +1 Shooting and +1 Range to all friendly heroes." },
    "empowerment.t3.footwork": { title: "Footwork", desc: "Passive. Grants a permanent +1 Speed to all friendly heroes." },
    "empowerment.t3.discipline": { title: "Discipline", desc: "Passive. Grants a permanent +1 Cooldown and +1 Effect Duration to friendly heroes." },
    "empowerment.t4.forecheck": { title: "Forecheck", desc: "Passive. Grants +3px steal radius to friendly units in the middle third." },
    "empowerment.t4.fuelreserves": { title: "Fuel Reserves", desc: "Passive. Multiplies maximum speed boost (fuel) capacity by 1.5x." },
    "empowerment.t4.heroportals": { title: "Hero Portals", desc: "Passive. Spawns repositioning node portals restricted to hero interactions." },
    "empowerment.t5.focusedtraining": { title: "Focused Training", desc: "Passive. Adds +2 points to your highest existing goalie mastery category." },
    "empowerment.t5.focusedtraining2": { title: "Focused Training II", desc: "Passive. Adds +2 points to your highest existing goalie mastery category." },
    "empowerment.t5.energysurge": { title: "Energy Surge", desc: "Active (50g). Instantly restores maximum fuel capacity for all friendly heroes." },
    "empowerment.t5.secondwind": { title: "Second Wind", desc: "Active (50g). Reduces active Q and W ability cooldown timers by 50%." },
    "empowerment.t5.heistcamp": { title: "Heist Camp", desc: "Passive. Grants a permanent +2px increase to steal radius for all friendly heroes." },
    "empowerment.t5.clutchgene": { title: "Clutch Gene", desc: "Passive. Grants +30% defense (armor ratio) to friendly heroes when in possession of the ball." },
    "empowerment.t6.dragonsbreath": { title: "Dragon's Breath", desc: "Passive. Spawns a miniboss in bot lane; slaying it grants team permanent FAST." },
    "empowerment.t6.apexform": { title: "Apex Form", desc: "Passive. Adds a permanent +1 allocation to all 9 core masteries for friendly heroes." },
    "empowerment.t6.bannerofcommand": { title: "Banner of Command", desc: "Passive. Allied minions gain +15% damage for each hero in their lane at spawn." },
    // Cultivation
    "cultivation.t1.manawell": { title: "Mana Well", desc: "Passive. Unlocks cultivation mana resource and generates passive mana tick." },
    "cultivation.t2.manainfusion": { title: "Mana Infusion", desc: "Active (50g). Instantly grants +100 mana and permanently increases tick rate by 10%." },
    "cultivation.t3.manacompounding": { title: "Mana Compounding", desc: "Passive. Multiplies passive mana tick rate based on the current mana bank." },
    "cultivation.t3.highermanacap": { title: "Higher Mana Cap", desc: "Passive. Increases the maximum goalie mana pool threshold to 1000." },
    "cultivation.t3.tollcollector": { title: "Toll Collector", desc: "Passive. Gain +5 goalie mana whenever a friendly minion crosses the midfield line." },
    "cultivation.t4.manavines": { title: "Mana Vines", desc: "Passive (250m). Spawns a base hazard zone that slows and burns enemies, granting +30 mana when an enemy passes through." },
    "cultivation.t4.manafrenzy": { title: "Mana Frenzy", desc: "Passive (275m). Boosts friendly hero cooldown recovery speed by 1% per 30 current mana." },
    "cultivation.t5.manasurge": { title: "Mana Surge", desc: "Active (40m). Injects a team burst, reducing Q/W cds by 50% and restoring 10% health and 25% boost." },
    "cultivation.t5.manasummon": { title: "Mana Summon", desc: "Active (50m). Spawns 2 heavy minions at your end of the field." },
    "cultivation.t5.manapollinate": { title: "Mana Pollinate", desc: "Passive (250m). Allows mana to purchase a single T5 upgrade from another tree (one-time benefit)." },
    "cultivation.t5.riskadjustedreturn": { title: "Risk-Adjusted Return", desc: "Passive (300m). Grants enemy 1 point; awards 1.5 points to your team after 150s." },
    "cultivation.t5.tripledown": { title: "Triple Down", desc: "Passive (350m). Gives opponent +3 sidegoals (0.75 pts); awards you +1 main goal." },
    "cultivation.t6.wallportals": { title: "Wall Portals", desc: "Passive (300m). Spawns 20 permanent ball portals on the borders and midline paired horizontally." },
    "cultivation.t6.uninhibitedportal": { title: "Uninhibited Portal", desc: "Passive (275m). Spawns 2 extra allied minions in the middle lane every wave." },
    "cultivation.t6.iceportal": { title: "Ice Portal", desc: "Passive (250m). Continuously applies SLOW effect to all enemy minion units." }
};

export const HARDCODED_COSTS = {
    "fortress.t1.homeward": { cost: 50 },
    "fortress.t2.reinforce": { use: 80 },
    "fortress.t2.healingburst": { use: 30 },
    "fortress.t3.snaretrap": { cost: 100 },
    "fortress.t3.homehealamp": { cost: 125 },
    "fortress.t3.fastbreakinsurance": { cost: 150 },
    "fortress.t3.biggermodels": { cost: 125 },
    "fortress.t4.deadwalls": { cost: 275 },
    "fortress.t4.bastionprotocol": { cost: 300 },
    "fortress.t4.barrage": { cost: 325 },
    "fortress.t5.emergencybarrier": { use: 50 },
    "fortress.t5.repairdrone": { use: 50 },
    "fortress.t5.noflyzonetmp": { use: 60 },
    "fortress.t5.noflyzoneperm": { cost: 60 },
    "fortress.t5.dilators": { cost: 250 },
    "fortress.t5.icebarrage": { cost: 250 },
    "fortress.t5.firebarrage": { cost: 250 },
    "fortress.t6.impenetrable": { cost: 450 },
    "fortress.t6.hemmedin": { cost: 500 },
    "fortress.t6.deepfreeze": { cost: 400 },
    "siege.t1.siegedoctrine": { cost: 50 },
    "siege.t2.overchargeminion": { use: 15 },
    "siege.t2.lowgravity": { use: 15 },
    "siege.t2.energyrush": { use: 30 },
    "siege.t3.rushlane": { cost: 100 },
    "siege.t3.forwardmines": { cost: 125 },
    "siege.t3.ballportal": { cost: 150 },
    "siege.t3.vanguards": { cost: 125 },
    "siege.t3.pullgoalie": { cost: 10 },
    "siege.t4.accumulators": { cost: 275 },
    "siege.t4.parapet": { cost: 325 },
    "siege.t5.saveprogress": { cost: 400 },
    "siege.t5.incendiarymines": { cost: 325 },
    "siege.t5.forwardoutpost": { cost: 325 },
    "siege.t5.phalanx": { cost: 300 },
    "siege.t5.callsiegeminion": { use: 55 },
    "siege.t5.anchor": { use: 70 },
    "siege.t5.shockgrenade": { use: 40 },
    "siege.t5.wallsdown": { use: 120 },
    "siege.t6.forwardmedics": { cost: 400 },
    "siege.t6.maximumpressure": { cost: 450 },
    "siege.t6.multiball": { cost: 600 },
    "empowerment.t1.combinecontract": { cost: 50 },
    "empowerment.t2.sharpshooter": { use: 30 },
    "empowerment.t3.grit": { cost: 125 },
    "empowerment.t3.marksmanship": { cost: 125 },
    "empowerment.t3.footwork": { cost: 125 },
    "empowerment.t3.discipline": { cost: 125 },
    "empowerment.t4.forecheck": { cost: 250 },
    "empowerment.t4.fuelreserves": { cost: 300 },
    "empowerment.t4.heroportals": { cost: 325 },
    "empowerment.t5.focusedtraining": { cost: 300 },
    "empowerment.t5.focusedtraining2": { cost: 300 },
    "empowerment.t5.energysurge": { use: 50 },
    "empowerment.t5.secondwind": { use: 50 },
    "empowerment.t5.heistcamp": { cost: 300 },
    "empowerment.t5.clutchgene": { cost: 300 },
    "empowerment.t6.dragonsbreath": { cost: 400 },
    "empowerment.t6.apexform": { cost: 600 },
    "empowerment.t6.bannerofcommand": { cost: 450 },
    "cultivation.t1.manawell": { cost: 50 },
    "cultivation.t2.manainfusion": { use: 50 },
    "cultivation.t3.manacompounding": { cost: 100 },
    "cultivation.t3.highermanacap": { cost: 75 },
    "cultivation.t3.tollcollector": { cost: 125 },
    "cultivation.t4.manavines": { cost: 250, isMana: true },
    "cultivation.t4.manafrenzy": { cost: 275, isMana: true },
    "cultivation.t5.manasurge": { use: 40, isMana: true },
    "cultivation.t5.manasummon": { use: 50, isMana: true },
    "cultivation.t5.manapollinate": { cost: 250, isMana: true },
    "cultivation.t5.riskadjustedreturn": { cost: 300, isMana: true },
    "cultivation.t5.tripledown": { cost: 350, isMana: true },
    "cultivation.t6.wallportals": { cost: 300, isMana: true },
    "cultivation.t6.uninhibitedportal": { cost: 275, isMana: true },
    "cultivation.t6.iceportal": { cost: 250, isMana: true }
};

const DEBUG_MODE = false; // true = flat grey boxes over every node, no lock/star logic.
export const ANALYSIS_IMG_WIDTH = 1024;
export const ANALYSIS_IMG_HEIGHT = 559;

export const TREE_NODES = {
    GOALIE_TREE_FORTRESS: [
        [234,14,292,84], [377,14,435,84], [590,21,645,83], [735,18,790,82], [880,21,936,82],
        [485,118,538,170],
        [278,204,341,254], [405,204,481,254], [546,204,618,254], [681,204,741,254],
        [325,309,415,372], [463,309,549,372], [611,309,702,372],
        [263,389,322,452], [411,389,481,452], [591,389,653,452], [751,389,816,452],
        [147,482,389,545], [389,482,630,545], [653,482,872,545]
    ],
    GOALIE_TREE_EMPOWERMENT: [
        [376,12,439,85], [593,14,652,85], [740,12,798,85],
        [485,120,536,167],
        [285,204,340,253], [418,205,474,253], [555,204,609,253], [688,205,742,253],
        [310,320,406,390], [458,320,556,355], [600,316,725,355],
        [186,406,232,458], [429,406,473,458], [663,406,707,458],
        [146,463,386,533], [386,463,627,533], [658,462,863,531]
    ],
    GOALIE_TREE_SIEGE: [
        [234,16,290,85], [362,16,419,85], [486,16,543,85], [606,15,663,82], [710,15,767,82], [814,15,871,82], [918,15,974,82],
        [485,120,537,170],
        [272,210,354,259], [397,210,492,259], [537,210,616,259], [670,212,746,259], [785,175,945,290],
        [339,317,489,372], [537,317,685,372],
        [187,404,377,447], [420,389,610,451], [650,380,880,447],
        [147,478,399,548], [405,478,629,548], [653,478,870,548]
    ],
    GOALIE_TREE_CULTIVATION: [
        [331,13,391,83], [741,13,796,84], [896,14,952,83],
        [485,119,538,170],
        [270,205,400,280], [440,205,570,280], [620,205,750,280],
        [330,309,500,372], [535,309,700,372],
        [184,406,392,448], [429,406,605,448], [663,406,830,448],
        [147,480,375,547], [388,480,628,547], [654,479,802,547]
    ]
};

// Every node has a real tier (t1-t6) and a purchase kind. "header" was never
// a real tier - visually it's just row 1, which happens to contain the t2
// node(s) and the repeatable t5 node(s) for that tree, mixed with the
// occasional one-time t5 node (fortress.noflyzone) that's just drawn there.
// kind: 'cost' = one-time unlock, stays purchased forever, gets the star.
//       'use'  = repeatable single-use charge, no star, re-clickable forever
//                as long as its tier prereq still holds.
// currency: 'gold' (default, omitted) or 'mana' - drives which balance field
// gets checked/deducted. Only cultivation uses mana.
export const NODE_DEFS = {
    GOALIE_TREE_FORTRESS: [
        // row 1 (visual header row)
        { tier: 't2', name: 'reinforce',         kind: 'use'  },
        { tier: 't2', name: 'healingburst',       kind: 'use'  },
        { tier: 't5', name: 'emergencybarrier',   kind: 'use'  },
        { tier: 't5', name: 'noflyzonetmp',       kind: 'use'  },
        { tier: 't5', name: 'repairdrone',        kind: 'use'  },
        // row 2
        { tier: 't1', name: 'homeward',           kind: 'cost' },
        // row 3
        { tier: 't3', name: 'snaretrap',          kind: 'cost' },
        { tier: 't3', name: 'homehealamp',        kind: 'cost' },
        { tier: 't3', name: 'fastbreakinsurance', kind: 'cost' },
        { tier: 't3', name: 'biggermodels',       kind: 'cost' },
        // row 4
        { tier: 't4', name: 'bastionprotocol',          kind: 'cost' },
        { tier: 't4', name: 'deadwalls',    kind: 'cost' },
        { tier: 't4', name: 'barrage',            kind: 'cost' },
        // row 5 - TODO: TREE_NODES has 4 boxes here, only 3 named t5-cost entries exist
        { tier: 't5', name: 'noflyzoneperm',           kind: 'cost' },
        { tier: 't5', name: 'dilators',           kind: 'cost' },
        { tier: 't5', name: 'icebarrage',         kind: 'cost' },
        { tier: 't5', name: 'firebarrage',        kind: 'cost' },
        // row 6
        { tier: 't6', name: 'impenetrable',       kind: 'cost' },
        { tier: 't6', name: 'hemmedin',           kind: 'cost' },
        { tier: 't6', name: 'deepfreeze',         kind: 'cost' },
    ],
    GOALIE_TREE_SIEGE: [
        // row 1
        { tier: 't2', name: 'overchargeminion',   kind: 'use'  },
        { tier: 't2', name: 'lowgravity',          kind: 'use'  },
        { tier: 't2', name: 'energyrush',          kind: 'use'  },
        { tier: 't5', name: 'callsiegeminion',     kind: 'use'  },
        { tier: 't5', name: 'anchor',              kind: 'use'  },
        { tier: 't5', name: 'shockgrenade',        kind: 'use'  },
        { tier: 't5', name: 'wallsdown',           kind: 'use'  },
        // row 2
        { tier: 't1', name: 'siegedoctrine',       kind: 'cost' },
        // row 3
        { tier: 't3', name: 'forwardmines',        kind: 'cost' },
        { tier: 't3', name: 'ballportal',          kind: 'cost' },
        { tier: 't3', name: 'rushlane',            kind: 'cost' },
        { tier: 't3', name: 'vanguards',           kind: 'cost' },
        { tier: 't3', name: 'pullgoalie',          kind: 'cost' },
        // row 4
        { tier: 't4', name: 'accumulators',        kind: 'cost' },
        { tier: 't4', name: 'parapet',             kind: 'cost' },
        // row 5
        { tier: 't5', name: 'incendiarymines',     kind: 'cost' },
        { tier: 't5', name: 'phalanx',             kind: 'cost' },
        { tier: 't5', name: 'saveprogress',        kind: 'cost' },
        // row 6
        { tier: 't6', name: 'maximumpressure',     kind: 'cost' },
        { tier: 't6', name: 'forwardmedics',       kind: 'cost' },
        { tier: 't6', name: 'multiball',           kind: 'cost' },
    ],
    // TODO: row 5 has 3 boxes, only 1 named cfg entry (focusedtraining) -
    // need the other 2 names or confirmation 2 boxes are stray in TREE_NODES.
    GOALIE_TREE_EMPOWERMENT: [
        // row 1
        { tier: 't2', name: 'sharpshooter',        kind: 'use'  },
        { tier: 't5', name: 'energysurge',         kind: 'use'  },
        { tier: 't5', name: 'secondwind',          kind: 'use'  },
        // row 2
        { tier: 't1', name: 'combinecontract',     kind: 'cost' },
        // row 3
        { tier: 't3', name: 'grit',                kind: 'cost' },
        { tier: 't3', name: 'marksmanship',        kind: 'cost' },
        { tier: 't3', name: 'footwork',            kind: 'cost' },
        { tier: 't3', name: 'discipline',          kind: 'cost' },
        // row 4
        { tier: 't4', name: 'fuelreserves',        kind: 'cost' },
        { tier: 't4', name: 'heroportals',         kind: 'cost' },
        { tier: 't4', name: 'forecheck',           kind: 'cost' },
        // row 5
        { tier: 't5', name: 'heistcamp',           kind: 'cost' },
        { tier: 't5', name: 'clutchgene',          kind: 'cost' },
        { tier: 't5', name: 'focusedtraining',     kind: 'cost' },
        // row 6
        { tier: 't6', name: 'apexform',            kind: 'cost' },
        { tier: 't6', name: 'dragonsbreath',       kind: 'cost' },
        { tier: 't6', name: 'bannerofcommand',     kind: 'cost' },
    ],
    // TODO: "manainfusion" key/cost unconfirmed - using .mana by assumption.
    GOALIE_TREE_CULTIVATION: [
        // row 1
        { tier: 't2', name: 'manainfusion',        kind: 'use',  currency: 'mana' }, // UNCONFIRMED
        { tier: 't5', name: 'manasurge',           kind: 'use',  currency: 'mana' },
        { tier: 't5', name: 'manasummon',          kind: 'use',  currency: 'mana' },
        // row 2
        { tier: 't1', name: 'manawell',            kind: 'cost' }, // gold
        // row 3
        { tier: 't3', name: 'manacompounding',     kind: 'cost' }, // gold
        { tier: 't3', name: 'highermanacap',       kind: 'cost' }, // gold
        { tier: 't3', name: 'tollcollector',       kind: 'cost' }, // gold
        // row 4
        { tier: 't4', name: 'manavines',           kind: 'cost', currency: 'mana' },
        { tier: 't4', name: 'manafrenzy',          kind: 'cost', currency: 'mana' },
        // row 5
        { tier: 't5', name: 'manapollinate',       kind: 'cost', currency: 'mana' },
        { tier: 't5', name: 'riskadjustedreturn',  kind: 'cost', currency: 'mana' },
        { tier: 't5', name: 'tripledown',          kind: 'cost', currency: 'mana' },
        // row 6
        { tier: 't6', name: 'wallportals',         kind: 'cost', currency: 'mana' },
        { tier: 't6', name: 'uninhibitedportal',   kind: 'cost', currency: 'mana' },
        { tier: 't6', name: 'iceportal',           kind: 'cost', currency: 'mana' },
    ]
};

// Single source of truth for tab geometry - hud.js (drawing) and mouse.js
// (hit-testing) BOTH import this, never redeclare it.
export const tabNames  = ["Siege", "Fortress", "Empowerment", "Cultivation"];
export const tabColors = ["#E05A07", "#075AE0", "#E0B107", "#7207E0"];
export const tabKeys   = ["GOALIE_TREE_SIEGE", "GOALIE_TREE_FORTRESS", "GOALIE_TREE_EMPOWERMENT", "GOALIE_TREE_CULTIVATION"];
export const tabCount  = tabKeys.length;
export const tabWidth  = 180;
export const tabHeight = 48;
export const spacing   = 14;

export const TREE_SHORT_NAME = {
    GOALIE_TREE_SIEGE: "siege",
    GOALIE_TREE_FORTRESS: "fortress",
    GOALIE_TREE_EMPOWERMENT: "empowerment",
    GOALIE_TREE_CULTIVATION: "cultivation"
};


const REQUIRED_TECHS = {
    siege:       [1, 0, 2, 1, 1],
    fortress:    [1, 0, 2, 1, 1],
    empowerment: [1, 0, 2, 1, 1],
    cultivation: [1, 0, 2, 1, 1],
};  

function tierUnlocked(shortName, tier, treeState) {
    const n = parseInt(tier.slice(1), 10); // "t3" -> 3
    if (n <= 1) return true;

    const prevTier = 't' + (n - 1);
    if (!tierUnlocked(shortName, prevTier, treeState)) return false;

    const required = REQUIRED_TECHS[shortName][n - 2];
    return (treeState.tierProgress[prevTier] || 0) >= required;
}

export function canPollinateT5(nodeKey, purchasedSet) {
    if (!nodeKey || !purchasedSet) return false;
    const parts = nodeKey.split('.');
    if (parts.length < 3) return false;
    const tree = parts[0];
    const tier = parts[1];
    if (tier === 't5' && tree !== 'cultivation' && purchasedSet.has('cultivation.t5.manapollinate')) {
        for (const key of purchasedSet) {
            if (key.includes('.t5.') && !key.startsWith('cultivation.t5.')) {
                return false;
            }
        }
        return true;
    }
    return false;
}

export function isNodeUnlocked(activeKey, idx, treeState) {
    const def = getNodeDef(activeKey, idx);
    if (!def) return false;
    const shortName = TREE_SHORT_NAME[activeKey];
    const nodeKey = `${shortName}.${def.tier}.${def.name}`;
    if (def.kind === 'cost' && canPollinateT5(nodeKey, treeState.purchased)) {
        return true;
    }
    return tierUnlocked(shortName, def.tier, treeState);
}

export function getNodeDef(activeKey, idx) {
    return NODE_DEFS[activeKey] && NODE_DEFS[activeKey][idx];
}

export function getNodeConfigKey(activeKey, idx, purchasedSet) {
    const def = getNodeDef(activeKey, idx);
    if (!def) return null;
    const shortName = TREE_SHORT_NAME[activeKey];
    let name = def.name;
    if (name === 'focusedtraining' && purchasedSet && purchasedSet.has('empowerment.t5.focusedtraining') && !purchasedSet.has('empowerment.t5.focusedtraining2')) {
        name = 'focusedtraining2';
    }
    return `${shortName}.${def.tier}.${name}`;
}


export function getTreeState(game, team, activeKey) {
    // 1. Resolve the correct server array based on the player's team representation
    const isHome = team === 0 || team === 'HOME';
    const rawUpgrades = isHome ? game.homeGoaliePurchasedUpgrades : game.awayGoaliePurchasedUpgrades;
    
    // 2. Convert the array to a Set for the UI's .has() lookups
    const purchased = new Set(rawUpgrades || []);

    // 3. Calculate how many nodes have been bought in each tier for THIS specific tree
    const tierProgress = {};
    const shortName = TREE_SHORT_NAME[activeKey];
    const defs = NODE_DEFS[activeKey];

    if (defs) {
        defs.forEach(def => {
            const nodeKey = `${shortName}.${def.tier}.${def.name}`;
            // Only 'cost' upgrades count toward unlocking the next tier.
            // 'use' nodes are repeatable abilities, not structural unlocks.
            if (def.kind === 'cost' && purchased.has(nodeKey)) {
                tierProgress[def.tier] = (tierProgress[def.tier] || 0) + 1;
            }
        });
    }

    return { purchased, tierProgress };
}

function drawOverlayIcon(ctx, src, x, y, w, h, alpha) {
    const img = AssetManager.images[src];
    if (!img) return;
    const size = Math.min(w, h) * 0.6;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.drawImage(img, x + (w - size) / 2, y + (h - size) / 2, size, size);
    ctx.restore();
}

export function drawHud(ctx, game, state) {
    if (!game) return;
    let hoveredNode = null;
    ctx.globalCompositeOperation = "source-over";

    // Draw Health bars above entities
    const entities = [...(game.players || []), ...(game.entityPool || [])];

    for (const e of entities) {
        if (e.health > 0 && e.entityClass !== 'LaneMinion') {
            if (e.entityClass === 'Fire' || e.entityClass === 'Portal' || e.entityClass === 'BallPortal' || e.entityClass === 'Parapet' || (e.entityClass === 'Trap' && (e.width === 80 || e.maxHealth >= 99999))) continue;

            const invisible = game.underControl && game.underControl.team !== e.team &&
                              game.effectPool && game.effectPool.effects.some(ef => ef.effect === 'STEALTHED' && ef.on && ef.on.id === e.id) &&
                              !game.effectPool.effects.some(ef => ef.effect === 'FLARE' && ef.on && ef.on.id === e.id);
            if (invisible) continue;

            if (e.entityClass === 'Dragon') {
                const cx = Math.floor(e.X + (e.width || 120) / 2 - state.camX);
                const cy = Math.floor(e.Y - 25 - state.camY);
                const radius = 22;

                ctx.save();
                
                ctx.beginPath();
                ctx.arc(cx, cy, radius, 0, 2 * Math.PI);
                ctx.fillStyle = 'rgba(10, 26, 20, 0.8)';
                ctx.fill();

                ctx.beginPath();
                ctx.arc(cx, cy, radius, 0, 2 * Math.PI);
                ctx.strokeStyle = '#475569';
                ctx.lineWidth = 4;
                ctx.stroke();

                const homeRatio = Math.max(0, Math.min(1.0, (e.homeDamage || 0) / 250.0));
                ctx.beginPath();
                ctx.arc(cx, cy, radius, Math.PI / 2, Math.PI / 2 + homeRatio * Math.PI, false);
                ctx.strokeStyle = '#3b82f6';
                ctx.lineWidth = 4;
                ctx.stroke();

                const awayRatio = Math.max(0, Math.min(1.0, (e.awayDamage || 0) / 250.0));
                ctx.beginPath();
                ctx.arc(cx, cy, radius, Math.PI / 2, Math.PI / 2 - awayRatio * Math.PI, true);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 4;
                ctx.stroke();

                ctx.restore();
                continue;
            }

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
        ctx.fillText(`HOME: ${game.home.score}`, 50, 35);
    }
    if (game.away) {
        ctx.fillStyle = '#ffffff'; // Away team white
        ctx.fillText(`AWAY: ${game.away.score}`, CONSTANTS.X_RES - 200, 35);
    }

    // Draw Game Timer
    const fps = 1000 / (game.GAMETICK_MS || 25);
    const timeSec = game.framesSinceStart / fps;
    const sdTime = (game.options && game.options.suddenDeathIndex ? game.options.suddenDeathIndex * 60 : 240);
    const hsdTime = (game.options && game.options.tieIndex ? game.options.hardcoreSuddenDeathIndex * 60 : 240); //TODO recover value from server
    const tieTime = (game.options && game.options.tieIndex ? game.options.tieIndex * 60 : 360);

    let displayTime = 0;
    let timerColor = '#00ff00';
    let isOvertime = false;

    if (timeSec < sdTime) {
        displayTime = sdTime - timeSec;
    } else {
        displayTime = timeSec - sdTime;
        timerColor = '#ff3b30';
        isOvertime = true;
    }

    const timeRounded = Math.floor(displayTime * 10) / 10;
    const minutes = Math.floor(timeRounded / 60);
    const seconds = Math.floor(timeRounded % 60);
    const tenths = Math.floor((timeRounded * 10) % 10);
    let timeStr = `${minutes}:${seconds.toString().padStart(2, '0')}.${tenths}`;
    if (isOvertime) timeStr = "SD " + timeStr;

    ctx.save();
    ctx.textAlign = 'center';
    ctx.font = 'bold 36px Courier New';
    ctx.fillStyle = timerColor;
    ctx.fillText(timeStr, CONSTANTS.X_RES / 2, 35);
    ctx.restore();

    // Timer Warnings
    let bottomText = "";
    let warningColor = "rgba(0, 255, 0, 0.4)";
    const WARN = 30, FWARN = 10;
    const gTime = game.GOALIE_DISABLE_TIME || 120;

    const timer = Math.floor(timeSec);
    const CHWARN = 10;

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
    } else if (timer >= hsdTime - WARN && timer < hsdTime - FWARN) {
        warningColor = "rgba(230, 230, 0, 0.8)";
        bottomText = "HARDCORE SUDDEN DEATH WARNING";
    } else if (timer >= hsdTime - FWARN && timer < hsdTime) {
        warningColor = "rgba(255, 0, 0, 0.9)";
        bottomText = "HARDCORE SUDDEN DEATH WARNING";
    } else if (timer >= hsdTime && timer < hsdTime + CHWARN) {
        warningColor = "rgba(255, 0, 0, 1.0)";
        bottomText = "HARDCORE SUDDEN DEATH ENABLED";
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
            ctx.fillStyle = 'rgba(0, 0, 0, 0.65)';
            ctx.fillRect(0, 160, CONSTANTS.X_RES, 80);

            ctx.strokeStyle = 'black';
            ctx.lineWidth = 6;
            ctx.strokeText(bottomText, CONSTANTS.X_RES / 2, 200);

            ctx.fillStyle = warningColor;
            ctx.fillText(bottomText, CONSTANTS.X_RES / 2, 200);
        } else {
            ctx.font = 'bold 24px Verdana';
            ctx.fillStyle = warningColor;
            ctx.fillText(bottomText, CONSTANTS.X_RES / 2, 90);
        }
        ctx.restore();
    }

    // Draw Personal Cooldown / Status Effects
    if (game.underControl && game.effectPool && game.effectPool.effects) {
        let xOffset = 50;
        const yOffset = CONSTANTS.Y_RES - 80;

        let bannerText = "";
        let bannerColor = "";

        game.effectPool.effects.forEach((eff) => {
            if (eff.on && eff.on.id === game.underControl.id) {
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

                if (eff.effect !== 'ATTACKED') {
                    const iconImg = AssetManager.images[`EFFECT_${eff.effect}`];
                    if (iconImg) {
                        ctx.save();
                        ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
                        ctx.fillRect(xOffset - 4, yOffset - 4, 48, 48);

                        ctx.drawImage(iconImg, xOffset, yOffset, 40, 40);

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

        if (bannerText) {
            ctx.save();
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.font = 'bold 54px Verdana';

            ctx.fillStyle = 'black';
            ctx.fillText(bannerText, CONSTANTS.X_RES / 2 + 3, CONSTANTS.Y_RES / 2 - 100 + 3);

            ctx.fillStyle = bannerColor;
            ctx.fillText(bannerText, CONSTANTS.X_RES / 2, CONSTANTS.Y_RES / 2 - 100);
            ctx.restore();
        }

        // Draw Goalie Currency (Gold) & Mana
        if (game.underControl && game.underControl.type === 'GOALIE') {
            const isHome       = game.underControl.team === 'HOME';
            const purchased    = isHome ? (game.homeGoaliePurchasedUpgrades || []) : (game.awayGoaliePurchasedUpgrades || []);
            const purchasedSet = new Set(purchased);
            const purchasedArray = Array.from(purchasedSet);
            const hasMana = purchasedArray.some(key => key.startsWith('cultivation.'));

            // ── Auto-advance buildOrderIndex past already-purchased cost nodes ──
            // Handles the case where the player buys an upgrade manually (without X).
            const order = gameState.buildOrder;
            while (
                gameState.buildOrderIndex < order.length
            ) {
                const item = order[gameState.buildOrderIndex];
                const itemDefs     = NODE_DEFS[item.tree];
                const itemShort    = TREE_SHORT_NAME[item.tree];
                if (!itemDefs || !itemShort) { gameState.buildOrderIndex++; continue; }
                const itemDef = itemDefs.find(d => `${itemShort}.${d.tier}.${d.name}` === item.nodeKey);
                if (!itemDef || itemDef.kind !== 'cost') break; // 'use' nodes never auto-skip
                // Resolve actual key (handles focusedtraining→focusedtraining2)
                let resolvedNodeKey = item.nodeKey;
                if (itemDef.name === 'focusedtraining' && purchasedSet.has('empowerment.t5.focusedtraining') && !purchasedSet.has('empowerment.t5.focusedtraining2')) {
                    resolvedNodeKey = 'empowerment.t5.focusedtraining2';
                }
                if (purchasedSet.has(resolvedNodeKey)) {
                    gameState.buildOrderIndex++; // permanently skip
                } else {
                    break; // next unpurchased cost node found
                }
            }

            // ── Determine if next BO upgrade is affordable (for flash) ──
            let flashGold = false;
            let flashMana = false;
            if (gameState.buildOrderIndex < order.length) {
                const nextItem = order[gameState.buildOrderIndex];
                const costData = HARDCODED_COSTS[nextItem.nodeKey] || {};
                const amount   = costData.use !== undefined ? costData.use : (costData.cost !== undefined ? costData.cost : 0);
                const isManaNext = !!(costData.isMana);
                if (amount > 0) {
                    const goldAmt = Math.floor(isHome ? (game.homeGoalieCurrency || 0) : (game.awayGoalieCurrency || 0));
                    const manaAmt = Math.floor(isHome ? (game.homeGoalieMana    || 0) : (game.awayGoalieMana    || 0));
                    if (isManaNext && manaAmt >= amount) flashMana = true;
                    if (!isManaNext && goldAmt >= amount) flashGold = true;
                }
            }

            // Pulse: 0→1→0 on ~1.2 s cycle
            const pulse = 0.55 + 0.45 * Math.sin(Date.now() / 190);

            if (hasMana) {
                ctx.save();
                ctx.fillStyle   = 'rgba(10, 26, 20, 0.8)';
                ctx.strokeStyle = flashMana ? `rgba(220,160,255,${pulse})` : 'violet';
                ctx.lineWidth   = flashMana ? 3 + pulse * 2 : 2;
                if (flashMana) ctx.shadowColor = `rgba(220,160,255,${pulse})`;
                if (flashMana) ctx.shadowBlur  = 14 * pulse;
                ctx.beginPath();
                if (ctx.roundRect) ctx.roundRect(50, CONSTANTS.Y_RES - 220, 220, 50, 8);
                else ctx.rect(50, CONSTANTS.Y_RES - 220, 220, 50);
                ctx.fill();
                ctx.stroke();
                ctx.shadowBlur = 0;

                ctx.fillStyle = 'violet';
                ctx.beginPath();
                ctx.arc(75, CONSTANTS.Y_RES - 195, 12, 0, 2 * Math.PI);
                ctx.fill();

                ctx.fillStyle = '#000000';
                ctx.font = 'bold 14px Arial';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillText('M', 75, CONSTANTS.Y_RES - 195);

                ctx.fillStyle = flashMana ? `rgba(255,220,255,${0.7 + 0.3 * pulse})` : '#ffffff';
                ctx.font = 'bold 20px Arial';
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';
                const manaVal    = Math.floor(isHome ? (game.homeGoalieMana || 0) : (game.awayGoalieMana || 0));
                const maxManaVal = purchasedArray.includes('cultivation.t3.highermanacap') ? 1000 : 250;
                ctx.fillText(`Mana: ${manaVal}/${maxManaVal}`, 100, CONSTANTS.Y_RES - 195);
                ctx.restore();
            }

            ctx.save();
            ctx.fillStyle   = 'rgba(10, 26, 20, 0.8)';
            ctx.strokeStyle = flashGold ? `rgba(255,200,60,${pulse})` : '#ff9f1c';
            ctx.lineWidth   = flashGold ? 3 + pulse * 2 : 2;
            if (flashGold) ctx.shadowColor = `rgba(255,180,0,${pulse})`;
            if (flashGold) ctx.shadowBlur  = 14 * pulse;
            ctx.beginPath();
            if (ctx.roundRect) ctx.roundRect(50, CONSTANTS.Y_RES - 160, 220, 50, 8);
            else ctx.rect(50, CONSTANTS.Y_RES - 160, 220, 50);
            ctx.fill();
            ctx.stroke();
            ctx.shadowBlur = 0;

            ctx.fillStyle = '#ff9f1c';
            ctx.beginPath();
            ctx.arc(75, CONSTANTS.Y_RES - 135, 12, 0, 2 * Math.PI);
            ctx.fill();

            ctx.fillStyle = '#000000';
            ctx.font = 'bold 14px Arial';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('$', 75, CONSTANTS.Y_RES - 135);

            ctx.fillStyle = flashGold ? `rgba(255,230,100,${0.7 + 0.3 * pulse})` : '#ffffff';
            ctx.font = 'bold 20px Arial';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';
            const amt = Math.floor(isHome ? (game.homeGoalieCurrency || 0) : (game.awayGoalieCurrency || 0));
            ctx.fillText(`Gold: ${amt}`, 100, CONSTANTS.Y_RES - 135);
            ctx.restore();
        }
    }

    if (game.underControl && game.underControl.type === 'GOALIE') {
        ctx.save();
        ctx.globalAlpha = 1.0;
        ctx.globalCompositeOperation = "source-over";
        ctx.filter = "none";
        
        hoveredNode = null;

        const totalWidth = tabCount * tabWidth + (tabCount - 1) * spacing;
        const startX = (CONSTANTS.X_RES - totalWidth) / 2;
        const y = CONSTANTS.Y_RES - 60;

        // 1. Draw the Menu Image & Node Overlays
        if (clientUI.goalieTabIndex >= 0 && clientUI.goalieTabIndex < tabCount) {
            const activeKey = tabKeys[clientUI.goalieTabIndex];
            const activeImg = AssetManager.images[activeKey];

            if (activeImg) {
                const ox = (CONSTANTS.X_RES - activeImg.width) / 2;
                const oy = (CONSTANTS.Y_RES - activeImg.height) / 2;

                ctx.drawImage(activeImg, ox, oy);

                const nodes = TREE_NODES[activeKey];
                if (nodes) {
                    const scaleX = activeImg.width / ANALYSIS_IMG_WIDTH;
                    const scaleY = activeImg.height / ANALYSIS_IMG_HEIGHT;

                    if (DEBUG_MODE) {
                        ctx.fillStyle = 'rgba(128, 128, 128, 0.7)';
                        for (const [x1, y1, x2, y2] of nodes) {
                            const boxX = ox + (x1 * scaleX);
                            const boxY = oy + (y1 * scaleY);
                            const boxW = (x2 - x1) * scaleX;
                            const boxH = (y2 - y1) * scaleY;
                            ctx.fillRect(boxX, boxY, boxW, boxH);
                        }
                    } else {
                        const treeState = getTreeState(game, game.underControl.team, activeKey);

                        nodes.forEach((coords, idx) => {
                            const [x1, y1, x2, y2] = coords;
                            const boxX = ox + (x1 * scaleX);
                            const boxY = oy + (y1 * scaleY);
                            const boxW = (x2 - x1) * scaleX;
                            const boxH = (y2 - y1) * scaleY;

                            const def = getNodeDef(activeKey, idx);
                            if (!def) return; // no definition for this box - draw nothing rather than guess

                            const nodeKey = getNodeConfigKey(activeKey, idx, treeState.purchased);

                            if (state && state.mouseX >= boxX && state.mouseX <= boxX + boxW &&
                                state.mouseY >= boxY && state.mouseY <= boxY + boxH) {
                                hoveredNode = { nodeKey, def, boxX, boxY, boxW, boxH };
                            }

                            // Star: purchased, one-time ('cost' kind) upgrade. 'use'
                            // nodes are repeatable and never starred.
                            if (def.kind === 'cost' && treeState.purchased.has(nodeKey)) {
                                drawOverlayIcon(ctx, 'star', boxX, boxY, boxW, boxH, 1.0);
                                return;
                            }

                            if (!isNodeUnlocked(activeKey, idx, treeState)) {
                                drawOverlayIcon(ctx, 'lock', boxX, boxY, boxW, boxH, 1.0);
                            } else {
                                // Draw tabColors border around the buyable upgrade
                                const tabIdx = tabKeys.indexOf(activeKey);
                                const borderColor = tabColors[tabIdx] || '#ffffff';
                                ctx.save();
                                ctx.strokeStyle = borderColor;
                                ctx.lineWidth = 5;
                                ctx.strokeRect(boxX, boxY, boxW, boxH);
                                ctx.restore();
                            }
                            // else: prereqs met, unpurchased (or repeatable) -> no overlay, still clickable.
                        });
                    }
                }
            }
        }

        // 2. Draw the Tabs
        for (let i = 0; i < tabCount; i++) {
            const x = startX + i * (tabWidth + spacing);
            ctx.save();
            ctx.fillStyle = tabColors[i];
            ctx.globalAlpha = (clientUI.goalieTabIndex === i ? 1.0 : 0.75);
            ctx.beginPath();
            if (ctx.roundRect) {
                ctx.roundRect(x, y, tabWidth, tabHeight, 12);
            } else {
                ctx.rect(x, y, tabWidth, tabHeight);
            }
            ctx.fill();
            ctx.font = "bold 22px Arial";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillStyle = "white";
            ctx.fillText(tabNames[i], x + tabWidth / 2, y + tabHeight / 2);
            ctx.restore();
        }

        ctx.restore();
    }

    // 3. Draw premium tooltip if hover occurs (drawn outside scaling/restores to stay on top of everything!)
    if (hoveredNode) {
        const info = ABILITY_TOOLTIPS[hoveredNode.nodeKey] || { title: hoveredNode.nodeKey, desc: "Upgrade." };
        
        // Look up cost in the hardcoded map
        const costData = HARDCODED_COSTS[hoveredNode.nodeKey] || {};
        let costText = "";
        const isHome = game.underControl && (game.underControl.team === 0 || game.underControl.team === 'HOME');
        const rawUpgrades = isHome ? game.homeGoaliePurchasedUpgrades : game.awayGoaliePurchasedUpgrades;
        const purchasedSet = new Set(rawUpgrades || []);

        if (hoveredNode.def.kind === 'cost') {
            const amt = costData.cost !== undefined ? costData.cost : 0;
            const useMana = costData.isMana || canPollinateT5(hoveredNode.nodeKey, purchasedSet);
            costText = useMana ? `Cost: ${amt} Mana` : `Cost: ${amt} Gold`;
        } else {
            const amt = costData.use !== undefined ? costData.use : 0;
            costText = costData.isMana ? `Use: ${amt} Mana` : `Use: ${amt} Gold`;
        }
        
        const typeText = hoveredNode.def.kind === 'cost' ? 'Passive' : 'Active';

        const tooltipWidth = 320;
        const descWords = info.desc.split(' ');
        const lines = [];
        let currentLine = "";
        ctx.font = '14px Outfit, sans-serif';
        for (let word of descWords) {
            let testLine = currentLine + word + " ";
            let metrics = ctx.measureText(testLine);
            if (metrics.width > tooltipWidth - 30 && currentLine !== "") {
                lines.push(currentLine.trim());
                currentLine = word + " ";
            } else {
                currentLine = testLine;
            }
        }
        if (currentLine !== "") {
            lines.push(currentLine.trim());
        }

        const tooltipHeight = 75 + lines.length * 20;

        let tx = state.mouseX - tooltipWidth / 2;
        let ty = state.mouseY - tooltipHeight - 20;

        // Clamp to screen edges
        if (tx < 10) tx = 10;
        if (tx + tooltipWidth > CONSTANTS.X_RES - 10) {
            tx = CONSTANTS.X_RES - tooltipWidth - 10;
        }
        if (ty < 10) {
            ty = state.mouseY + 20;
        }

        ctx.save();
        ctx.shadowColor = 'rgba(0, 0, 0, 0.5)';
        ctx.shadowBlur = 10;
        
        ctx.fillStyle = 'rgba(15, 23, 42, 0.96)';
        ctx.strokeStyle = 'rgba(139, 92, 246, 0.6)';
        ctx.lineWidth = 2.5;

        ctx.beginPath();
        if (ctx.roundRect) {
            ctx.roundRect(tx, ty, tooltipWidth, tooltipHeight, 10);
        } else {
            ctx.rect(tx, ty, tooltipWidth, tooltipHeight);
        }
        ctx.fill();
        ctx.stroke();

        ctx.shadowColor = 'transparent';
        ctx.textAlign = 'center';

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 18px Outfit, sans-serif';
        ctx.fillText(info.title, tx + tooltipWidth / 2, ty + 25);

        ctx.fillStyle = hoveredNode.def.kind === 'cost' ? '#38bdf8' : '#fb7185';
        ctx.font = 'bold 13px Outfit, sans-serif';
        ctx.fillText(`${typeText} • ${costText}`, tx + tooltipWidth / 2, ty + 46);

        ctx.fillStyle = '#e2e8f0';
        ctx.font = '14px Outfit, sans-serif';
        let ly = ty + 68;
        for (let line of lines) {
            ctx.fillText(line, tx + tooltipWidth / 2, ly);
            ly += 20;
        }

        ctx.restore();
    }
}

function getHpColor(percent) {
    if (percent > 66) return 'green';
    if (percent > 33) return 'yellow';
    return 'red';
}