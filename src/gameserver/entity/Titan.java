package gameserver.entity;


import gameserver.engine.TeamAffiliation;

import java.awt.*;
import java.util.List;
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

    private TitanType type;

    public List<RangeCircle> rangeIndicators = new ArrayList<>();

    public Titan(int x, int y, TeamAffiliation team, TitanType type){
        super(team);
        X = x;
        Y = y;
        this.type = type;
        this.width = 70;
        this.height = 70;
        setVarsBasedOnType();
        titanList.add(this);
        this.solid = true;
    }

    public static Titan byId(UUID query) {
        for(Titan t : titanList){
            if(t.id.equals(query)){
                return t;
            }
        }
        return null;
    }

    public double getThrowPower() {
        return throwPower;
    }

    public void setThrowPower(double throwPower) {
        this.throwPower = throwPower;
    }

    private void setVarsBasedOnType() {
        this.maxHealth = titanHealth.get(type);
        this.health = titanHealth.get(type) / 2;
        this.throwPower = titanShoot.get(type);
        this.speed = titanSpeed.get(type);
        this.eCastFrames = titanEFrames.get(type);
        this.rCastFrames = titanRFrames.get(type);
        this.sCastFrames = titanStealFrames.get(type);
        if(titanRange.containsKey(this.type)){
            this.rangeIndicators = new ArrayList<RangeCircle>();
            this.rangeIndicators.addAll(titanRange.get(this.type));
        }
    }
    public TitanType getType() {
        return type;
    }

    public void setType(TitanType type) {
        this.type = type;
        this.setVarsBasedOnType();
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

    static List<Titan> titanList = new ArrayList();
    public static Map<TitanType, Double> titanHealth = new HashMap();
    public static Map<TitanType, Double> titanSpeed = new HashMap();
    public static Map<TitanType, Double> titanShoot = new HashMap();
    static Map<TitanType, Integer> titanEFrames = new HashMap();
    static Map<TitanType, Integer> titanRFrames = new HashMap();
    static Map<TitanType, Integer> titanStealFrames = new HashMap();
    static Map<TitanType, Set<RangeCircle>> titanRange = new HashMap();
    public static Map<TitanType, String> titanText = new HashMap();
    public static Map<TitanType, String> titanEText = new HashMap();
    public static Map<TitanType, String> titanRText = new HashMap();

    public static double normalOutOfTenFromStat(Map<TitanType, ?> stat, TitanType query){
        double mean=0.0, sd=0.0;
        final double MEAN_STARS=5.0, SD_STARS=2.0;
        for(Map.Entry<TitanType, ?> entry : stat.entrySet()){
            if(!entry.getKey().equals(TitanType.GUARDIAN)) {
                mean += typesafeNumeric(entry.getValue());
            }
        }
        mean/=stat.size() -1;//Ignore one element for guardian
        for(Map.Entry<TitanType, ?> entry : stat.entrySet()){
            if(!entry.getKey().equals(TitanType.GUARDIAN)) {
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
    public static Optional<Titan> titanInPossession() {//code smell and potentially broken during shots

        for(int i=0; i <titanList.size(); i++){
            if(titanList.get(i).possession == 1){
                return Optional.of(titanList.get(i));
            }
        }
        return Optional.empty();
    }

    public TitanState actionState  = TitanState.IDLE;
    public enum TitanState{
        PASS, SHOOT, A1, A2, CURVE_LEFT, CURVE_RIGHT, STEAL, IDLE
    }

    public int runRight = 0;
    public int runLeft = 0;
    public int runUp = 0;
    public int runDown = 0;

    static{
        titanSpeed.put(TitanType.ARTISAN, 6.0);
        titanSpeed.put(TitanType.GUARDIAN, 5.0);
        titanSpeed.put(TitanType.MARKSMAN, 5.2);
        titanSpeed.put(TitanType.POST, 4.2);
        titanSpeed.put(TitanType.RANGER, 5.0);
        //titanSpeed.put(TitanType.RECON, 7);
        titanSpeed.put(TitanType.SLASHER, 4.5);
        titanSpeed.put(TitanType.STEALTH, 4.8);
        titanSpeed.put(TitanType.SUPPORT, 5.7);
        titanSpeed.put(TitanType.WARRIOR, 5.7);
        titanSpeed.put(TitanType.MAGE, 4.7);
        titanSpeed.put(TitanType.BUILDER, 4.7);

        titanHealth.put(TitanType.ARTISAN, 100.0);
        titanHealth.put(TitanType.GUARDIAN, 800.0);
        titanHealth.put(TitanType.MARKSMAN, 75.0);
        titanHealth.put(TitanType.POST, 180.0);
        titanHealth.put(TitanType.RANGER, 120.0);
        //titanHealth.put(TitanType.RECON, 65.0);
        titanHealth.put(TitanType.SLASHER, 80.0);
        titanHealth.put(TitanType.STEALTH, 80.0);
        titanHealth.put(TitanType.SUPPORT, 85.0);
        titanHealth.put(TitanType.WARRIOR, 150.0);
        titanHealth.put(TitanType.MAGE, 110.0);
        titanHealth.put(TitanType.BUILDER, 90.0);

        titanShoot.put(TitanType.ARTISAN, 1.0);
        titanShoot.put(TitanType.GUARDIAN, 1.1);
        titanShoot.put(TitanType.MARKSMAN, 1.2);
        titanShoot.put(TitanType.POST, 1.2);
        titanShoot.put(TitanType.RANGER, 0.48);
        //titanShoot.put(TitanType.RECON, 0.8);
        titanShoot.put(TitanType.SLASHER, 0.9);
        titanShoot.put(TitanType.STEALTH, 0.9);
        titanShoot.put(TitanType.SUPPORT, 0.7);
        titanShoot.put(TitanType.WARRIOR, 0.48);
        titanShoot.put(TitanType.MAGE, 0.48);
        titanShoot.put(TitanType.BUILDER, 0.7);

        titanEFrames.put(TitanType.ARTISAN, 10);
        titanEFrames.put(TitanType.GUARDIAN, 1);
        titanEFrames.put(TitanType.MARKSMAN, 8);
        titanEFrames.put(TitanType.POST, 6);
        titanEFrames.put(TitanType.RANGER, 10);
        //titanEFrames.put(TitanType.RECON, 1);
        titanEFrames.put(TitanType.SLASHER, 14);
        titanEFrames.put(TitanType.STEALTH, 6);
        titanEFrames.put(TitanType.SUPPORT, 6);
        titanEFrames.put(TitanType.WARRIOR, 1);
        titanEFrames.put(TitanType.MAGE, 8);
        titanEFrames.put(TitanType.BUILDER, 8);

        titanRFrames.put(TitanType.ARTISAN, 10);
        titanRFrames.put(TitanType.GUARDIAN, 1);
        titanRFrames.put(TitanType.MARKSMAN, 6);
        titanRFrames.put(TitanType.POST, 32);
        titanRFrames.put(TitanType.RANGER, 22);
        //titanRFrames.put(TitanType.RECON, 1);
        titanRFrames.put(TitanType.SLASHER, 8);
        titanRFrames.put(TitanType.STEALTH, 1);
        titanRFrames.put(TitanType.SUPPORT, 6);
        titanRFrames.put(TitanType.WARRIOR, 24);
        titanRFrames.put(TitanType.MAGE, 8);
        titanRFrames.put(TitanType.BUILDER, 8);

        titanStealFrames.put(TitanType.ARTISAN, 40);
        titanStealFrames.put(TitanType.GUARDIAN, 40);
        titanStealFrames.put(TitanType.MARKSMAN, 40);
        titanStealFrames.put(TitanType.POST, 40);
        titanStealFrames.put(TitanType.RANGER, 40);
        //titanStealFrames.put(TitanType.RECON, 40);
        titanStealFrames.put(TitanType.SLASHER, 40);
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
        mage.add(e(200));
        mage.add(r(250));
        builder.add(e(200));
        builder.add(r(250));
        support.add(e(50));
        support.add(r(250));
        ranger.add(e(250));
        ranger.add(r(60));
        warrior.add(e(140));
        warrior.add(r(100));
        titanRange.put(TitanType.MAGE, mage);
        titanRange.put(TitanType.RANGER, ranger);
        titanRange.put(TitanType.MARKSMAN, Collections.singleton(e(150)));
        titanRange.put(TitanType.SLASHER, Collections.singleton(r(250)));
        titanRange.put(TitanType.POST, Collections.singleton(r(90)));
        titanRange.put(TitanType.BUILDER, builder);
        titanRange.put(TitanType.WARRIOR, warrior);
        titanRange.put(TitanType.STEALTH, Collections.singleton(r(100)));
        titanRange.put(TitanType.SUPPORT, support);
        titanRange.put(TitanType.ARTISAN, Collections.singleton(e(140)));

        titanText.put(TitanType.MAGE, "DAMAGE ignite enemies and warp players around the map with portals");
        titanText.put(TitanType.RANGER, "DAMAGE/DEFENSE take attacking enemies down from a distance");
        titanText.put(TitanType.MARKSMAN, "SCORER long-range shooting and passing specialist");
        titanText.put(TitanType.SLASHER, "SCORER drives to the hoop, dashing around enemies");
        titanText.put(TitanType.POST, "SCORER/UTILITY slow-moving but high survivability under duress");
        titanText.put(TitanType.BUILDER, "UTILITY/DEFENSE build field hazards to deter+manipulate enemies");
        titanText.put(TitanType.WARRIOR, "DAMAGE/DEFENSE slash and dash your way through the opposition");
        titanText.put(TitanType.SUPPORT, "HEALING/UTILITY heal allies and stun enemies to create advantages");
        titanText.put(TitanType.ARTISAN, "UTILITY possession specialist. E+R with ball will pass it with spin");
        titanText.put(TitanType.STEALTH, "SCORER vanish briefly and escape to a better strategic position");

        titanEText.put(TitanType.MAGE, "Spawn a portal to carry players with a 5-second cooldown");
        titanEText.put(TitanType.RANGER, "Shoot a damaging arrow at enemies");
        titanEText.put(TitanType.MARKSMAN, "Slow a nearby enemy temporarily");
        titanEText.put(TitanType.SLASHER, "Significant, short-term speed boost");
        titanEText.put(TitanType.POST, "Block 99% of incoming damage for a few seconds");
        titanEText.put(TitanType.BUILDER, "Build traps that will damage anyone moving thru them");
        titanEText.put(TitanType.WARRIOR, "Warp a short distance, or until you hit a solid player/object");
        titanEText.put(TitanType.SUPPORT, "Stun an enemy for a short amount of time");
        titanEText.put(TitanType.ARTISAN, "Suck a nearby ball towards you until it touches any player");
        titanEText.put(TitanType.STEALTH, "Go invisible for a very short time");

        titanRText.put(TitanType.MAGE, "Scald an enemy with powerful fire magic");
        titanRText.put(TitanType.RANGER, "Kick a nearby enemy a short distance away from you");
        titanRText.put(TitanType.MARKSMAN, "Massively boost shot/pass range and power");
        titanRText.put(TitanType.SLASHER, "Ignite an enemy with a flare to prevent stealth");
        titanRText.put(TitanType.POST, "Knock all nearby enemies back a moderate distance");
        titanRText.put(TitanType.BUILDER, "Build walls that block balls and players");
        titanRText.put(TitanType.WARRIOR, "Powerfully slash nearby enemies for significant damage");
        titanRText.put(TitanType.SUPPORT, "Heal an ally, some at first and more over time");
        titanRText.put(TitanType.ARTISAN, "Spawn a portal that can carry a ball (including its momentum)");
        titanRText.put(TitanType.STEALTH, "Blink a very short distance");

    }

    public static List<Entity> getTitanList(){
        List<Entity> ents = new ArrayList<>();
        for(Titan t: titanList){
            ents.add((Entity) t);
        }
        return ents;
    }
    private static RangeCircle e(int x){
        return new RangeCircle(Color.GREEN, x);
    }
    private static RangeCircle r(int x){
        Color purple = new Color(.45f, .0f, .85f);
        return new RangeCircle(purple, x);
    }
}