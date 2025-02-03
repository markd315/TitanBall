package gameserver.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import gameserver.Const;
import gameserver.entity.Titan;
import util.ConstOperations;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Masteries  implements Serializable {

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
    }

    public Masteries(Map<String, Integer> json){
        this.health = json.get("health");
        this.shot = json.get("shot");
        this.damage = json.get("damage");
        this.cooldowns = json.get("cooldowns");
        this.effectDuration = json.get("effectDuration");
        this.stealRadius = json.get("stealRadius");
        this.abilityRange = json.get("abilityRange");
        this.abilityLag = json.get("abilityLag");
        this.speed = json.get("speed");
        this.painReduction = json.get("painReduction");

    }

    @JsonProperty
    public int health, shot, damage, cooldowns, effectDuration, stealRadius;
    @JsonProperty
    public int abilityRange, abilityLag, speed, painReduction;

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
            default:
                return "Pain Reduction";
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
        return skill_remaining;
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
        return ret;
    }

    public int[] asArray() {
        int[] ret = new int[10];
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

        return ret;
    }

    public void applyMasteries(Titan t) {
        if (!t.typeAndMasteriesLocked && this.validate() != -1) {
            System.out.println("Mastery adjusted stats for " + t.getType().toString());
            ConstOperations c = new Const("res/game.cfg");
            t.speed *= Math.pow(c.getD("masteries.speed.mult"), this.speed-1);
            t.throwPower *= Math.pow(c.getD("masteries.throw.mult"), this.shot-1);
            t.rangeFactor *= Math.pow(c.getD("masteries.range.mult"), this.abilityRange-1);
            t.stealRad *= Math.pow(c.getD("masteries.stealRadius.mult"), this.stealRadius-1);
            t.maxHealth *= Math.pow(c.getD("masteries.health.mult"), this.health-1);
            t.damageFactor *= Math.pow(c.getD("masteries.damage.mult"), this.damage-1);
            t.cooldownFactor /= Math.pow(c.getD("masteries.cooldowns.mult"), this.cooldowns-1);
            t.durationsFactor *= Math.pow(c.getD("masteries.effectDuration.mult"), this.effectDuration-1);
            t.eCastFrames /= Math.pow(c.getD("masteries.eCastFrames.mult"), this.abilityLag-1);
            t.rCastFrames /= Math.pow(c.getD("masteries.rCastFrames.mult"), this.abilityLag-1);
            t.sCastFrames /= Math.pow(c.getD("masteries.stealCastFrames.mult"), this.abilityLag-1);
            t.painReduction *= Math.pow(c.getD("masteries.painReduction.mult"), this.painReduction-1);
            System.out.println("speed, throw, range, steal, health, damage, cooldown, duration, eCast, rCast, sCast");
            System.out.println("[" + t.speed + "," + t.throwPower + "," + t.rangeFactor + "," + t.stealRad + "," + t.maxHealth + "," + t.damageFactor + "," + t.cooldownFactor + "," + t.durationsFactor + "," + t.eCastFrames + "," + t.rCastFrames + "," + t.sCastFrames + "]");
            t.typeAndMasteriesLocked = true;
        }
    }
}
