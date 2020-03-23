package gameserver.entity.minions;

import gameserver.effects.EffectId;
import gameserver.effects.effects.RatioEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Titan;

public class Trap extends gameserver.entity.Entity implements Collidable {

    private String caster;

    public Trap(Titan caster, int x, int y) {
        super(caster.team);
        this.caster = caster.id.toString();
        this.setX(x);
        this.setY(y);
        this.width = 100;
        this.height = 100;
        this.health = 15;
        this.maxHealth = 15;
        this.solid = false;
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Titan) {
            Titan entity = (Titan) box;
            if (entity.team != this.team) {
                context.effectPool.addUniqueEffect(
                        new RatioEffect(200, entity, EffectId.SLOW, 1.9), context);
            }
        }
    }

    public Trap(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}
