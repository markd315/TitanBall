package gameserver.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import gameserver.Const;
import gameserver.effects.EffectPool;
import gameserver.engine.*;
import gameserver.entity.Box;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.gamemanager.GamePhase;
import gameserver.targeting.ShapePayload;
import networking.ClientPacket;
import networking.PlayerDivider;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Game implements Serializable {

    @JsonProperty
    public String gameId;
    @JsonProperty
    public UUID lastPossessed;
    public Const c = new Const("res/game.cfg");
    public final int GAMETICK_MS = c.GAMETICK_MS;
    @JsonProperty
    public double secondsToStart = c.getD("server.startDelay");
    @JsonProperty
    public List<PlayerDivider> clients;
    @JsonProperty
    public ClientPacket[] lastControlPacket = null;

    @JsonProperty
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    public LocalDateTime now;

    @JsonProperty
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    public LocalDateTime serverTimeStamp; //TODO use this to make sure client rendering order is clean.
    @JsonProperty
    public List<ShapePayload> colliders;
    @JsonProperty
    public Entity[] allSolids;
    @JsonProperty
    protected int curveFactor;
    @JsonProperty
    public Titan underControl = null; //Only set by the gameserver right before pushing an update
    @JsonProperty
    public boolean ended = false;
    @JsonProperty
    public StatEngine stats = new StatEngine();

    @JsonProperty
    public int framesSinceStart = 0;
    @JsonProperty
    protected boolean hoopDmg = true;
    @JsonProperty
    protected boolean suddenDeath = false;
    @JsonProperty
    protected boolean extremeSuddenDeath = false;
    @JsonProperty
    protected boolean tieAble = false;
    @JsonProperty
    public final double GOALIE_DISABLE_TIME = c.getD("goalie.disable.time");
    @JsonProperty
    public final double PAIN_DISABLE_TIME = 9999999.0;
    @JsonProperty
    public GameOptions options;
    @JsonProperty
    public EffectPool effectPool = new EffectPool();
    @JsonProperty
    public List<Entity> entityPool = new ArrayList<>();
    @JsonProperty
    public boolean ballVisible, inGame, goalVisible;

    @JsonProperty
    public GamePhase phase;
    @JsonProperty
    public boolean began = false;
    @JsonProperty
    public double xKickPow, yKickPow;

    @JsonProperty
    Titan hGol = new Titan(0, 0, TeamAffiliation.HOME, TitanType.GOALIE);
    @JsonProperty
    Titan awGol = new Titan(1200, 400, TeamAffiliation.AWAY, TitanType.GOALIE);

    @JsonProperty
    public Titan[] players = {hGol,
            awGol,
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.WARRIOR),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.BUILDER),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.MAGE),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.SUPPORT),
            new Titan(0, 0, TeamAffiliation.HOME, TitanType.DASHER),

            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.ARTISAN),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.RANGER),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.HOUNDMASTER),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.STEALTH),
            new Titan(0, 0, TeamAffiliation.AWAY, TitanType.MARKSMAN),
    };

    @JsonProperty
    public Box ball = new Box(0, 0, c.getI("ball.w"), c.getI("ball.h"));

    @JsonProperty
    public Map<String, TitanType> picksAndBans = new HashMap<>();
    @JsonProperty
    public TeamAffiliation yourteam;

    protected final int FIELD_LENGTH = c.getI("pos.field");
    protected final int TOP_WING_HOME = c.getI("pos.top.y");
    protected final int MID_WING_HOME = c.getI("pos.mid.y");
    protected final int BOT_WING_HOME = c.getI("pos.bot.y");
    protected final int DEFENDER_HOME = c.getI("pos.def.x");
    protected final int MID_HOME = c.getI("pos.mid.x");
    protected final int FW_HOME = c.getI("pos.fw.x");
    public final int HOME_HI_X = c.getI("goal.home.hi.x");
    public final int HOME_HI_Y = c.getI("goal.hi.y");
    public final int AWAY_HI_X = c.getI("goal.away.hi.x");
    public final int AWAY_HI_Y = c.getI("goal.hi.y");

    public GoalHoop homeHiGoal = new GoalHoop(HOME_HI_X, HOME_HI_Y, c.getI("goal.hi.width"), c.getI("goal.hi.height"), TeamAffiliation.HOME);
    public GoalHoop awayHiGoal = new GoalHoop(AWAY_HI_X, AWAY_HI_Y, c.getI("goal.hi.width"), c.getI("goal.hi.height"), TeamAffiliation.AWAY);
    public GoalHoop[] hiGoals = {homeHiGoal, awayHiGoal};
    public GoalHoop[] lowGoals = {new GoalHoop(c.getI("goal.home.low.x"), c.getI("goal.low.y"), c.getI("goal.low.width"), c.getI("goal.low.height"), TeamAffiliation.HOME),
            new GoalHoop(c.getI("goal.home.low.x"), c.getI("goal.low2.y"), c.getI("goal.low.width"), c.getI("goal.low.height"), TeamAffiliation.HOME),
            new GoalHoop(c.getI("goal.away.low.x"), c.getI("goal.low.y"), c.getI("goal.low.width"), c.getI("goal.low.height"), TeamAffiliation.AWAY),
            new GoalHoop(c.getI("goal.away.low.x"), c.getI("goal.low2.y"), c.getI("goal.low.width"), c.getI("goal.low.height"), TeamAffiliation.AWAY)};
    @JsonProperty
    public Team away = new Team(TeamAffiliation.AWAY, 0.0, awayHiGoal, lowGoals[2], lowGoals[3]);
    @JsonProperty
    public Team home = new Team(TeamAffiliation.HOME, 0.0, homeHiGoal, lowGoals[0], lowGoals[1],
            players);

    public final int SPRITE_X_EMPTY = c.getI("titan.hitbox.empty.x");
    public final int SPRITE_Y_EMPTY = c.getI("titan.hitbox.empty.y");

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
    //used for legacy AI only
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
    protected final int T_CIRCLE_WING_HOME = 450;
    protected final int B_CIRCLE_WING_HOME = 750;
}
