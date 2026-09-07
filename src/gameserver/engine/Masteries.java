package gameserver.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import gameserver.Const;
import gameserver.entity.Titan;
import util.ConstOperations;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Masteries   {

    public Masteries(Masteries other) { //copy constructor
        this.health = other.health;
        this.shot = other.shot;
        this.damage = other.damage;
        this.speed = other.speed;
        this.cooldowns = other.cooldowns;
        this.effectDuration = other.effectDuration;
        this.stealRadius = other.stealRadius;
        this.abilityRange = other.abilityRange;
        this.abilityLag = other.abilityLag;
        this.painReduction = other.painReduction;
        this.boost = other.boost;
    }

    public Masteries() {
        this.health = 1;
        this.shot = 1;
        this.damage = 1;
        this.cooldowns = 1;
        this.effectDuration = 1;
        this.stealRadius = 1;
        this.abilityRange = 1;
        this.abilityLag = 1;
        this.speed = 1;
        this.painReduction = 1;
        this.boost = 0;
    }

    public Masteries(Map<String, Integer> json){
        this.health = json.getOrDefault("health", 1);
        this.shot = json.getOrDefault("shot", 1);
        this.damage = json.getOrDefault("damage", 1);
        this.cooldowns = json.getOrDefault("cooldowns", 1);
        this.effectDuration = json.getOrDefault("effectDuration", 1);
        this.stealRadius = json.getOrDefault("stealRadius", 1);
        this.abilityRange = json.getOrDefault("abilityRange", 1);
        this.abilityLag = json.getOrDefault("abilityLag", 1);
        this.speed = json.getOrDefault("speed", 1);
        this.painReduction = json.getOrDefault("painReduction", 1);
        this.boost = json.getOrDefault("boost", 0);
    }

    @JsonProperty
    public int health, shot, damage, cooldowns, effectDuration, stealRadius;
    @JsonProperty
    public int abilityRange, abilityLag, speed, painReduction, boost;

    public static String masteryFromIndex(int idx) {
        switch (idx) {
            case 0:
                return "Health";
            case 1:
                return "Shooting";
            case 2:
                return "Damage";
            case 3:
                return "Speed";
            case 4:
                return "Cooldown Reduction";
            case 5:
                return "Effect Duration";
            case 6:
                return "Steal Range";
            case 7:
                return "Ability Range";
            case 8:
                return "Cast lag";
            case 9:
                return "Pain Reduction";
            default:
                return "Boost";
        }
    }

    /**
     * Returns -1 if invalid, otherwise returns the number of skills remaining
     * @return int
     */
    public int validate() {
        final int MAX_SKILL = 3;
        int skill_remaining = 10;
        for (int x: asArray()){
            skill_remaining -= x;
            if (x > MAX_SKILL || x < 0) {
                return -1;
            }
        }
        if (skill_remaining != 0) {
            return -1;
        }
        return 0;
    }


    public Map<String, Integer> asMap(){
        HashMap ret = new HashMap();
        ret.put("health", this.health);
        ret.put("shot", this.shot);
        ret.put("damage", this.damage);
        ret.put("speed", this.speed);
        ret.put("cooldowns", this.cooldowns);
        ret.put("effectDuration", this.effectDuration);
        ret.put("stealRadius", this.stealRadius);
        ret.put("abilityRange", this.abilityRange);
        ret.put("abilityLag", this.abilityLag);
        ret.put("painReduction", this.painReduction);
        ret.put("boost", this.boost);
        return ret;
    }

    public int[] asArray() {
        int[] ret = new int[11];
        ret[0] = this.health;
        ret[1] = this.shot;
        ret[2] = this.damage;
        ret[3] = this.speed;
        ret[4] = this.cooldowns;
        ret[5] = this.effectDuration;
        ret[6] = this.stealRadius;
        ret[7] = this.abilityRange;
        ret[8] = this.abilityLag;
        ret[9] = this.painReduction;
        ret[10] = this.boost;

        return ret;
    }

    public void zeroAll() {
        this.health = 0;
        this.shot = 0;
        this.damage = 0;
        this.speed = 0;
        this.cooldowns = 0;
        this.effectDuration = 0;
        this.stealRadius = 0;
        this.abilityRange = 0;
        this.abilityLag = 0;
        this.painReduction = 0;
        this.boost = 0;
    }

    public void setByIndex(int idx, int val) {
        switch (idx) {
            case 0: this.health = val; break;
            case 1: this.shot = val; break;
            case 2: this.damage = val; break;
            case 3: this.speed = val; break;
            case 4: this.cooldowns = val; break;
            case 5: this.effectDuration = val; break;
            case 6: this.stealRadius = val; break;
            case 7: this.abilityRange = val; break;
            case 8: this.abilityLag = val; break;
            case 9: this.painReduction = val; break;
            case 10: this.boost = val; break;
        }
    }

    public static Masteries createGoalieMasteries(Random rng) {
        Masteries m = new Masteries();
        m.zeroAll();
        m.speed = 3;
        m.boost = 3;
        m.health = 3;

        // Remaining 8 stats: shot (1), damage (2), cooldowns (4), effectDuration (5),
        // stealRadius (6), abilityRange (7), abilityLag (8), painReduction (9)
        int[] remaining = new int[] {1, 2, 4, 5, 6, 7, 8, 9};
        int pick = remaining[rng.nextInt(remaining.length)];
        m.setByIndex(pick, 1);
        return m;
    }

    public static Masteries createRandom3331(Random rng) {
        Masteries m = new Masteries();
        m.zeroAll();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, rng);
        // 3 stats get 3 points
        for (int i = 0; i < 3; i++) {
            m.setByIndex(indices.get(i), 3);
        }
        // 1 stat gets 1 point
        m.setByIndex(indices.get(3), 1);
        // Remaining 7 stats are 0
        return m;
    }

    public List<String> getPlusThreeMasteryNames() {
        List<String> list = new ArrayList<>();
        if (this.health == 3) list.add("HEALTH");
        if (this.shot == 3) list.add("SHOT");
        if (this.damage == 3) list.add("DAMAGE");
        if (this.speed == 3) list.add("SPEED");
        if (this.cooldowns == 3) list.add("COOLDOWNS");
        if (this.effectDuration == 3) list.add("EFFECTDURATION");
        if (this.stealRadius == 3) list.add("STEALRADIUS");
        if (this.abilityRange == 3) list.add("ABILITYRANGE");
        if (this.abilityLag == 3) list.add("ABILITYLAG");
        if (this.painReduction == 3) list.add("PAINREDUCTION");
        if (this.boost == 3) list.add("BOOST");
        return list;
    }

    public void applyMasteries(Titan t) {
        if (t == null || t.getType() == null) {
            return;
        }
        if (t.typeAndMasteriesLocked) {
            return;
        }
        if (this.validate() == -1) {
            return;
        }
        t.masteries = this;
        System.out.println("Mastery adjusted stats for " + t.getType().toString());
        ConstOperations c = new Const("res/game.cfg");
        t.speed *= (1.0 + (this.speed - 1) * (c.getD("masteries.speed.mult") - 1.0));
        t.throwPower *= (1.0 + (this.shot - 1) * (c.getD("masteries.throw.mult") - 1.0));
        t.rangeFactor *= (1.0 + (this.abilityRange - 1) * (c.getD("masteries.range.mult") - 1.0));
        t.stealRad += (this.stealRadius - 1) * c.getI("masteries.stealRadius.flat");
        t.maxHealth *= (1.0 + (this.health - 1) * (c.getD("masteries.health.mult") - 1.0));
        t.damageFactor *= (1.0 + (this.damage - 1) * (c.getD("masteries.damage.mult") - 1.0));
        t.cooldownFactor /= (1.0 + (this.cooldowns - 1) * (c.getD("masteries.cooldowns.mult") - 1.0));
        t.durationsFactor *= (1.0 + (this.effectDuration - 1) * (c.getD("masteries.effectDuration.mult") - 1.0));
        t.eCastFrames /= (1.0 + (this.abilityLag - 1) * (c.getD("masteries.eCastFrames.mult") - 1.0));
        t.rCastFrames /= (1.0 + (this.abilityLag - 1) * (c.getD("masteries.rCastFrames.mult") - 1.0));
        t.sCastFrames /= (1.0 + (this.abilityLag - 1) * (c.getD("masteries.stealCastFrames.mult") - 1.0));
        t.painReduction *= (1.0 + (this.painReduction - 1) * (c.getD("masteries.painReduction.mult") - 1.0));

        double boostMult = 1.0 + (this.boost) * (c.getD("masteries.boost.mult") - 1.0);
        t.boostMaxFactor *= boostMult;
        t.boostRegenFactor *= boostMult;

        t.health = t.maxHealth;
        System.out.println("speed, throw, range, steal, health, damage, cooldown, duration, eCast, rCast, sCast, boost");
        System.out.println("[" + t.speed + "," + t.throwPower + "," + t.rangeFactor + "," + t.stealRad + "," + t.maxHealth + "," + t.damageFactor + "," + t.cooldownFactor + "," + t.durationsFactor + "," + t.eCastFrames + "," + t.rCastFrames + "," + t.sCastFrames + "," + boostMult + "]");
        t.baseSpeed = t.speed;
        t.baseThrowPower = t.throwPower;
        t.baseRangeFactor = t.rangeFactor;
        t.baseCooldownFactor = t.cooldownFactor;
        t.baseDurationsFactor = t.durationsFactor;
        t.baseMaxHealth = t.maxHealth;
        t.basePainReduction = t.painReduction;
        t.baseStealRad = t.stealRad;
        t.baseDamageFactor = t.damageFactor;
        t.baseBoostMaxFactor = t.boostMaxFactor;
        t.baseBoostRegenFactor = t.boostRegenFactor;
        t.typeAndMasteriesLocked = true;
    }
}
