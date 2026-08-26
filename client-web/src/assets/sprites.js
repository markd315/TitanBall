import { gameState } from '../state.js';

function removeGridLines(ctx, width = 70, height = 70) {
    const imgData = ctx.getImageData(0, 0, width, height);
    const data = imgData.data;

    function getAlpha(x, y) {
        return data[(y * width + x) * 4 + 3];
    }
    function clearPixel(x, y) {
        const idx = (y * width + x) * 4;
        data[idx] = 0;
        data[idx + 1] = 0;
        data[idx + 2] = 0;
        data[idx + 3] = 0;
    }

    // Check 1-2 pixel border rows (top: y=0,1; bottom: y=height-2, height-1)
    for (const y of [0, 1, height - 2, height - 1]) {
        let opaqueCount = 0;
        let maxRun = 0;
        let curRun = 0;
        for (let x = 0; x < width; x++) {
            if (getAlpha(x, y) > 30) {
                opaqueCount++;
                curRun++;
                if (curRun > maxRun) maxRun = curRun;
            } else {
                curRun = 0;
            }
        }
        if (maxRun >= 35 || opaqueCount >= 45) {
            for (let x = 0; x < width; x++) {
                clearPixel(x, y);
            }
        }
    }

    // Check 1-2 pixel border columns (left: x=0,1; right: x=width-2, width-1)
    for (const x of [0, 1, width - 2, width - 1]) {
        let opaqueCount = 0;
        let maxRun = 0;
        let curRun = 0;
        for (let y = 0; y < height; y++) {
            if (getAlpha(x, y) > 30) {
                opaqueCount++;
                curRun++;
                if (curRun > maxRun) maxRun = curRun;
            } else {
                curRun = 0;
            }
        }
        if (maxRun >= 35 || opaqueCount >= 45) {
            for (let y = 0; y < height; y++) {
                clearPixel(x, y);
            }
        }
    }

    ctx.putImageData(imgData, 0, 0);
}

function processUnifiedSpriteSheet(cName, img) {
    const isHorizontal = img.width >= img.height * 2;
    const frameCount = 8;
    const frameW = isHorizontal ? img.width / frameCount : img.width;
    const frameH = isHorizontal ? img.height : img.height / frameCount;

    // Order specified for 1x8 unified sheet:
    // 0: standing, 1: runA, 2: runB, 3: throw, 4: steal, 5: death, 6: ability1, 7: ability2
    const FRAME_MAPPINGS = [
        ['stand'],                     // 0: standing
        ['runA'],                     // 1: runA
        ['runB'],                     // 2: runB
        ['shot', 'pass', 'throw'],    // 3: throw
        ['steal'],                    // 4: steal
        ['die', 'death'],             // 5: death
        ['atk1', 'ability1'],         // 6: ability1
        ['atk2', 'ability2']          // 7: ability2
    ];

    for (let i = 0; i < frameCount; i++) {
        const sx = isHorizontal ? Math.round(i * frameW) : 0;
        const sy = isHorizontal ? 0 : Math.round(i * frameH);
        const sw = Math.round(frameW);
        const sh = Math.round(frameH);

        // Right-facing canvas
        const cR = document.createElement('canvas');
        cR.width = 70;
        cR.height = 70;
        const ctxR = cR.getContext('2d');
        ctxR.imageSmoothingEnabled = false;
        ctxR.drawImage(img, sx, sy, sw, sh, 0, 0, 70, 70);
        removeGridLines(ctxR, 70, 70);
        cR.isUnified = true;

        // Left-facing flipped canvas
        const cL = document.createElement('canvas');
        cL.width = 70;
        cL.height = 70;
        const ctxL = cL.getContext('2d');
        ctxL.imageSmoothingEnabled = false;
        ctxL.translate(70, 0);
        ctxL.scale(-1, 1);
        ctxL.drawImage(cR, 0, 0);
        cL.isUnified = true;

        // Populate AssetManager.images with priority override
        const keys = FRAME_MAPPINGS[i] || [];
        for (const anim of keys) {
            AssetManager.images[`${cName}_${anim}R`] = cR;
            AssetManager.images[`${cName}_${anim}L`] = cL;
        }
    }
}

