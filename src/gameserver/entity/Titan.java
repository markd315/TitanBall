package gameserver.entity;


import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.effects.Effect;
import gameserver.effects.effects.RatioEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;

import util.ConstOperations;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

import static util.Util.typesafeNumeric;


@JsonIgnoreProperties(ignoreUnknown = true)
public class Titan extends Entity   {
    public int sel, possession = 0;

    public double throwPower = 1.0;

    public int facing = 0;

    public int inactiveDir = 0;//Variables to decide the movement of Rating players when not active, unused for cpu?

    public int runningFrame, runningFrameCounter =0; //This is a BAD variable I couldn't find translations for them while refactoring
    public int eCastFrames = 20, rCastFrames = 25, sCastFrames = 25;

    //TODO these client-affecting variables need to be revisited
    public int dirToBall = 0; //Direction of the player relative to the ball
    public int diagonalRunDir = 0; //Used if running away from the ball
    public int kickingFrames;
    public int actionFrame = 0; //For answering how long have we been in a shooting/passing state?

    public double fuel = 50.0;
    public boolean isBoosting = false;

    public boolean programmed = false;
    public int marchingOrderX = 0;
    public int marchingOrderY = 0;
    public int stealRad = 26;
    public boolean typeAndMasteriesLocked = false;
    public double damageFactor = 1.0;
    public double cooldownFactor = 1.0;
    public double durationsFactor = 1.0;
    public double rangeFactor = 1.0;
    public boolean moveMemU, moveMemD, moveMemL, moveMemR;
    public boolean resurrecting = false;
    public double baseSpeed = 5;
    public double baseThrowPower = 1.0;
    public double baseRangeFactor = 1.0;
    public double baseCooldownFactor = 1.0;
    public double baseDurationsFactor = 1.0;
    public double baseMaxHealth = 100.0;
    public double basePainReduction = 1.0;
    public int baseStealRad = 26;
    public double baseDamageFactor = 1.0;

    private TitanType type;

    public ArrayList<RangeCircle> rangeIndicators = new ArrayList<>();
    public double boostFactor = 1.15;

    public Titan(int x, int y, TeamAffiliation team, TitanType type){
        super(team);
        X = x;
        Y = y;
        this.type = type;
        this.width = 70;
        this.height = 70;
        this.solid = true;
        this.setVarsBasedOnType();
    }

    public double getThrowPower() {
        return throwPower;
    }

    public void setThrowPower(double throwPower) {
        this.throwPower = throwPower;
    }

    ConstOperations c = new Const("res/game.cfg");

    public void setVarsBasedOnType() {
        if(type != null){
            this.maxHealth = titanHealth.get(type);
            this.health = 3.0 * titanHealth.get(type) / 4;
            this.throwPower = titanShoot.get(type);
            this.speed = titanSpeed.get(type);
            this.eCastFrames = titanEFrames.get(type);
            this.rCastFrames = titanRFrames.get(type);
            this.sCastFrames = titanStealFrames.get(type);
            this.stealRad = titanStealRad.get(type);
            this.rangeFactor = 1.0;
            this.damageFactor = 1.0;
            this.cooldownFactor = 1.0;
            this.durationsFactor = 1.0;
            this.painReduction = 1.0;

            this.baseMaxHealth = this.maxHealth;
            this.baseThrowPower = this.throwPower;
            this.baseSpeed = this.speed;
            this.baseStealRad = this.stealRad;
            this.baseRangeFactor = this.rangeFactor;
            this.baseDamageFactor = this.damageFactor;
            this.baseCooldownFactor = this.cooldownFactor;
            this.baseDurationsFactor = this.durationsFactor;
            this.basePainReduction = this.painReduction;

            if(titanRange.containsKey(this.type)){
                this.rangeIndicators = new ArrayList<>();
                this.rangeIndicators.addAll(titanRange.get(this.type));
            }
            if(type == TitanType.DASHER){
                this.boostFactor = c.getD("dasher.boost.boostFactor");
            }else{
                this.boostFactor = c.getD("globals.boost.boostFactor");
            }
        }
    }
    @JsonProperty("type")
    public TitanType getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(TitanType type) {
        if (type == null) {
            return;
        }
        if (this.type == type) {
            if (this.maxHealth <= 0 || this.rangeIndicators == null || this.rangeIndicators.isEmpty()) {
                this.setVarsBasedOnType();
            }
            return;
        }
        if (typeAndMasteriesLocked && this.type != null) {
            return;
        }
        TitanType prev = this.type;
        double prevHealth = this.health;
        this.type = type;
        this.setVarsBasedOnType();
        if (prevHealth != -999.0 && prevHealth > 0.0) {
            this.health = prevHealth;
        }
    }

