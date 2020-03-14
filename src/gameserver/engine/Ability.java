package gameserver.engine;

import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownE;
import gameserver.effects.effects.DefenseEffect;
import gameserver.effects.effects.EmptyEffect;
import gameserver.effects.effects.HideBallEffect;
import gameserver.entity.Titan;
import gameserver.models.Game;
import gameserver.targeting.ShapePayload;
import gameserver.targeting.core.Selector;

import java.awt.*;
import java.awt.geom.Ellipse2D;

public class Ability {
    public boolean castE(GameEngine context, Titan caster) throws NullPointerException {
        AbilityStrategy strat = new AbilityStrategy(context, caster);
        if (!context.effectPool.hasEffect(caster, EffectId.COOLDOWN_E)) {
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
                    strat.stunByRadius(1500);
                    break;
                case GOLEM:
                    context.effectPool.addUniqueEffect(new CooldownE((int) (caster.cooldownFactor *18000), caster));
                    context.effectPool.addUniqueEffect(
                            new DefenseEffect((int) (caster.durationsFactor*5000), caster, 10));
                    break;
                case STEALTH:
                    context.effectPool.addUniqueEffect(new CooldownE((int) (caster.cooldownFactor *15000), caster));
                    context.effectPool.addUniqueEffect(
                            new EmptyEffect((int) (caster.durationsFactor*2500), caster, EffectId.STEALTHED));
                    break;
                case DASHER:
                    if(caster.possession == 1){
                        context.effectPool.addUniqueEffect(new CooldownE((int) (caster.cooldownFactor *9000), caster));
                        context.effectPool.addUniqueEffect(
                                new HideBallEffect((int) (caster.durationsFactor*500), caster));
                    }
                    break;
                case RANGER:
                    strat.shootArrow();
                    break;
                case WARRIOR:
                    strat.circleSlash();
                    break;
            }
            return injectColliders(context, strat, caster);
        }
        return false;
    }

    public boolean castR(GameEngine context, Titan caster) throws NullPointerException {
        AbilityStrategy strat = new AbilityStrategy(context, caster);
        if (!context.effectPool.hasEffect(caster, EffectId.COOLDOWN_R)) {
            switch (caster.getType()) {
                case DASHER:
                    strat.ignite(5, 3, 0, 0);
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
                    strat.scatter();
                    break;
                case RANGER:
                    strat.kick();
                    break;
                case MAGE:
                    strat.ignite(20, 3, .45, .45);
                    break;
                case WARRIOR:
                    strat.parameterizedFlash(23, 140);
                    break;
                case BUILDER:
                    strat.wall();
                    break;
                case STEALTH:
                    strat.parameterizedFlash(21, 100);
                    break;
            }
            return injectColliders(context, strat, caster);
        }
        return false;
    }

    public boolean castQ(GameEngine context, Titan caster) throws NullPointerException {
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
            Rectangle bounds = sel.latestCollider.getBounds();
            if (shape instanceof Ellipse2D.Double) {
                context.colliders.add(
                        new ShapePayload(new Ellipse2D.Double(bounds.getX(), bounds.getY(),
                                bounds.getWidth(), bounds.getHeight())));
            } else if (shape instanceof Polygon) {
                //this won't work probably
                context.colliders.add(
                        new ShapePayload(sel.latestCollider));
            } else if (shape instanceof Rectangle) {
                context.colliders.add(
                        new ShapePayload(new Rectangle((int) bounds.getX(), (int) bounds.getY(),
                                (int) bounds.getWidth(), (int) bounds.getHeight())));
            }
            context.colliders.get(context.colliders.size() - 1).setColor(caster);
        }
        return true;
    }
}
