import { gameState } from '../state.js';

export const AssetManager = {
    images: {},
    audio: {},
    loadSprite(key, src) {
        const img = new Image();
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
    if (cName === 'GOALIE') return 'SpritePost';
    if (cName === 'DASHER') return 'SpriteSlasher';
    if (cName === 'GOLEM') return 'SpriteGuardian';
    // Title-case formatting
    return 'Sprite' + cName.charAt(0) + cName.slice(1).toLowerCase();
}

export function initAssets() {
    // Basic UI and Court
    AssetManager.loadSprite('field', '/res/Court/field.png');
    AssetManager.loadSprite('logo', '/res/Court/logo2.png');
    AssetManager.loadSprite('selector', '/res/Court/select.png');
    AssetManager.loadSprite('ballA', '/res/Court/ballA.png');
    AssetManager.loadSprite('ballB', '/res/Court/ballB.png');
    AssetManager.loadSprite('ballFA', '/res/Court/ballFA.png');
    AssetManager.loadSprite('ballFB', '/res/Court/ballFB.png');
    AssetManager.loadSprite('ballPtr', '/res/Court/ballptr.png');
    AssetManager.loadSprite('ballFPtr', '/res/Court/ballfptr.png');
    AssetManager.loadSprite('victory', '/res/Court/victory.png');
    AssetManager.loadSprite('defeat', '/res/Court/defeat.png');
    AssetManager.loadSprite('tie', '/res/Court/tie.png');
    AssetManager.loadSprite('lobby', '/res/Court/lobby.png');

    // Minions
    AssetManager.loadSprite('wall', '/res/Court/wall.png');
    AssetManager.loadSprite('trap1', '/res/Court/trap.png');
    AssetManager.loadSprite('trap2', '/res/Court/trap2.png');
    AssetManager.loadSprite('portal1', '/res/Court/portal.png');
    AssetManager.loadSprite('portal2', '/res/Court/portal2.png');
    AssetManager.loadSprite('portalcd', '/res/Court/portalcd.png');
    AssetManager.loadSprite('bportal1', '/res/Court/ballp.png');
    AssetManager.loadSprite('bportal2', '/res/Court/ballp2.png');
    AssetManager.loadSprite('bportalcd', '/res/Court/ballpcd.png');
    AssetManager.loadSprite('fire1', '/res/Court/fireA.png');
    AssetManager.loadSprite('fire2', '/res/Court/fireB.png');
    AssetManager.loadSprite('cage', '/res/Court/caged.png');
    AssetManager.loadSprite('wolf1L', '/res/Wolf/wolfL.png');
    AssetManager.loadSprite('wolf2L', '/res/Wolf/wolf2L.png');
    AssetManager.loadSprite('wolf3L', '/res/Wolf/wolf3L.png');
    AssetManager.loadSprite('wolf5L', '/res/Wolf/wolf5L.png');
    AssetManager.loadSprite('wolf1R', '/res/Wolf/wolfR.png');
    AssetManager.loadSprite('wolf2R', '/res/Wolf/wolf2R.png');
    AssetManager.loadSprite('wolf3R', '/res/Wolf/wolf3R.png');
    AssetManager.loadSprite('wolf5R', '/res/Wolf/wolf5R.png');

    // Load classes
    classNames.forEach(cName => {
        const dir = getDirectoryName(cName);
        const base = `/res/${dir}/`;
        AssetManager.loadSprite(`${cName}_standL`, base + 'standL.png');
        AssetManager.loadSprite(`${cName}_standR`, base + 'standR.png');
        AssetManager.loadSprite(`${cName}_runAL`, base + 'runAL.png');
        AssetManager.loadSprite(`${cName}_runAR`, base + 'runAR.png');
        AssetManager.loadSprite(`${cName}_runBL`, base + 'runBL.png');
        AssetManager.loadSprite(`${cName}_runBR`, base + 'runBR.png');
        AssetManager.loadSprite(`${cName}_atk1L`, base + 'atk1L.png');
        AssetManager.loadSprite(`${cName}_atk1R`, base + 'atk1R.png');
        AssetManager.loadSprite(`${cName}_atk2L`, base + 'atk2L.png');
        AssetManager.loadSprite(`${cName}_atk2R`, base + 'atk2R.png');
        AssetManager.loadSprite(`${cName}_shotL`, base + 'shotL.png');
        AssetManager.loadSprite(`${cName}_shotR`, base + 'shotR.png');
        AssetManager.loadSprite(`${cName}_passL`, base + 'passL.png');
        AssetManager.loadSprite(`${cName}_passR`, base + 'passR.png');
        AssetManager.loadSprite(`${cName}_dieL`, base + 'dieL.png');
        AssetManager.loadSprite(`${cName}_dieR`, base + 'dieR.png');
    });

    AssetManager.loadAudio('shot', '/res/Sound/shotsound.wav');
}