    public Titan(){
        super();
        this.health = -999.0;
    }

    public int getSel() {
        return sel;
    }

    public void setSel(int sel) {
        this.sel = sel;
    }

    public static Map<TitanType, Double> titanHealth = new HashMap();
    public static Map<TitanType, Double> titanSpeed = new HashMap();
    public static Map<TitanType, Double> titanShoot = new HashMap();
    static Map<TitanType, Integer> titanEFrames = new HashMap();
    static Map<TitanType, Integer> titanRFrames = new HashMap();
    static Map<TitanType, Integer> titanStealFrames = new HashMap();
    public static Map<TitanType, Integer> titanStealRad = new HashMap();
    static Map<TitanType, Set<RangeCircle>> titanRange = new HashMap();
    public static Map<TitanType, String> titanText = new HashMap();
    public static Map<TitanType, String> titanEText = new HashMap();
    public static Map<TitanType, String> titanRText = new HashMap();

    public static double normalOutOfTenFromStat(Map<TitanType, ?> stat, TitanType query){
        double mean=0.0, sd=0.0;
        final double MEAN_STARS=5.0, SD_STARS=1.7;
        for(Map.Entry<TitanType, ?> entry : stat.entrySet()){
            if(!entry.getKey().equals(TitanType.GOALIE)) {
                mean += typesafeNumeric(entry.getValue());
            }
        }
        mean/=stat.size() -1;//Ignore one element for guardian
        for(Map.Entry<TitanType, ?> entry : stat.entrySet()){
            if(!entry.getKey().equals(TitanType.GOALIE)) {
                double meandist = mean - typesafeNumeric(entry.getValue());
                sd += Math.pow(meandist, 2);//Step 1: For each data point, find the square of its distance to the mean, sum these
            }
        }//Ignore one element for guardian
        sd /=stat.size() - 1; //Step 2: Divide by the number of data points and sqrt
        sd = Math.sqrt(sd);
        double toConvert = typesafeNumeric(stat.get(query));
        double zScore = (toConvert - mean) / sd;
        return MEAN_STARS + (zScore*SD_STARS);
    }

    public TitanState actionState  = TitanState.IDLE;

    public double actualSpeed(GameEngine context) {
        double inspeed = this.speed;
        boolean hasDilators = context.homeGoaliePurchasedUpgrades.contains("fortress.t5.dilators") ||
                              context.awayGoaliePurchasedUpgrades.contains("fortress.t5.dilators");
        if (hasDilators) {
            inspeed *= context.c.getD("guardian.dilators.speedmult");
        }
        for(Effect eff : context.effectPool.getEffects()){
            if(eff.on.id.equals(this.id)
                && eff.effect.equals(EffectId.SLOW)){
                RatioEffect sl = (RatioEffect) eff;
                inspeed /= sl.getRatio();
            }
            if(eff.on.id.equals(this.id)
                    && eff.effect.equals(EffectId.FAST)){
                RatioEffect sl = (RatioEffect) eff;
                inspeed *= sl.getRatio();
            }
        }

        if(this.runRight + this.runLeft + this.runDown + this.runUp > 1){
            inspeed *= .707; //sqrt(2)/2
        }
        double speedVal = this.isBoosting
                ? inspeed * this.boostFactor
                : inspeed;

        int L = 0;
        int topCY = (int) (context.c.getI("goal.low.y") + context.c.getI("goal.low.height") / 2.0);
        int midCY = (int) (context.c.getI("goal.hi.y") + context.c.getI("goal.hi.height") / 2.0);
        int botCY = (int) (context.c.getI("goal.low2.y") + context.c.getI("goal.low.height") / 2.0);
        double d0 = Math.abs(this.Y - topCY);
        double d1 = Math.abs(this.Y - midCY);
        double d2 = Math.abs(this.Y - botCY);
        if (d1 < d0 && d1 < d2) L = 1;
        else if (d2 < d0 && d2 < d1) L = 2;

        return context.getLaneMinionSpeed(L, this.team, speedVal);
    }

