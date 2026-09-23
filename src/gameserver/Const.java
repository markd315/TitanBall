package gameserver;

import util.ConstOperations;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Const extends ConstOperations   {
    public static final long serialVersionUID = 1L;
    public final double PAIN_FACTOR = getD("globals.ent.painfactor");
    public final double FLAT_PAIN = getD("globals.ent.flatdamage");
    public final double MAX_HEAL =getD("globals.titan.maxheal");
    public final double MAX_PAIN = getD("globals.titan.maxpain");
    public final int FAR_RANGE = getI("globals.far.range");
    public final int STEAL_CD = getI("titan.steal.cdms");
    public final int STOLEN_STUN = getI("titan.steal.effectms");
    public final int GAMETICK_MS = getI("globals.gametick.ms");
    public final boolean HEADLESS_ENABLED = hasKey("headless.enabled") ? getB("headless.enabled") : false;
    public final int HEADLESS_CONCURRENCY = hasKey("headless.concurrency") ? getI("headless.concurrency") : 100;
    public final int HEADLESS_GAMETICK_MS = hasKey("headless.gametick.ms") ? getI("headless.gametick.ms") : 8;
    public final boolean HEADLESS_BUILDORDERS_ENABLED = hasKey("headless.buildorders.enabled") ? getB("headless.buildorders.enabled") : false;
    public final double SHOT_FREEZE_RATIO = hasKey("globals.shot.caststun") ? getD("globals.shot.caststun") : 1.0;
    public final int SHOT_CASTLAG_FRAMES = hasKey("globals.shot.castlag") ? getI("globals.shot.castlag") : 20;
    public final int LOB_CASTLAG_FRAMES = hasKey("globals.lob.castlag") ? getI("globals.lob.castlag") : 15;
    public boolean GOALIE_DISABLED = getB("globals.goalie.disabled");
    public boolean AI_OMNISCIENCE_ENABLED = hasKey("globals.ai.omniscience.enabled") ? getB("globals.ai.omniscience.enabled") : false;
    public final double AI_GOALIE_REACTION_RATIO = hasKey("globals.ai.goalie.reaction.ratio") ? getD("globals.ai.goalie.reaction.ratio") : 0.70;
    public final int AI_DIFFICULTY = hasKey("globals.ai.difficulty") ? getI("globals.ai.difficulty") : 4;
    public final gameserver.entity.TitanType[] AI_INCLUDED_TITANS = initAiIncludedTitans();
    public final double BALL_X = getD("ball.x");
    public final double BALL_Y = getD("ball.y");
    public final int GOALIE_Y_MIN = getI("goalie.box.y");
    public final int GOALIE_Y_MAX = GOALIE_Y_MIN + getI("goalie.box.h");
    public final int GOALIE_XH_MIN = getI("goalie.box.xh");
    public final int GOALIE_XH_MAX = GOALIE_XH_MIN + getI("goalie.box.w");
    public final int GOALIE_XA_MIN = getI("goalie.box.xa");
    public final int GOALIE_XA_MAX = GOALIE_XA_MIN + getI("goalie.box.w");
    public final int GOALIE_INTERCEPT_W = getI("goalie.hitbox.intercept.w");
    public final int GOALIE_INTERCEPT_H = getI("goalie.hitbox.intercept.h");
    public final int GOALIE_SOLID_W = getI("goalie.hitbox.solid.w");
    public final int GOALIE_SOLID_H = getI("goalie.hitbox.solid.h");
    public int MAX_X = getI("max.x");
    public int E_MAX_X = getI("max.ex");
    public int MAX_Y = getI("max.y");
    public int E_MAX_Y = getI("max.ey");
    public int MIN_X = getI("min.x");
    public int E_MIN_X = getI("min.ex");
    public int MIN_Y = getI("min.y");
    public int E_MIN_Y = getI("min.ey");
    public final int DEFENSIVE_THIRD_X = 680;
    public final int ATTACKING_THIRD_X = 1368;
    public final int HOOP_SIDEGOAL_CD_MS = hasKey("hoop.sidegoal.cdms") ? getI("hoop.sidegoal.cdms") : 1800;
    public final int HOOP_BOUNCE_EXTRA_KICK = hasKey("hoop.bounce.extra.kick") ? getI("hoop.bounce.extra.kick") : 30;
    public final double GUARDIAN_BIGGERMODELS_SCALE = hasKey("guardian.biggermodels.scale") ? getD("guardian.biggermodels.scale") : 1.25;
    public final double GUARDIAN_BIGGERMODELS_COMPENSATION = hasKey("guardian.biggermodels.compensation") ? getD("guardian.biggermodels.compensation") : 7.0;
    public final double GUARDIAN_PARAPET_DEFENSE_RATIO = hasKey("guardian.parapet.defense.ratio") ? getD("guardian.parapet.defense.ratio") : 2.50;
    public final double LANE_ADVANTAGE_SOFTCAP = hasKey("guardian.laneadvantage.softcap") ? getD("guardian.laneadvantage.softcap") : 15.0;
    public final double LANE_ADVANTAGE_HARDCAP = hasKey("guardian.laneadvantage.hardcap") ? getD("guardian.laneadvantage.hardcap") : 30.0;
    public final double LANE_ADVANTAGE_UNILATERAL_FACTOR = hasKey("guardian.laneadvantage.unilateral.factor") ? getD("guardian.laneadvantage.unilateral.factor") : 0.0050;
    public final double LANE_ADVANTAGE_DIRECTIONAL_FACTOR = hasKey("guardian.laneadvantage.directional.factor") ? getD("guardian.laneadvantage.directional.factor") : 0.01;
    public final double MAXIMUM_PRESSURE_MULTIPLIER = hasKey("guardian.maximumpressure.multiplier") ? getD("guardian.maximumpressure.multiplier") : 2.0;

    private gameserver.entity.TitanType[] initAiIncludedTitans() {
        if (hasKey("globals.ai.titans.included")) {
            String raw = getS("globals.ai.titans.included");
            if (raw != null && !raw.trim().isEmpty()) {
                String[] tokens = raw.split(",");
                java.util.List<gameserver.entity.TitanType> list = new java.util.ArrayList<>();
                for (String token : tokens) {
                    String clean = token.trim();
                    if (!clean.isEmpty()) {
                        try {
                            gameserver.entity.TitanType type = gameserver.entity.TitanType.valueOf(clean.toUpperCase());
                            list.add(type);
                        } catch (Exception ignored) {}
                    }
                }
                if (!list.isEmpty()) {
                    return list.toArray(new gameserver.entity.TitanType[0]);
                }
            }
        }
        return new gameserver.entity.TitanType[]{
            gameserver.entity.TitanType.WARRIOR, gameserver.entity.TitanType.RANGER, gameserver.entity.TitanType.DASHER, gameserver.entity.TitanType.MARKSMAN,
            gameserver.entity.TitanType.STEALTH, gameserver.entity.TitanType.SUPPORT, gameserver.entity.TitanType.ARTISAN, gameserver.entity.TitanType.GOLEM,
            gameserver.entity.TitanType.BUILDER, gameserver.entity.TitanType.HOUNDMASTER, gameserver.entity.TitanType.CAPTAIN, gameserver.entity.TitanType.SPIDER
        };
    }

    public Const() {//kryo
        super("res/game.cfg");
        loadJsonConfig("res/config.json");
    }

    public Const(String fn) {//kryo
        super(fn);
        loadJsonConfig("res/config.json");
    }
}
