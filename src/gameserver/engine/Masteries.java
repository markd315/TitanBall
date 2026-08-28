package gameserver.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import gameserver.Const;
import gameserver.entity.Titan;
import util.ConstOperations;

import com.fasterxml.jackson.annotation.*;
import java.util.HashMap;
import java.util.Map;

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

    public void applyMasteries(Titan t) {
        if (t.getType() == null || t.getType() == gameserver.entity.TitanType.GOALIE) {
            //System.out.println("[DIAG] applyMasteries SKIP: titan id=" + t.id
            //        + " type=" + t.getType() + " locked=" + t.typeAndMasteriesLocked);
            return;
        }
        if (t.typeAndMasteriesLocked) {
            //System.out.println("[DIAG] applyMasteries SKIP (already locked): titan id=" + t.id
            //        + " type=" + t.getType());
            return;
        }
        if (this.validate() == -1) {
            //System.out.println("[DIAG] applyMasteries SKIP (invalid mastery allocation): titan id="
            //        + t.id + " type=" + t.getType() + " masteries=" + this.asMap());
            return;
        }
        if (!t.typeAndMasteriesLocked) {
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
}