    public void resurrect(GameEngine context) {
        this.actionState = TitanState.IDLE;
        this.resurrecting = false;
        if (this.team == TeamAffiliation.HOME) {
            this.X = context.homeHiGoal.x + (context.homeHiGoal.w / 2);
            this.Y = context.homeHiGoal.y + (context.homeHiGoal.h / 2);
            while (this.collidesSolid(context, context.allSolids)) {
                this.X -= 35;
                if(this.X < context.c.E_MIN_X){
                    this.X = context.c.E_MIN_X;
                    this.Y +=35;
                }
            }
        }
        if (this.team == TeamAffiliation.AWAY) {
            this.X = context.awayHiGoal.x + (context.awayHiGoal.w / 2.0);
            this.Y = context.awayHiGoal.y + (context.awayHiGoal.h / 2.0);
            while (this.collidesSolid(context, context.allSolids)) {
                this.X += 35;
                if(this.X > context.c.E_MAX_X){
                    this.X = context.c.E_MAX_X;
                    this.Y +=35;
                }
            }
        }
        this.health = this.maxHealth;
    }

    public enum TitanState{
        LOB, SHOOT, A1, A2, CURVE_LEFT, CURVE_RIGHT, STEAL, IDLE, DEAD
    }

    public int runRight = 0;
    public int runLeft = 0;
    public int runUp = 0;
    public int runDown = 0;

