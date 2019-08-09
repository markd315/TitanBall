package gameserver.models;

import client.ClientPacket;
import gameserver.effects.EffectPool;
import gameserver.engine.GoalHoop;
import gameserver.engine.StatEngine;
import gameserver.engine.Team;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.targeting.ShapePayload;
import networking.PlayerDivider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Game {
    public String gameId;
    protected UUID lastPossessed;
    public static final double SOFT_WIN = 3.0, WIN_BY = 2.0, HARD_WIN = 8.0;
    protected static final long GAMETICK_MS = 25;
    public List<PlayerDivider> clients;
    public ClientPacket[] lastControlPacket = null;
    public static final int SPRITE_X_EMPTY = 50;
    public static final int SPRITE_Y_EMPTY = 18;
    protected AtomicBoolean locked = new AtomicBoolean(false);
    public List<ShapePayload> colliders;
    public Entity[] allSolids;
    protected int curveFactor;
    public Titan underControl = null; //Only set by the gameserver right before pushing an update
    public boolean ended = false;
    public StatEngine stats = new StatEngine();
    public static final int MAX_X = 2070;
    public static final int MAX_Y = 1010;
    public static final int MIN_X = 50;
    public static final int MIN_Y = 230;
    public static final int GOALIE_Y_MAX = 890;
    public static final int GOALIE_Y_MIN = 290;
    public static final int GOALIE_XH_MAX = 179;
    public static final int GOALIE_XH_MIN = 43;
    public static final int GOALIE_XA_MAX = 2002;
    public static final int GOALIE_XA_MIN = 1866;
    protected final int FIELD_LENGTH = 2050;
    protected final int TOP_WING_ST = 0;
    protected final int TOP_WING_END = 500;
    protected final int BOT_WING_ST = 700;
    protected final int BOT_WING_END = 9999;
    protected final int DEFENDER_CREEP = 650;
    protected final int DEFENDER_RETREAT = 0;
    protected final int MID_CREEP = 1550;
    protected final int MID_RETREAT = 550;
    protected final int FW_CREEP = 1850;
    protected final int FW_RETREAT = 1250;
    protected final int TOP_WING_HOME = 287;
    protected final int MID_WING_HOME = 587;
    protected final int BOT_WING_HOME = 887;
    protected final int T_CIRCLE_WING_HOME = 450;
    protected final int B_CIRCLE_WING_HOME = 750;
    protected final int DEFENDER_HOME = 325;
    protected final int MID_HOME = 850;
    protected final int FW_HOME = 900;

    public EffectPool effectPool = new EffectPool();
    public List<Entity> entityPool = new ArrayList<>();
    public boolean ballVisible, inGame, goalVisible;

    public int phase;
    public long startTime;
    public long startGameTime;
    public long endGameTime;
    public int timeSpent;
    public boolean began = false;
    public double xKickPow, yKickPow;

    public final int HOME_HI_X = 133;
    public final int HOME_HI_Y = 584;
    public final int AWAY_HI_X = 1923;
    public final int AWAY_HI_Y = 584;

    Titan hGol = new Titan(0, 0, TeamAffiliation.HOME, TitanType.GUARDIAN);
    Titan awGol = new Titan(1200, 400, TeamAffiliation.AWAY, TitanType.GUARDIAN);

    public Titan[] players = {hGol,
            awGol,
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.WARRIOR),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.SLASHER),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.MAGE),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.SUPPORT),

            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.RANGER),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.ARTISAN),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.MARKSMAN),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.STEALTH) //bugfix where not displayed
    };
    public GoalHoop homeHiGoal = new GoalHoop(HOME_HI_X, HOME_HI_Y, 56, 70, TeamAffiliation.HOME);
    public GoalHoop awayHiGoal = new GoalHoop(AWAY_HI_X, AWAY_HI_Y, 56, 70, TeamAffiliation.AWAY);
    public GoalHoop[] hiGoals = {homeHiGoal, awayHiGoal};
    public GoalHoop[] lowGoals = {new GoalHoop(189, 353, 23, 97, TeamAffiliation.HOME),
            new GoalHoop(189, 789, 23, 97, TeamAffiliation.HOME),
            new GoalHoop(1901, 353, 23, 97, TeamAffiliation.AWAY),
            new GoalHoop(1901, 789, 23, 97, TeamAffiliation.AWAY)};
    public Team away = new Team(TeamAffiliation.AWAY, 0.0, awayHiGoal, lowGoals[2], lowGoals[3]);
    public Team home = new Team(TeamAffiliation.HOME, 0.0, homeHiGoal, lowGoals[0], lowGoals[1],
            players);

    public Box ball = new Box(0, 0, 15, 15);

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
                    Titan.TitanState.CURVE_LEFT || t.actionState == Titan.TitanState.PASS) {
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
