package gameserver.gamemanager;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum GamePhase implements Serializable {
    CREDITS(0),
    CONTROLS(1),
    SHOW_GAME_MODES(2),
    DRAW_CLASS_SCREEN(3),
    SET_MASTERIES(4),
    TRANSITIONAL(5),
    WAIT_FOR_GAME(6),
    COUNTDOWN(7),
    INGAME(8),
    SCORE_FREEZE(9),
    TOURNAMENT_CODE(10),
    TEAM_LAUNCH(20),
    DRAFT_HOMEBAN(11),
    DRAFT_AWAYBAN(12),
    DRAFT_HOMEMID(13),
    DRAFT_AWAYMID(14),
    DRAFT_HOMETOP(15),
    DRAFT_AWAYTOP(16),
    DRAFT_HOMEBOT(17),
    DRAFT_AWAYBOT(18),
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