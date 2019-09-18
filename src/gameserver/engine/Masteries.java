package gameserver.engine;

import gameserver.entity.Titan;

public class Masteries {

    public Masteries() {
        this.health = 2;
        this.shot = 1;
        this.damage = 1;
        this.cooldowns = 1;
        this.effectDuration = 1;
        this.stealRadius = 1;
        this.abilityRange = 1;
        this.abilityLag = 1;
        this.speed = 1;
    }

    public int health, shot, damage, cooldowns, effectDuration, stealRadius;
    public int abilityRange, abilityLag, speed;

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
            default:
                return "Cast lag";
        }
    }

    public boolean validate() {
        final int MAX_SKILL = 3;
        if (health > MAX_SKILL || health < 0) {
            return false;
        }
        if (stealRadius > MAX_SKILL || stealRadius < 0) {
            return false;
        }
        if (shot > MAX_SKILL || shot < 0) {
            return false;
        }
        if (abilityRange > MAX_SKILL || abilityRange < 0) {
            return false;
        }
        if (abilityLag > MAX_SKILL || abilityLag < 0) {
            return false;
        }
        if (damage > MAX_SKILL || damage < 0) {
            return false;
        }
        if (cooldowns > MAX_SKILL || cooldowns < 0) {
            return false;
        }
        if (effectDuration > MAX_SKILL || effectDuration < 0) {
            return false;
        }
        if (speed > MAX_SKILL || speed < 0) {
            return false;
        }
        return skillsRemaining() >= 0;
    }

    public int skillsRemaining() {
        final int MAX_TOTAL_SKILL = 10;
        return MAX_TOTAL_SKILL - (health + stealRadius + shot + abilityLag + abilityRange + damage + cooldowns + effectDuration + speed);
    }

    public int[] asArray() {
        int[] ret = new int[9];
        ret[0] = this.health;
        ret[1] = this.shot;
        ret[2] = this.damage;
        ret[3] = this.speed;
        ret[4] = this.cooldowns;
        ret[5] = this.effectDuration;
        ret[6] = this.stealRadius;
        ret[7] = this.abilityRange;
        ret[8] = this.abilityLag;

        return ret;
    }

    public void applyMasteries(Titan t) {
        if (!t.typeAndMasteriesLocked && this.validate()) {
            t.maxHealth *= Math.pow(1.1, this.health-1);
            t.damageFactor *= Math.pow(1.1, this.damage-1);
            t.speed *= Math.pow(1.1, this.speed-1);
            t.cooldownFactor /= Math.pow(1.1, this.cooldowns-1);
            t.throwPower *= Math.pow(1.05, this.shot-1);
            t.rangeFactor *= Math.pow(1.07, this.abilityRange-1);
            t.durationsFactor *= Math.pow(1.15, this.effectDuration-1);
            t.stealRad *= Math.pow(1.09, this.stealRadius-1);
            t.eCastFrames /= Math.pow(1.18, this.abilityLag-1);
            t.rCastFrames /= Math.pow(1.18, this.abilityLag-1);
            t.sCastFrames /= Math.pow(1.18, this.abilityLag-1);
            t.typeAndMasteriesLocked = true;
        }
    }
}