export const AssetManager = {
    images: {},
    audio: {},
    loadSprite(key, src) {
        const img = new Image();
        img.onerror = () => {
            console.warn(`AssetManager: "${key}" not found at ${src} (ok if this class doesn't have this animation)`);
            delete this.images[key];
        };
        img.src = src;
        this.images[key] = img;
    },
    loadUnifiedSprite(cName, src) {
        const img = new Image();
        img.onload = () => {
            console.log(`AssetManager: Unified spritesheet loaded for ${cName} from ${src}`);
            processUnifiedSpriteSheet(cName, img);
        };
        img.onerror = () => {
            // Optional: class does not have a unified spritesheet, fallback animations remain active
        };
        img.src = src;
    },
    loadAudio(key, src) {
        const aud = new Audio(src);
        this.audio[key] = aud;
    }
};

const classNames = [
    'GOALIE', 'WARRIOR', 'RANGER', 'DASHER', 'MARKSMAN', 'STEALTH',
    'SUPPORT', 'ARTISAN', 'GOLEM', 'MAGE', 'BUILDER', 'GRENADIER', 'HOUNDMASTER'
];

function getDirectoryName(cName) {
    if (cName === 'GOALIE') return 'SpriteGuardian';
    if (cName === 'DASHER') return 'SpriteSlasher';
    if (cName === 'GOLEM') return 'SpritePost';
    // Title-case formatting
    return 'Sprite' + cName.charAt(0) + cName.slice(1).toLowerCase();
}

