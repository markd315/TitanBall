// ── Goalie Tech Tree Data, Geometry, and Unlock Logic ─────────────────────────

export const ABILITY_TOOLTIPS = {
    // Fortress
    "fortress.t1.homeward": { title: "Home Ward Charter", desc: "Unlock base defense mechanics. Required for all Fortress upgrades." },
    "fortress.t2.reinforce": { title: "Reinforce", desc: "Active (80g). Spawns a solid wall near your base in a random Y location. Despawns and decays health over 10 seconds." },
    "fortress.t2.healingburst": { title: "Healing Burst", desc: "Active (30g). Applies a healing zone to all allied entities in your defensive third." },
    "fortress.t3.snaretrap": { title: "Snare Trap", desc: "Passive. Spawns a permanent trap at a random location in your third that slows enemy units on contact." },
    "fortress.t3.homehealamp": { title: "Amplified Healing", desc: "Passive. Enhances the potency of all defensive third heal effects." },
    "fortress.t3.fastbreakinsurance": { title: "Fast-break Insurance", desc: "Passive. Nullifies uphill speed penalty for 5s after a turnover." },
    "fortress.t3.biggermodels": { title: "Bigger Character Models", desc: "Passive. Increases hitbox size and collision radius of all allied outfield titans (+25%), but grants opponents +3px steal radius." },
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
    "siege.t4.parapet": { title: "Parapet", desc: "Passive. Spawns an elevated battlement in the enemy top lane near the blueline (solid for enemies). Friendly heroes enter with a 0.5s root, teleporting to center for 60% Damage Reduction, +20% Shot/Lob Power, and Steal Protection. Moving roots 0.25s and teleports you back outside." },
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
    "empowerment.t3.grit": { title: "Grit", desc: "Passive. Grants a permanent +3 Health and +2 Pain Reduction to all friendly heroes." },
    "empowerment.t3.marksmanship": { title: "Marksmanship", desc: "Passive. Grants a permanent +2 Shooting and +2 Range to all friendly heroes." },
    "empowerment.t3.footwork": { title: "Footwork", desc: "Passive. Grants a permanent +1 Speed to all friendly heroes." },
    "empowerment.t3.discipline": { title: "Discipline", desc: "Passive. Grants a permanent +4 Cooldown and +2 Effect Duration to friendly heroes." },
    "empowerment.t4.forecheck": { title: "Forecheck", desc: "Passive. Grants +2 Steal Range mastery to friendly heroes, plus +4px steal radius in the middle third." },
    "empowerment.t4.fuelreserves": { title: "Fuel Reserves", desc: "Passive. Grants +2 Boost mastery to friendly heroes and reduces boost drain rate by 33%." },
    "empowerment.t4.heroportals": { title: "Hero Portals", desc: "Passive. Spawns repositioning node portals restricted to hero interactions." },
    "empowerment.t5.focusedtraining": { title: "Focused Training", desc: "Passive. Adds +3 points to your highest existing goalie mastery category." },
    "empowerment.t5.focusedtraining2": { title: "Focused Training II", desc: "Passive. Adds +3 points to your highest existing goalie mastery category." },
    "empowerment.t5.energysurge": { title: "Energy Surge", desc: "Active (50g). Instantly restores maximum fuel capacity for all friendly heroes." },
    "empowerment.t5.secondwind": { title: "Second Wind", desc: "Active (50g). Reduces active Q and W ability cooldown timers by 50%." },
    "empowerment.t5.heistcamp": { title: "Heist Camp", desc: "Passive. Grants +2 Speed mastery and a permanent +2px increase to steal radius for all friendly heroes." },
    "empowerment.t5.clutchgene": { title: "Clutch Gene", desc: "Passive. Grants +2 Damage mastery, and +30% defense (armor ratio) when in possession of the ball." },
    "empowerment.t6.dragonsbreath": { title: "Dragon's Breath", desc: "Passive. Spawns a miniboss in bot lane; slaying it grants team permanent FAST." },
    "empowerment.t6.apexform": { title: "Apex Form", desc: "Passive. Adds a permanent +1 allocation to all 9 core masteries for friendly heroes." },
    "empowerment.t6.bannerofcommand": { title: "Banner of Command", desc: "Passive. Allied minions gain +30% damage for each hero in their lane at spawn." },
    // Cultivation
    "cultivation.t1.manawell": { title: "Mana Well", desc: "Passive. Unlocks cultivation mana resource and generates passive mana tick." },
    "cultivation.t2.manainfusion": { title: "Mana Infusion", desc: "Active (50g). Instantly grants +100 mana and permanently increases tick rate by 10%." },
    "cultivation.t3.manacompounding": { title: "Mana Compounding", desc: "Passive. Multiplies passive mana tick rate based on the current mana bank." },
    "cultivation.t3.highermanacap": { title: "Higher Mana Cap", desc: "Passive. Increases the maximum goalie mana pool threshold to 1000." },
    "cultivation.t3.tollcollector": { title: "Toll Collector", desc: "Passive. Gain +5 goalie mana whenever a friendly minion crosses the midfield line." },
    "cultivation.t4.manavines": { title: "Mana Vines", desc: "Passive (250m). Spawns a base hazard zone that slows and burns enemies, granting +30 mana when an enemy passes through." },
    "cultivation.t4.manafrenzy": { title: "Mana Frenzy", desc: "Passive (275m). Boosts friendly hero cooldown recovery speed by 1% per 45 current mana." },
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
    "cultivation.t6.wallportals": { cost: 400, isMana: true },
    "cultivation.t6.uninhibitedportal": { cost: 275, isMana: true },
    "cultivation.t6.iceportal": { cost: 250, isMana: true }
};

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

