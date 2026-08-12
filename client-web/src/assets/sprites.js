import { gameState } from '../state.js';

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
    AssetManager.loadSprite('trap1', 'res/Court/trap.png');
    AssetManager.loadSprite('trap2', 'res/Court/trap2.png');
    AssetManager.loadSprite('portal1', 'res/Court/portal.png');
    AssetManager.loadSprite('portal2', 'res/Court/portal2.png');
    AssetManager.loadSprite('portalcd', 'res/Court/portalcd.png');
    AssetManager.loadSprite('bportal1', 'res/Court/ballp.png');
    AssetManager.loadSprite('bportal2', 'res/Court/ballp2.png');
    AssetManager.loadSprite('bportalcd', 'res/Court/ballpcd.png');
    AssetManager.loadSprite('fire1', 'res/Court/fireA.png');
    AssetManager.loadSprite('fire2', 'res/Court/fireB.png');
    AssetManager.loadSprite('cage', 'res/Court/caged.png');
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
    };

    function loadClassSprites(cName) {
        const dir = getDirectoryName(cName);
        const base = `res/${dir}/`;

        for (const suffix of Object.values(ANIM_SETS).flat()) {
            AssetManager.loadSprite(`${cName}_${suffix}`, base + `${suffix}.png`);
        }
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
