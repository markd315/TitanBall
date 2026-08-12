import { GamePhase } from './constants.js';

export const clientUI = {
    goalieTabIndex: -1
};

export const gameState = {
    phase: GamePhase.CREDITS,
    game: null,
    controlsHeld: {
        UP: false,
        LEFT: false,
        DOWN: false,
        RIGHT: false,
        E: false,
        R: false,
        CAM: false,
        STEAL: false,
        SWITCH: false,
        BOOST: false,
        BOOST_LOCK: false,
        lobBtn: false,
        shotBtn: false,
        posX: -1,
        posY: -1,
        camX: 0,
        camY: 0,
        token: null,
        gameID: null,
        classSelection: null,
        masteries: null
    },
    pendingGoalieBuy: null,
    camX: 500,
    camY: 300,
    camFollow: true,
    token: null,
    gameID: null,
    prevHomeScore: 0,
    prevAwayScore: 0,
    prevGoalVisible: false,
    goalComboType: null
};