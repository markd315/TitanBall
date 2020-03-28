package gameserver;

import util.ConstOperations;

public class Const extends ConstOperations {
    public final double PAIN_FACTOR = getD("globals.ent.painfactor");
    public final double FLAT_PAIN = getD("globals.ent.flatdamage");
    public final double MAX_HEAL =getD("globals.titan.maxheal");
    public final double MAX_PAIN = getD("globals.titan.maxpain");
    public final int FAR_RANGE = getI("globals.far.range");
    public final int STEAL_CD = getI("titan.steal.cdms");
    public final int STOLEN_STUN = getI("titan.steal.effectms");
    public final int GAMETICK_MS = getI("globals.gametick.ms");
    public final double SHOT_FREEZE_RATIO = getD("globals.shot.caststun");
    public boolean GOALIE_DISABLED = getB("globals.goalie.disabled");
    public final double BALL_X = getD("ball.x");
    public final double BALL_Y = getD("ball.y");
    public final int GOALIE_Y_MIN = getI("goalie.box.y");
    public final int GOALIE_Y_MAX = GOALIE_Y_MIN + getI("goalie.box.h");
    public final int GOALIE_XH_MIN = getI("goalie.box.xh");
    public final int GOALIE_XH_MAX = GOALIE_XH_MIN + getI("goalie.box.w");
    public final int GOALIE_XA_MIN = getI("goalie.box.xa");
    public final int GOALIE_XA_MAX = GOALIE_XA_MIN + getI("goalie.box.w");
    public final int MAX_X = getI("max.x");
    public final int E_MAX_X = getI("max.ex");
    public final int MAX_Y = getI("max.y");
    public final int E_MAX_Y = getI("max.ey");
    public final int MIN_X = getI("min.x");
    public final int E_MIN_X = getI("min.ex");
    public final int MIN_Y = getI("min.y");
    public final int E_MIN_Y = getI("min.ey");

    public Const() {//kryo
        super("res/game.cfg");
    }

    public Const(String fn) {//kryo
        super(fn);
    }
}
