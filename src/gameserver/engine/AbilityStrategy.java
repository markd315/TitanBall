package gameserver.engine;

import gameserver.Const;
import gameserver.effects.effects.Effect;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.targeting.core.Filter;
import gameserver.targeting.core.Limiter;
import gameserver.targeting.core.Selector;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AbilityStrategy {

    public AbilityStrategy() {}

    protected Selector sel;
    protected CollisionMath.Bounds shape;
    protected Set<Entity> appliedTo;
    protected Effect eff;
    protected CollisionMath.Bounds corners;
    protected GameEngine context;
    protected Titan caster;
    public int x, y;
    protected Const c;

    static final Filter friendly = AbilityHelper.friendly;
    static final Filter friendlyIncSelf = AbilityHelper.friendlyIncSelf;
    static final Filter champions = AbilityHelper.champions;
    static final Filter championsNoGoalie = AbilityHelper.championsNoGoalie;
    static final Filter enemiesIncMinions = AbilityHelper.enemiesIncMinions;
    static final Filter all = AbilityHelper.all;
    static final Filter notFriendly = AbilityHelper.notFriendly;

    static final Limiter nearest = AbilityHelper.nearest;
    static final Limiter unlimited = AbilityHelper.unlimited;
    static final Limiter mouseNear = AbilityHelper.mouseNear;

    public AbilityStrategy(GameEngine context, Titan caster) {
        this.context = context;
        this.caster = caster;
        this.c = context != null ? context.c : null;
        int[] coords = AbilityTargetResolver.resolveTargetCoords(context, caster);
        this.x = coords[0];
        this.y = coords[1];
    }

    public void goOnCooldown(Titan caster, String cdKey, char qOrW) {
        AbilityHelper.goOnCooldown(context, caster, cdKey, qOrW);
    }

    // --- Goalie Abilities ---
    public void goalieBlock() {
        TitanAbilitiesSupport.goalieBlock(this);
    }

    public void goalieSlide() {
        TitanAbilitiesMobility.goalieSlide(this);
    }

    public void parameterizedFlash(double cdSeconds, int dist) {
        TitanAbilitiesMobility.parameterizedFlash(this, cdSeconds, dist);
    }

    // --- Combat / Attack Abilities ---
    public void ignite(double cd, double dur, double initialD, double recurringD) {
        TitanAbilitiesCombat.ignite(this, cd, dur, initialD, recurringD);
    }

    public void circleSlash(double dmg, double cdMs) {
        TitanAbilitiesCombat.circleSlash(this, dmg, cdMs);
    }

    public void shootArrow(double dmg) {
        TitanAbilitiesCombat.shootArrow(this, dmg);
    }

    public void chargeShot() {
        TitanAbilitiesCombat.chargeShot(this);
    }

    public void slow() {
        TitanAbilitiesCombat.slow(this);
    }

    public void flashbang(double durMillis) {
        TitanAbilitiesCombat.flashbang(this, durMillis);
    }

    public void molotov() {
        TitanAbilitiesCombat.molotov(this);
    }

    public void stunByRadius(double durMillis) {
        TitanAbilitiesCombat.stunByRadius(this, durMillis);
    }

    // --- Mobility / Displacement Abilities ---
    public void kickSelectedTarget() {
        TitanAbilitiesMobility.kickSelectedTarget(this);
    }

    public void spiderCocoon() {
        TitanAbilitiesMobility.spiderCocoon(this);
    }

    // --- Deployable / Field Object Abilities ---
    public void wall() {
        TitanAbilitiesDeployable.wall(this);
    }

    public void scatter(int rangeIn, int scatterDist, int cdms) {
        TitanAbilitiesDeployable.scatter(this, rangeIn, scatterDist, cdms);
    }

    public void spawnBallPortal() {
        TitanAbilitiesDeployable.spawnBallPortal(this);
    }

    public void spawnPortal() {
        TitanAbilitiesDeployable.spawnPortal(this);
    }

    public void spawnTrap() {
        TitanAbilitiesDeployable.spawnTrap(this);
    }

    public void spawnCage() {
        TitanAbilitiesDeployable.spawnCage(this);
    }

    public void releaseCages() {
        TitanAbilitiesDeployable.releaseCages(this);
    }

    public void spiderWeb() {
        TitanAbilitiesDeployable.spiderWeb(this);
    }

    // --- Support / Ball Manipulation Abilities ---
    public void heal() {
        TitanAbilitiesSupport.heal(this);
    }

    public void suckBall() {
        TitanAbilitiesSupport.suckBall(this);
    }

    public boolean stealBall() {
        return TitanAbilitiesSupport.stealBall(this);
    }

    public void captainShoot() {
        TitanAbilitiesSupport.captainShoot(this);
    }

    public void captainSlideBomb() {
        TitanAbilitiesSupport.captainSlideBomb(this);
    }
}