    static{
        Const c = new Const("res/game.cfg");
        for (TitanType t : TitanType.values()) {
            String prefix = "titan." + t.name().toLowerCase() + ".";
            if (c.hasKey(prefix + "speed")) titanSpeed.put(t, c.getD(prefix + "speed"));
            if (c.hasKey(prefix + "health")) titanHealth.put(t, c.getD(prefix + "health"));
            if (c.hasKey(prefix + "shoot")) titanShoot.put(t, c.getD(prefix + "shoot"));
            if (c.hasKey(prefix + "eframes")) titanEFrames.put(t, c.getI(prefix + "eframes"));
            if (c.hasKey(prefix + "rframes")) titanRFrames.put(t, c.getI(prefix + "rframes"));
            if (c.hasKey(prefix + "stealrad")) titanStealRad.put(t, c.getI(prefix + "stealrad"));
            if (c.hasKey(prefix + "stealframes")) titanStealFrames.put(t, c.getI(prefix + "stealframes"));
        }

        HashSet<RangeCircle> mage= new HashSet<>();
        HashSet<RangeCircle> builder= new HashSet<>();
        HashSet<RangeCircle> support= new HashSet<>();
        HashSet<RangeCircle> ranger= new HashSet<>();
        HashSet<RangeCircle> warrior= new HashSet<>();
        HashSet<RangeCircle> artisan= new HashSet<>();
        HashSet<RangeCircle> grenadier= new HashSet<>();
        mage.add(e(c.getI("titan.portal.range")));
        mage.add(r(c.getI("titan.ignite.range")));
        builder.add(e(c.getI("titan.trap.range")));
        builder.add(r(c.getI("titan.wall.range")));
        support.add(e(c.getI("titan.stun.range") / 2));
        support.add(r(c.getI("titan.heal.range")));
        ranger.add(e(c.getI("titan.arrow.range")));
        ranger.add(r(c.getI("titan.kick.range")/2));
        warrior.add(e(c.getI("titan.slash.range")/2));
        warrior.add(r(c.getI("titan.flash.warrior.dist")));
        artisan.add(e(c.getI("titan.suck.range") /2));
        artisan.add(r(c.getI("titan.bportal.range")));
        grenadier.add(e(c.getI("titan.flashbang.range") /2));
        grenadier.add(r(c.getI("titan.molotov.range")));
        titanRange.put(TitanType.MAGE, mage);
        titanRange.put(TitanType.RANGER, ranger);
        titanRange.put(TitanType.MARKSMAN, Collections.singleton(e(c.getI("titan.ice.range"))));
        titanRange.put(TitanType.DASHER, Collections.singleton(r(c.getI("titan.ignite.range"))));
        titanRange.put(TitanType.GOLEM, Collections.singleton(r(c.getI("titan.scatter.range")/2)));
        titanRange.put(TitanType.BUILDER, builder);
        titanRange.put(TitanType.WARRIOR, warrior);
        titanRange.put(TitanType.STEALTH, Collections.singleton(r(c.getI("titan.flash.stealth.dist"))));
        titanRange.put(TitanType.SUPPORT, support);
        titanRange.put(TitanType.ARTISAN, artisan);
        titanRange.put(TitanType.HOUNDMASTER,  Collections.singleton(e(c.getI("titan.cage.range"))));
        titanRange.put(TitanType.GRENADIER, grenadier);
        titanRange.put(TitanType.GOALIE, Collections.singleton(e(c.getI("titan.goalie.rangex"), c.getI("titan.goalie.rangey"))));

        titanText.put(TitanType.MAGE, "DAMAGE ignite enemies and warp players around the map with portals");
        titanText.put(TitanType.RANGER, "DAMAGE/DEFENSE take attacking enemies down from a distance");
        titanText.put(TitanType.MARKSMAN, "SCORER long-range shooting and passing specialist");
        titanText.put(TitanType.DASHER, "SCORER boost is more effective, and permitted with the ball");
        titanText.put(TitanType.GOLEM, "SCORER/UTILITY slow-moving but high survivability under duress");
        titanText.put(TitanType.BUILDER, "UTILITY/DEFENSE build field hazards to deter+manipulate enemies");
        titanText.put(TitanType.WARRIOR, "DAMAGE/DEFENSE slash and dash your way through the opposition");
        titanText.put(TitanType.SUPPORT, "HEALING/UTILITY heal allies and stun enemies to create advantages");
        titanText.put(TitanType.ARTISAN, "UTILITY ball-portals, ball magnet and spin shots");
        titanText.put(TitanType.STEALTH, "SCORER vanish briefly and escape to a better strategic position");
        titanText.put(TitanType.GRENADIER, "UTILITY manipulate the battlefield with grenades");
        titanText.put(TitanType.HOUNDMASTER, "DAMAGE/DEFENSE swarm enemies with fragile, biting dogs");
        titanText.put(TitanType.GOALIE, "DEFENSE protect goals and direct RTS lanes");

        titanEText.put(TitanType.MAGE, "Spawn a portal to carry friendly players long distances");
        titanEText.put(TitanType.RANGER, "Shoot a damaging arrow at enemies");
        titanEText.put(TitanType.MARKSMAN, "Slow a nearby enemy temporarily");
        titanEText.put(TitanType.DASHER, "Protect the ball from any steal attempts");
        titanEText.put(TitanType.GOLEM, "Reduce incoming damage for a few seconds");
        titanEText.put(TitanType.BUILDER, "Build traps that will damage anyone moving thru them");
        titanEText.put(TitanType.WARRIOR, "Powerfully slash nearby enemies for significant damage");
        titanEText.put(TitanType.SUPPORT, "Stun an enemy for a short amount of time");
        titanEText.put(TitanType.ARTISAN, "Suck the ball towards you / toggle spin shooting modes");
        titanEText.put(TitanType.STEALTH, "Go invisible for a short time. Avoid fire!");
        titanEText.put(TitanType.GRENADIER, "Activate a flashbang blinding nearby enemies");
        titanEText.put(TitanType.HOUNDMASTER, "Spawn a cage with a hound");
        titanEText.put(TitanType.GOALIE, "Goalie Ability E");

        titanRText.put(TitanType.MAGE, "Scald an enemy with powerful fire magic");
        titanRText.put(TitanType.RANGER, "Knock all nearby enemies back a short distance");
        titanRText.put(TitanType.MARKSMAN, "Massively boost shot/pass range and power");
        titanRText.put(TitanType.DASHER, "Ignite an enemy with a flare to prevent stealth");
        titanRText.put(TitanType.GOLEM, "Knock all nearby enemies back a long distance");
        titanRText.put(TitanType.BUILDER, "Build walls that block balls and players");
        titanRText.put(TitanType.WARRIOR, "Warp a short distance, or until you hit a solid player/object");
        titanRText.put(TitanType.SUPPORT, "Heal an ally, some at first and more over time");
        titanRText.put(TitanType.ARTISAN, "Spawn a portal that can carry a ball (including its momentum)");
        titanRText.put(TitanType.STEALTH, "Blink a very short distance");
        titanRText.put(TitanType.GRENADIER, "Deal damage and deny a large region with vicious fire");
        titanRText.put(TitanType.HOUNDMASTER, "Open all cages. More dogs means more damage");
        titanRText.put(TitanType.GOALIE, "Goalie Ability R");

    }
    private static RangeCircle e(int x){
        return new RangeCircle(0.0, 1.0, 0.0, 1.0, x);
    }
    private static RangeCircle e(int x, int y){
        return new RangeCircle(0.0, 1.0, 0.0, 1.0, x, y);
    }
    public RangeCircle getEnemyRadius(int x){
        return new RangeCircle(0.5, 0.0, 0.5, 1.0, x);
    }
    private static RangeCircle r(int x){
        return new RangeCircle(0.5, 0.0, 0.5, 1.0, x);
    }