export function initAssets() {
    // Basic UI and Court
    AssetManager.loadSprite('field', 'res/Court/field.png');
    AssetManager.loadSprite('logo', 'res/Court/logo2.png');
    AssetManager.loadSprite('selector', 'res/Court/select.png');
    AssetManager.loadSprite('ballA', 'res/Court/ballA.png');
    AssetManager.loadSprite('ballB', 'res/Court/ballB.png');
    AssetManager.loadSprite('ballFA', 'res/Court/ballFA.png');
    AssetManager.loadSprite('ballFB', 'res/Court/ballFB.png');
    AssetManager.loadSprite('ballPtr', 'res/Court/ballptr.png');
    AssetManager.loadSprite('ballFPtr', 'res/Court/ballfptr.png');
    AssetManager.loadSprite('victory', 'res/Court/victory.png');
    AssetManager.loadSprite('defeat', 'res/Court/defeat.png');
    AssetManager.loadSprite('tie', 'res/Court/tie.png');
    AssetManager.loadSprite('lobby', 'res/Court/lobby.png');
    AssetManager.loadSprite('goal', 'res/Court/goal.png');
    AssetManager.loadSprite('lock', 'res/Court/lock.png');
    AssetManager.loadSprite('star', 'res/Court/star.png');
    //upgrades
    AssetManager.loadSprite('GOALIE_TREE_SIEGE', 'res/CoachUpgradeTrees/siege.png');
    AssetManager.loadSprite('GOALIE_TREE_FORTRESS', 'res/CoachUpgradeTrees/fortress.png');
    AssetManager.loadSprite('GOALIE_TREE_EMPOWERMENT', 'res/CoachUpgradeTrees/empowerment.png');
    AssetManager.loadSprite('GOALIE_TREE_CULTIVATION', 'res/CoachUpgradeTrees/cultivation.png');


    // Minions
    AssetManager.loadSprite('wall', 'res/Court/wall.png');
    AssetManager.loadSprite('parapet_home', 'res/Court/parapet_home.png');
    AssetManager.loadSprite('parapet_away', 'res/Court/parapet_away.png');
    AssetManager.loadSprite('trap1', 'res/Court/trap.png');
    AssetManager.loadSprite('trap2', 'res/Court/trap2.png');
    AssetManager.loadSprite('vines', 'res/Court/vines.png');
    AssetManager.loadSprite('portal1', 'res/Court/portal.png');
    AssetManager.loadSprite('portal2', 'res/Court/portal2.png');
    AssetManager.loadSprite('portalcd', 'res/Court/portalcd.png');
    AssetManager.loadSprite('bportal1', 'res/Court/ballp.png');
    AssetManager.loadSprite('bportal2', 'res/Court/ballp2.png');
    AssetManager.loadSprite('bportalcd', 'res/Court/ballpcd.png');
    AssetManager.loadSprite('fireH1', 'res/Court/fireH.png');
    AssetManager.loadSprite('fireH2', 'res/Court/fireH2.png');
    AssetManager.loadSprite('fireA1', 'res/Court/fireA.png');
    AssetManager.loadSprite('fireA2', 'res/Court/fireA2.png');
    AssetManager.loadSprite('cage', 'res/Court/caged.png');
    AssetManager.loadSprite('dragon', 'res/Court/dragon.png');
    AssetManager.loadSprite('wolf1L', 'res/Wolf/wolfL.png');
    AssetManager.loadSprite('wolf2L', 'res/Wolf/wolf2L.png');
    AssetManager.loadSprite('wolf3L', 'res/Wolf/wolf3L.png');
    AssetManager.loadSprite('wolf5L', 'res/Wolf/wolf5L.png');
    AssetManager.loadSprite('wolf1R', 'res/Wolf/wolfR.png');
    AssetManager.loadSprite('wolf2R', 'res/Wolf/wolf2R.png');
    AssetManager.loadSprite('wolf3R', 'res/Wolf/wolf3R.png');
    AssetManager.loadSprite('wolf5R', 'res/Wolf/wolf5R.png');

    // Load classes
    const ANIM_SETS = {
        stand: ['standL', 'standR'],
        run:   ['runAL', 'runAR', 'runBL', 'runBR'],
        atk:   ['atk1L', 'atk1R', 'atk2L', 'atk2R'],
        shot:  ['shotL', 'shotR'],
        pass:  ['passL', 'passR'],
        die:   ['dieL', 'dieR'],
        steal: ['stealL', 'stealR'],
    };

    function loadClassSprites(cName) {
        const dir = getDirectoryName(cName);
        const base = `res/${dir}/`;

        // 1. Fallback: load separate individual animation files
        for (const suffix of Object.values(ANIM_SETS).flat()) {
            AssetManager.loadSprite(`${cName}_${suffix}`, base + `${suffix}.png`);
        }

        // 2. Priority: load unified 1x8 sprite sheet at runtime if available
        AssetManager.loadUnifiedSprite(cName, base + 'unified.png');
    }

    // in initAssets():
    classNames.forEach(loadClassSprites);
    // Load effect icons
    const effectIds = [
      'BLEED', 'BLIND', 'COOLDOWN_CURVE', 'COOLDOWN_Q', 'COOLDOWN_STEAL', 'COOLDOWN_W',
      'CURSED', 'DEAD', 'DEFENSE', 'FAST', 'FLARE', 'HEAL',
      'HIDE_BALL', 'PASS', 'ROOT', 'SHOOT', 'SLOW', 'STEAL', 'STEALTHED', 'STUN'
    ];
    effectIds.forEach(effectId => {
      AssetManager.loadSprite(`EFFECT_${effectId}`, `res/Effects/${effectId}.png`);
    });
    AssetManager.loadSprite('EFFECT_COOLDOWN_GOALIE', 'res/Effects/COOLDOWN_Q.png');

    AssetManager.loadAudio('shot', 'res/Sound/shotsound.wav');
    AssetManager.loadAudio('tut0', 'res/Sound/tut0.wav');
    AssetManager.loadAudio('tut1', 'res/Sound/tut1.wav');
    AssetManager.loadAudio('tut2', 'res/Sound/tut2.wav');
    AssetManager.loadAudio('tut3', 'res/Sound/tut3.wav');
    AssetManager.loadAudio('tut4', 'res/Sound/tut4.wav');
}
