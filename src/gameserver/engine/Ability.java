package gameserver.engine;

import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.effects.DefenseEffect;
import gameserver.effects.effects.EmptyEffect;
import gameserver.effects.effects.HideBallEffect;
import gameserver.entity.Titan;
import gameserver.models.Game;
import gameserver.targeting.ShapePayload;
import gameserver.targeting.core.Selector;
import javafx.geometry.Bounds;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

import java.io.Serializable;

public class Ability implements Serializable {
    public boolean castQ(GameEngine context, Titan caster) throws NullPointerException {
        AbilityStrategy strat = new AbilityStrategy(context, caster);
        Const c = strat.c;
        if (!context.effectPool.hasEffect(caster, EffectId.COOLDOWN_Q)) {
            switch (caster.getType()) {
                case MAGE:
                    strat.spawnPortal();
                    break;
                case BUILDER:
                    strat.spawnTrap();
                    break;
                case MARKSMAN:
                    strat.slow();
                    break;
                case ARTISAN:
                    if(caster.possession == 0){
                        strat.suckBall();
                    }
                    break;
                case SUPPORT:
                    strat.stunByRadius(c.getI("titan.stun.dur"));
                    break;
                case GOLEM:
                    context.effectPool.addUniqueEffect(new CooldownQ((int) (caster.cooldownFactor *c.getI("titan.shield.cdms")), caster), context);
                    context.effectPool.addUniqueEffect(
                            new DefenseEffect((int) (caster.durationsFactor*c.getI("titan.shield.dur")),
                                    caster, caster.durationsFactor*c.getI("titan.shield.ratio")), context);
                    break;
                case STEALTH:
                    context.effectPool.addUniqueEffect(new CooldownQ((int) (caster.cooldownFactor *c.getI("titan.stealth.cdms")), caster), context);
                    context.effectPool.addUniqueEffect(
                            new EmptyEffect((int) (caster.durationsFactor*c.getI("titan.stealth.dur")), caster, EffectId.STEALTHED), context);
                    break;
                case DASHER:
                    if(caster.possession == 1){
                        context.effectPool.addUniqueEffect(new CooldownQ((int) (caster.cooldownFactor *c.getI("titan.hide.cdms")), caster), context);
                        context.effectPool.addUniqueEffect(
                                new HideBallEffect((int) (caster.durationsFactor*c.getI("titan.hide.dur")), caster), context);
                    }
                    break;
                case RANGER:
                    strat.shootArrow(c.getI("titan.arrow.dmg"));
                    //4.5 DPS
                    break;
                case WARRIOR:
                    //6.0 DPS
                    strat.circleSlash(c.getI("titan.slash.dmg"), c.getI("titan.slash.cdms"));
                    break;
                case HOUNDMASTER:
                    strat.spawnCage();
                    break;
                case GRENADIER:
                    strat.flashbang(c.getI("titan.flashbang.dur"));
                    break;
            }
            return injectColliders(context, strat, caster);
        }
        return false;
    }

    public boolean castW(GameEngine context, Titan caster) throws NullPointerException {
        AbilityStrategy strat = new AbilityStrategy(context, caster);
        if (!context.effectPool.hasEffect(caster, EffectId.COOLDOWN_W)) {
            switch (caster.getType()) {
                case DASHER:
                    strat.ignite(context.c.getD("titan.flare.cds"),
                            context.c.getD("titan.flare.dur"),
                            context.c.getD("titan.flare.initd"),
                            context.c.getD("titan.flare.recurd"));
                    break;
                case MARKSMAN:
                    strat.chargeShot();
                    break;
                case SUPPORT:
                    strat.heal();
                    break;
                case ARTISAN:
                    strat.spawnBallPortal();
                    break;
                case GOLEM:
                    strat.scatter(context.c.getI("titan.scatter.range"),
                            context.c.getI("titan.scatter.dist"),
                            context.c.getI("titan.scatter.cdms"));
                    break;
                case RANGER:
                    strat.scatter(context.c.getI("titan.kick.range"),
                            context.c.getI("titan.kick.dist"),
                            context.c.getI("titan.kick.cdms"));
                    break;
                case MAGE:
                    strat.ignite(context.c.getD("titan.ignite.cds"),
                            context.c.getD("titan.ignite.dur"),
                            context.c.getD("titan.ignite.initd"),
                            context.c.getD("titan.ignite.recurd"));
                    //41 ticks per second
                    //16.4 tick DPS + 5 initial
                    //37.8 TD every 20 seconds
                    //1.89 DPS
                    break;
                case WARRIOR:
                    strat.parameterizedFlash(context.c.getI("titan.flash.warrior.cds"),
                            context.c.getI("titan.flash.warrior.dist"));
                    break;
                case BUILDER:
                    strat.wall();
                    break;
                case STEALTH:
                    strat.parameterizedFlash(context.c.getI("titan.flash.stealth.cds"),
                            context.c.getI("titan.flash.stealth.dist"));
                    break;
                case HOUNDMASTER:
                    strat.releaseCages();
                    break;
                case GRENADIER:
                    strat.molotov();
                    break;
            }
            return injectColliders(context, strat, caster);
        }
        return false;
    }

    public boolean castSteal(GameEngine context, Titan caster) throws NullPointerException {
        AbilityStrategy strat = new AbilityStrategy(context, caster);
        boolean ret = strat.stealBall();
        injectColliders(context, strat, caster);
        return ret;
    }

    private boolean injectColliders(Game context, AbilityStrategy strat, Titan caster) {
        context.cullOldColliders();
        Selector sel = strat.sel;
        Shape shape = strat.shape;
        if (sel != null && sel.latestCollider != null) {
            //sel has the bounds, shape has the correct class.
            //so we inject the sel bounds back into the shape class and eventually use the camera to render it
            Shape bounds = sel.latestCollider;
            if (shape instanceof Ellipse) {
                Ellipse e = (Ellipse) shape;
                context.colliders.add(
                        new ShapePayload(new Ellipse(e.getCenterX(), e.getCenterY(),
                                e.getRadiusX(), e.getRadiusY())));
            } else if (shape instanceof Polygon) {
                //this won't work probably
                context.colliders.add(
                        new ShapePayload(sel.latestCollider));
            } else if (shape instanceof Rectangle) {
                Bounds b = bounds.getBoundsInLocal();
                context.colliders.add(
                        new ShapePayload(new Rectangle(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight())));
            }
            context.colliders.get(context.colliders.size() - 1).setColor(caster);
        }
        return true;
    }
}
