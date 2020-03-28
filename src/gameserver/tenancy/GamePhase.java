package gameserver.tenancy;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum GamePhase {
    CREDITS(0),
    CONTROLS(1),
    SHOW_GAME_MODES(15),
    DRAW_CLASS_SCREEN(2),
    SET_MASTERIES(3),
    TRANSITIONAL(4),
    WAIT_FOR_GAME(6),
    COUNTDOWN(7),
    INGAME(8),
    SCORE_FREEZE(9),
    TOURNAMENT_CODE(69),
    TUTORIAL_START(100),
    TUTORIAL(101);

    private static final Map<Integer, GamePhase> lookup
            = new HashMap<Integer, GamePhase>();

    static {
        for (GamePhase s : EnumSet.allOf(GamePhase.class))
            lookup.put(s.getCode(), s);
    }

    private int code;

    private GamePhase(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static GamePhase get(int code) {
        return lookup.get(code);
    }

    public boolean pregame() {
        return this == CREDITS || this == CONTROLS || this == DRAW_CLASS_SCREEN
                || this == SET_MASTERIES || this == TRANSITIONAL
                || this == WAIT_FOR_GAME
                || this == COUNTDOWN;
    }
}