// Every node has a real tier (t1-t6) and a purchase kind.
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
        { tier: 't4', name: 'bastionprotocol',    kind: 'cost' },
        { tier: 't4', name: 'deadwalls',          kind: 'cost' },
        { tier: 't4', name: 'barrage',            kind: 'cost' },
        // row 5
        { tier: 't5', name: 'noflyzoneperm',      kind: 'cost' },
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
        { tier: 't2', name: 'lowgravity',         kind: 'use'  },
        { tier: 't2', name: 'energyrush',         kind: 'use'  },
        { tier: 't5', name: 'callsiegeminion',    kind: 'use'  },
        { tier: 't5', name: 'anchor',             kind: 'use'  },
        { tier: 't5', name: 'shockgrenade',       kind: 'use'  },
        { tier: 't5', name: 'wallsdown',          kind: 'use'  },
        // row 2
        { tier: 't1', name: 'siegedoctrine',      kind: 'cost' },
        // row 3
        { tier: 't3', name: 'forwardmines',       kind: 'cost' },
        { tier: 't3', name: 'ballportal',         kind: 'cost' },
        { tier: 't3', name: 'rushlane',           kind: 'cost' },
        { tier: 't3', name: 'vanguards',          kind: 'cost' },
        { tier: 't3', name: 'pullgoalie',         kind: 'cost' },
        // row 4
        { tier: 't4', name: 'accumulators',       kind: 'cost' },
        { tier: 't4', name: 'parapet',            kind: 'cost' },
        // row 5
        { tier: 't5', name: 'incendiarymines',    kind: 'cost' },
        { tier: 't5', name: 'phalanx',            kind: 'cost' },
        { tier: 't5', name: 'saveprogress',       kind: 'cost' },
        // row 6
        { tier: 't6', name: 'maximumpressure',    kind: 'cost' },
        { tier: 't6', name: 'forwardmedics',      kind: 'cost' },
        { tier: 't6', name: 'multiball',          kind: 'cost' },
    ],
    GOALIE_TREE_EMPOWERMENT: [
        // row 1
        { tier: 't2', name: 'sharpshooter',       kind: 'use'  },
        { tier: 't5', name: 'energysurge',        kind: 'use'  },
        { tier: 't5', name: 'secondwind',         kind: 'use'  },
        // row 2
        { tier: 't1', name: 'combinecontract',    kind: 'cost' },
        // row 3
        { tier: 't3', name: 'grit',               kind: 'cost' },
        { tier: 't3', name: 'marksmanship',       kind: 'cost' },
        { tier: 't3', name: 'footwork',           kind: 'cost' },
        { tier: 't3', name: 'discipline',         kind: 'cost' },
        // row 4
        { tier: 't4', name: 'fuelreserves',       kind: 'cost' },
        { tier: 't4', name: 'heroportals',        kind: 'cost' },
        { tier: 't4', name: 'forecheck',          kind: 'cost' },
        // row 5
        { tier: 't5', name: 'heistcamp',          kind: 'cost' },
        { tier: 't5', name: 'clutchgene',         kind: 'cost' },
        { tier: 't5', name: 'focusedtraining',    kind: 'cost' },
        // row 6
        { tier: 't6', name: 'apexform',           kind: 'cost' },
        { tier: 't6', name: 'dragonsbreath',      kind: 'cost' },
        { tier: 't6', name: 'bannerofcommand',    kind: 'cost' },
    ],
    GOALIE_TREE_CULTIVATION: [
        // row 1
        { tier: 't2', name: 'manainfusion',       kind: 'use',  currency: 'mana' },
        { tier: 't5', name: 'manasurge',          kind: 'use',  currency: 'mana' },
        { tier: 't5', name: 'manasummon',         kind: 'use',  currency: 'mana' },
        // row 2
        { tier: 't1', name: 'manawell',           kind: 'cost' },
        // row 3
        { tier: 't3', name: 'manacompounding',    kind: 'cost' },
        { tier: 't3', name: 'highermanacap',      kind: 'cost' },
        { tier: 't3', name: 'tollcollector',      kind: 'cost' },
        // row 4
        { tier: 't4', name: 'manavines',          kind: 'cost', currency: 'mana' },
        { tier: 't4', name: 'manafrenzy',         kind: 'cost', currency: 'mana' },
        // row 5
        { tier: 't5', name: 'manapollinate',      kind: 'cost', currency: 'mana' },
        { tier: 't5', name: 'riskadjustedreturn', kind: 'cost', currency: 'mana' },
        { tier: 't5', name: 'tripledown',         kind: 'cost', currency: 'mana' },
        // row 6
        { tier: 't6', name: 'wallportals',        kind: 'cost', currency: 'mana' },
        { tier: 't6', name: 'uninhibitedportal',  kind: 'cost', currency: 'mana' },
        { tier: 't6', name: 'iceportal',          kind: 'cost', currency: 'mana' },
    ]
};