    public void pushMove() {
        copyMove();
        this.runUp = 0;
        this.runDown = 0;
        this.runLeft = 0;
        this.runRight = 0;
    }

    public void copyMove() {
        this.moveMemU = this.runUp == 1;
        this.moveMemD = this.runDown == 1;
        this.moveMemL = this.runLeft == 1;
        this.moveMemR = this.runRight == 1;
    }

    public void popMove() {
        this.runUp = this.moveMemU ? 1 : 0;
        this.runDown = this.moveMemD ? 1 : 0;
        this.runLeft = this.moveMemL ? 1 : 0;
        this.runRight = this.moveMemR ? 1 : 0;
    }
    /*

    TODO FIRST:
     Fix reset logic after a goal:
     all entities despawned
     All health and boosts reset to 75%
     All cooldowns reset to 0
     All effects ended

     Fix reset logic at end of game:
     stop contacting players after the one final update

    More character designs:

    Joker scorer:
    When he blinks, he spawns a fake titan at his location
    Randomly teleports to a random location

    Turtle support :
    Fires a shell that can bounce off walls, stunning anyone in its path
    lives for a short time (10 seconds?)
    Defensive ability to cast on allies?

    Lanternfish support:
    Hooks enemies with his tail (projectile) and pulls them towards him
    Can extend the lantern to pull allies towards him

    Snake assault:
    Leaves a trail of poison that can slow enemies
    Can bite enemies and jump to them

    Spider support:
    Can place webs that slow enemies and allies
    Can teleport within the webs

    Footballer scorer:
    Can tackle enemies (push them away for a short time)
    Forward pass is his second ability
    Can only pass backwards (lateral)

    Skater ?:
    Can place ice fields that force all entities to slip until they exit the field

     */
}