package gameserver.entity;


import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;

import java.awt.*;
import java.util.*;

import static util.Util.typesafeNumeric;


public class Titan extends Entity {
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
    }

    public double getThrowPower() {
        return throwPower;
    }

    public void setThrowPower(double throwPower) {
        this.throwPower = throwPower;
    }

    public void setVarsBasedOnType() {
        if(type != null){
            this.maxHealth = titanHealth.get(type);
            this.health = titanHealth.get(type) / 2;
            this.throwPower = titanShoot.get(type);
            this.speed = titanSpeed.get(type);
            this.eCastFrames = titanEFrames.get(type);
            this.rCastFrames = titanRFrames.get(type);
            this.sCastFrames = titanStealFrames.get(type);
            this.stealRad = titanStealRad.get(type);
            if(titanRange.containsKey(this.type)){
                this.rangeIndicators = new ArrayList<>();
                this.rangeIndicators.addAll(titanRange.get(this.type));
            }
            if(type == TitanType.DASHER){
                this.boostFactor = 1.45;
            }else{
                this.boostFactor = 1.33;
            }
        }
    }
    public TitanType getType() {
        return type;
    }

    public void setType(TitanType type) {
        if(!typeAndMasteriesLocked){
            this.type = type;
            this.setVarsBasedOnType();
        }

    }

    public Titan(){
        super();
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

    public double actualSpeed() {
        double inspeed = this.speed;
        if(this.runRight + this.runLeft + this.runDown + this.runUp > 1){
            inspeed *= .707; //sqrt(2)/2
        }
        return this.isBoosting
                ? inspeed * this.boostFactor
                : inspeed;
    }

    public void resurrect(GameEngine context) {
        this.actionState = TitanState.IDLE;
        this.resurrecting = false;
        if (this.team == TeamAffiliation.HOME) {
            this.X = context.homeHiGoal.x + (context.homeHiGoal.w / 2);
            this.Y = context.homeHiGoal.y + (context.homeHiGoal.h / 2);
            while (this.collidesSolid(context, context.allSolids)) {
                this.X -= 35;
                if(this.X < context.E_MIN_X){
                    this.X = context.E_MIN_X;
                    this.Y +=35;
                }
            }
        }
        if (this.team == TeamAffiliation.AWAY) {
            this.X = context.awayHiGoal.x + (context.awayHiGoal.w / 2.0);
            this.Y = context.awayHiGoal.y + (context.awayHiGoal.h / 2.0);
            while (this.collidesSolid(context, context.allSolids)) {
                this.X += 35;
                if(this.X > context.E_MAX_X){
                    this.X = context.E_MAX_X;
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
        titanSpeed.put(TitanType.SUPPORT, 5.5);
        titanSpeed.put(TitanType.WARRIOR, 5.5);
        titanSpeed.put(TitanType.ARTISAN, 5.3);
        titanSpeed.put(TitanType.MARKSMAN, 5.1);
        titanSpeed.put(TitanType.RANGER, 5.0);
        titanSpeed.put(TitanType.STEALTH, 4.85);
        titanSpeed.put(TitanType.MAGE, 4.8);
        titanSpeed.put(TitanType.BUILDER, 4.75);
        titanSpeed.put(TitanType.DASHER, 4.7); //Has a 1.5x as effective boost, so this is pretty solid
        titanSpeed.put(TitanType.GOLEM, 4.5);
        titanSpeed.put(TitanType.GOALIE, 2.75);
        //titanSpeed.put(TitanType.RECON, 7);


        titanHealth.put(TitanType.GOALIE, 200.0);
        titanHealth.put(TitanType.GOLEM, 200.0);
        titanHealth.put(TitanType.WARRIOR, 135.0);
        titanHealth.put(TitanType.RANGER, 120.0);
        titanHealth.put(TitanType.MAGE, 110.0);
        titanHealth.put(TitanType.ARTISAN, 90.0);
        titanHealth.put(TitanType.BUILDER, 90.0);
        titanHealth.put(TitanType.SUPPORT, 85.0);
        titanHealth.put(TitanType.MARKSMAN, 85.0);
        titanHealth.put(TitanType.DASHER, 80.0);
        titanHealth.put(TitanType.STEALTH, 80.0);
        //titanHealth.put(TitanType.RECON, 65.0);


        titanShoot.put(TitanType.GOALIE, 1.5);
        titanShoot.put(TitanType.MARKSMAN, 1.5);
        titanShoot.put(TitanType.GOLEM, 1.45);
        titanShoot.put(TitanType.ARTISAN, 1.15);
        titanShoot.put(TitanType.DASHER, 1.09);
        titanShoot.put(TitanType.STEALTH, 1.09);
        titanShoot.put(TitanType.SUPPORT, 1.02);
        titanShoot.put(TitanType.BUILDER, 1.00);
        titanShoot.put(TitanType.RANGER, 0.84);
        titanShoot.put(TitanType.MAGE, 0.84);
        titanShoot.put(TitanType.WARRIOR, 0.80);
        //titanShoot.put(TitanType.RECON, 0.8);



        //33 Frames == 1 Second
        titanEFrames.put(TitanType.GOALIE, 1);
        titanEFrames.put(TitanType.DASHER, 3); //Cover ball
        titanEFrames.put(TitanType.GOLEM, 3); //steroids
        titanEFrames.put(TitanType.BUILDER, 5);//Wall
        titanEFrames.put(TitanType.RANGER, 5); //Single targets
        titanEFrames.put(TitanType.MARKSMAN, 5);
        titanEFrames.put(TitanType.STEALTH, 8); //stealth
        titanEFrames.put(TitanType.ARTISAN, 10);//Suck
        titanEFrames.put(TitanType.MAGE, 12);
        titanEFrames.put(TitanType.WARRIOR, 16);
        titanEFrames.put(TitanType.SUPPORT, 16); //Stun (for 1.5s)

        titanRFrames.put(TitanType.GOALIE, 1);
        titanRFrames.put(TitanType.WARRIOR, 1);//blinks
        titanRFrames.put(TitanType.STEALTH, 1);
        titanRFrames.put(TitanType.MARKSMAN, 3);//steroids
        titanRFrames.put(TitanType.MAGE, 5);//Single Targets
        titanRFrames.put(TitanType.DASHER, 5);
        titanRFrames.put(TitanType.SUPPORT, 5);
        titanRFrames.put(TitanType.ARTISAN, 10);//Minions
        titanRFrames.put(TitanType.BUILDER, 10);
        titanRFrames.put(TitanType.GOLEM, 12);//Scatters
        titanRFrames.put(TitanType.RANGER, 15);
        //titanRFrames.put(TitanType.RECON, 1);

        //Most cast lag=support(21) warrior (17) ranger (17) mage (17)
        //2 points is a 40% reduction, 0 points is a 30% increase (in fullcombo)
        //Would take support from 27 frames to 12 frames for fullcombo, a difference of .45 seconds
        //Least cast lag=, slasher (8), marksman (8), stealth (9)
        //If inted, reduces a single-target from 6 to 3 frames (0pts vs max)
        //anything less than 4 frames may have no effect for some points with 30% scaling


        titanStealRad.put(TitanType.ARTISAN, 33);
        titanStealRad.put(TitanType.GOALIE, 40);
        titanStealRad.put(TitanType.MARKSMAN, 24);
        titanStealRad.put(TitanType.GOLEM, 35);
        titanStealRad.put(TitanType.RANGER, 24);
        //titanStealFrames.put(TitanType.RECON, 40);
        titanStealRad.put(TitanType.DASHER, 30);
        titanStealRad.put(TitanType.STEALTH, 26);
        titanStealRad.put(TitanType.SUPPORT, 28);
        titanStealRad.put(TitanType.WARRIOR, 26);
        titanStealRad.put(TitanType.MAGE, 26);
        titanStealRad.put(TitanType.BUILDER, 26);

        titanStealFrames.put(TitanType.ARTISAN, 40);
        titanStealFrames.put(TitanType.GOALIE, 1);
        titanStealFrames.put(TitanType.MARKSMAN, 40);
        titanStealFrames.put(TitanType.GOLEM, 40);
        titanStealFrames.put(TitanType.RANGER, 40);
        //titanStealFrames.put(TitanType.RECON, 40);
        titanStealFrames.put(TitanType.DASHER, 40);
        titanStealFrames.put(TitanType.STEALTH, 40);
        titanStealFrames.put(TitanType.SUPPORT, 40);
        titanStealFrames.put(TitanType.WARRIOR, 40);
        titanStealFrames.put(TitanType.MAGE, 40);
        titanStealFrames.put(TitanType.BUILDER, 40);

        HashSet<RangeCircle> mage= new HashSet<>();
        HashSet<RangeCircle> builder= new HashSet<>();
        HashSet<RangeCircle> support= new HashSet<>();
        HashSet<RangeCircle> ranger= new HashSet<>();
        HashSet<RangeCircle> warrior= new HashSet<>();
        HashSet<RangeCircle> artisan= new HashSet<>();
        mage.add(e(200));
        mage.add(r(250));
        builder.add(e(200));
        builder.add(r(250));
        support.add(e(65));
        support.add(r(250));
        ranger.add(e(250));
        ranger.add(r(60));
        warrior.add(e(100));
        warrior.add(r(140));
        artisan.add(e(140));
        artisan.add(r(200));
        titanRange.put(TitanType.MAGE, mage);
        titanRange.put(TitanType.RANGER, ranger);
        titanRange.put(TitanType.MARKSMAN, Collections.singleton(e(150)));
        titanRange.put(TitanType.DASHER, Collections.singleton(r(250)));
        titanRange.put(TitanType.GOLEM, Collections.singleton(r(90)));
        titanRange.put(TitanType.BUILDER, builder);
        titanRange.put(TitanType.WARRIOR, warrior);
        titanRange.put(TitanType.STEALTH, Collections.singleton(r(100)));
        titanRange.put(TitanType.SUPPORT, support);
        titanRange.put(TitanType.ARTISAN, artisan);

        titanText.put(TitanType.MAGE, "DAMAGE ignite enemies and warp players around the map with portals");
        titanText.put(TitanType.RANGER, "DAMAGE/DEFENSE take attacking enemies down from a distance");
        titanText.put(TitanType.MARKSMAN, "SCORER long-range shooting and passing specialist");
        titanText.put(TitanType.DASHER, "SCORER drives to the hoop with high boost, dashing around enemies");
        titanText.put(TitanType.GOLEM, "SCORER/UTILITY slow-moving but high survivability under duress");
        titanText.put(TitanType.BUILDER, "UTILITY/DEFENSE build field hazards to deter+manipulate enemies");
        titanText.put(TitanType.WARRIOR, "DAMAGE/DEFENSE slash and dash your way through the opposition");
        titanText.put(TitanType.SUPPORT, "HEALING/UTILITY heal allies and stun enemies to create advantages");
        titanText.put(TitanType.ARTISAN, "UTILITY possession specialist. E+R with ball will pass it with spin");
        titanText.put(TitanType.STEALTH, "SCORER vanish briefly and escape to a better strategic position");

        titanEText.put(TitanType.MAGE, "Spawn a portal to carry players with a 5-second cooldown");
        titanEText.put(TitanType.RANGER, "Shoot a damaging arrow at enemies");
        titanEText.put(TitanType.MARKSMAN, "Slow a nearby enemy temporarily");
        titanEText.put(TitanType.DASHER, "Protect the ball from any steal attempts");
        titanEText.put(TitanType.GOLEM, "Block 99% of incoming damage for a few seconds");
        titanEText.put(TitanType.BUILDER, "Build traps that will damage anyone moving thru them");
        titanEText.put(TitanType.WARRIOR, "Powerfully slash nearby enemies for significant damage");
        titanEText.put(TitanType.SUPPORT, "Stun an enemy for a short amount of time");
        titanEText.put(TitanType.ARTISAN, "Suck a nearby ball towards you until it touches any player");
        titanEText.put(TitanType.STEALTH, "Go invisible for a very short time");

        titanRText.put(TitanType.MAGE, "Scald an enemy with powerful fire magic");
        titanRText.put(TitanType.RANGER, "Kick a nearby enemy a short distance away from you");
        titanRText.put(TitanType.MARKSMAN, "Massively boost shot/pass range and power");
        titanRText.put(TitanType.DASHER, "Ignite an enemy with a flare to prevent stealth");
        titanRText.put(TitanType.GOLEM, "Knock all nearby enemies back a moderate distance");
        titanRText.put(TitanType.BUILDER, "Build walls that block balls and players");
        titanRText.put(TitanType.WARRIOR, "Warp a short distance, or until you hit a solid player/object");
        titanRText.put(TitanType.SUPPORT, "Heal an ally, some at first and more over time");
        titanRText.put(TitanType.ARTISAN, "Spawn a portal that can carry a ball (including its momentum)");
        titanRText.put(TitanType.STEALTH, "Blink a very short distance");

    }
    private static RangeCircle e(int x){
        return new RangeCircle(Color.GREEN, x);
    }
    private static RangeCircle r(int x){
        Color purple = new Color(.45f, .0f, .85f);
        return new RangeCircle(purple, x);
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
}