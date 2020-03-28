package gameserver.entity.minions;

import gameserver.effects.EffectId;
import gameserver.effects.effects.EmptyEffect;
import gameserver.effects.effects.RatioEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

public class Trap extends gameserver.entity.Entity implements Collidable {
    public Trap(Titan caster, GameEngine context, int x, int y) {
        super(caster.team);
        this.setX(x);
        this.setY(y);
        this.width = 100;
        this.height = 100;
        this.health = 8;
        this.maxHealth = 8;
        this.solid = false;
        context.effectPool.addUniqueEffect(
                new EmptyEffect(context.c.getI("trap.stealth.dur"), this, EffectId.STEALTHED),
                context);
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Entity) {
            Entity entity = (Entity) box;
            if (entity.team != this.team) {
                if(context.effectPool.hasEffect(this, EffectId.STEALTHED)){
                    context.effectPool.cullAllOn(context, this);
                }
                context.effectPool.addUniqueEffect(
                        new RatioEffect(context.c.getI("trap.dur"), entity, EffectId.SLOW, context.c.getD("trap.ratio")), context);
            }
        }
    }

    public Trap(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}
