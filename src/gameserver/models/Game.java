package gameserver.models;

import gameserver.effects.EffectPool;
import gameserver.engine.*;
import gameserver.entity.Box;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.targeting.ShapePayload;
import networking.ClientPacket;
import networking.PlayerDivider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Game {
    public String gameId;
    public UUID lastPossessed;
    public static final long GAMETICK_MS = 24;
    public List<PlayerDivider> clients;
    public ClientPacket[] lastControlPacket = null;
    public static final int SPRITE_X_EMPTY = 50;
    public static final int SPRITE_Y_EMPTY = 18;
    public int secondsToStart = 5;
    protected AtomicBoolean locked = new AtomicBoolean(false);
    public List<ShapePayload> colliders;
    public Entity[] allSolids;
    protected int curveFactor;
    public Titan underControl = null; //Only set by the gameserver right before pushing an update
    public boolean ended = false;
    public StatEngine stats = new StatEngine();
    public static final int MAX_X = 2048;
    public static final int E_MAX_X = 2030;
    public static final int MAX_Y = 988;
    public static final int E_MAX_Y = 950;
    public static final int MIN_X = 36;
    public static final int E_MIN_X = -10;
    public static final int MIN_Y = 232;
    public static final int E_MIN_Y = 170;
    public static final int GOALIE_Y_MAX = 890;
    public static final int GOALIE_Y_MIN = 290;
    public static final int GOALIE_XH_MAX = 289;
    public static final int GOALIE_XH_MIN = 133;
    public static final int GOALIE_XA_MAX = 1912;
    public static final int GOALIE_XA_MIN = 1776;
    protected static final int FIELD_LENGTH = 2050;
    protected static final int TOP_WING_ST = 0;
    protected static final int TOP_WING_END = 500;
    protected static final int BOT_WING_ST = 700;
    protected static final int BOT_WING_END = 9999;
    protected static final int DEFENDER_CREEP = 650;
    protected static final int DEFENDER_RETREAT = 0;
    protected static final int MID_CREEP = 1550;
    protected static final int MID_RETREAT = 550;
    protected static final int FW_CREEP = 1850;
    protected static final int FW_RETREAT = 1250;
    protected static final int TOP_WING_HOME = 287;
    protected static final int MID_WING_HOME = 587;
    protected static final int BOT_WING_HOME = 887;
    protected static final int T_CIRCLE_WING_HOME = 450;
    protected static final int B_CIRCLE_WING_HOME = 750;
    protected static final int DEFENDER_HOME = 455;
    protected static final int MID_HOME = 850;
    protected static final int FW_HOME = 900;
    public int framesSinceStart = 0;
    protected boolean hoopDmg = true;
    protected boolean suddenDeath = false;
    protected boolean extremeSuddenDeath = false;
    protected boolean tieAble = false;
    public final double GOALIE_DISABLE_TIME = 300.0; //300
    public final double PAIN_DISABLE_TIME = 420.0; //420
    public GameOptions options;

    public EffectPool effectPool = new EffectPool();
    public List<Entity> entityPool = new ArrayList<>();
    public boolean ballVisible, inGame, goalVisible;

    public int phase;
    public boolean began = false;
    public double xKickPow, yKickPow;

    public static final int HOME_HI_X = 223;
    public static final int HOME_HI_Y = 584;
    public static final int AWAY_HI_X = 1833;
    public static final int AWAY_HI_Y = 584;

    Titan hGol = new Titan(0, 0, TeamAffiliation.HOME, TitanType.GOALIE);
    Titan awGol = new Titan(1200, 400, TeamAffiliation.AWAY, TitanType.GOALIE);

    public Titan[] players = {hGol,
            awGol,
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.WARRIOR),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.DASHER),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.MAGE),
            //warrior mage are bad somehow in goalie mode idx 2,4
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.SUPPORT),

            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.RANGER),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.ARTISAN),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.MARKSMAN),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.STEALTH) //bugfix where not displayed
    };
    public GoalHoop homeHiGoal = new GoalHoop(HOME_HI_X, HOME_HI_Y, 56, 70, TeamAffiliation.HOME);
    public GoalHoop awayHiGoal = new GoalHoop(AWAY_HI_X, AWAY_HI_Y, 56, 70, TeamAffiliation.AWAY);
    public GoalHoop[] hiGoals = {homeHiGoal, awayHiGoal};
    public GoalHoop[] lowGoals = {new GoalHoop(265, 348, 43, 107, TeamAffiliation.HOME),
            new GoalHoop(265, 784, 43, 107, TeamAffiliation.HOME),
            new GoalHoop(1815, 348, 43, 107, TeamAffiliation.AWAY),
            new GoalHoop(1815, 784, 43, 107, TeamAffiliation.AWAY)};
    public Team away = new Team(TeamAffiliation.AWAY, 0.0, awayHiGoal, lowGoals[2], lowGoals[3]);
    public Team home = new Team(TeamAffiliation.HOME, 0.0, homeHiGoal, lowGoals[0], lowGoals[1],
            players);

    public Box ball = new Box(0, 0, 30, 30);

    public boolean anyPoss() {
        for (Titan t : players) {
            if (t.possession == 1) {
                return true;
            }
        }
        return false;
    }

    public boolean anyBallMoveState() {
        for (Titan t : players) {
            if (t.actionState == Titan.TitanState.SHOOT ||
                    t.actionState == Titan.TitanState.CURVE_RIGHT || t.actionState ==
                    Titan.TitanState.CURVE_LEFT || t.actionState == Titan.TitanState.LOB) {
                return true;
            }
        }
        return false;
    }


    public PlayerDivider clientFromTitan(Entity t) {
        for (PlayerDivider pd : clients) {
            List<Integer> sels = pd.possibleSelection;
            for (Integer i : sels) {
                if (players[i - 1].id.equals(t.id)) {
                    return pd;
                }
            }
        }
        return null;
    }

    public void cullOldColliders() {
        if (this.colliders == null) {
            this.colliders = new ArrayList<>();
        }
        ArrayList<ShapePayload> rm = new ArrayList<>();
        for (int i=0; i<this.colliders.size(); i++) {
            ShapePayload coll = this.colliders.get(i);
            if (!coll.checkDisp()) {
                rm.add(coll);
            }
        }
        this.colliders.removeAll(rm);
    }
}