// Single source of truth for tab geometry
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

export const REQUIRED_TECHS = {
    siege:       [1, 0, 2, 1, 1],
    fortress:    [1, 0, 2, 1, 1],
    empowerment: [1, 0, 2, 1, 1],
    cultivation: [1, 0, 2, 1, 1],
};

export function tierUnlocked(shortName, tier, treeState) {
    const n = parseInt(tier.slice(1), 10);
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

export function isBuildOrderManaNode(order, item) {
    if (!item || !item.nodeKey) return false;
    const costData = HARDCODED_COSTS[item.nodeKey] || {};
    if (costData.isMana) return true;
    if (item.nodeKey.includes('.t5.') && !item.nodeKey.startsWith('cultivation.') && Array.isArray(order)) {
        const pollinateIdx = order.findIndex(it => it && it.nodeKey === 'cultivation.t5.manapollinate');
        const nodeIdx = order.indexOf(item);
        if (pollinateIdx !== -1 && nodeIdx > pollinateIdx) {
            const shortName = TREE_SHORT_NAME[item.tree];
            const hasT1 = order.some(it => it && it.nodeKey && it.nodeKey.startsWith(`${shortName}.t1.`));
            if (!hasT1) return true;
        }
    }
    return false;
}

export function getTreeState(game, team, activeKey) {
    const isHome = team === 0 || team === 'HOME';
    const rawUpgrades = isHome ? game.homeGoaliePurchasedUpgrades : game.awayGoaliePurchasedUpgrades;
    const purchased = new Set(rawUpgrades || []);

    const tierProgress = {};
    const shortName = TREE_SHORT_NAME[activeKey];
    const defs = NODE_DEFS[activeKey];

    if (defs) {
        defs.forEach(def => {
            const nodeKey = `${shortName}.${def.tier}.${def.name}`;
            if (def.kind === 'cost' && purchased.has(nodeKey)) {
                tierProgress[def.tier] = (tierProgress[def.tier] || 0) + 1;
            }
        });
    }

    return { purchased, tierProgress };
}
