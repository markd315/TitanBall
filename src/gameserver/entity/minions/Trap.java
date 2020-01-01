package gameserver.entity.minions;

import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

public class Trap extends gameserver.entity.Entity implements Collidable {

    private String caster;

    public Trap(Titan caster, int x, int y) {
        super(caster.team);
        this.caster = caster.id.toString();
        this.setX(x);
        this.setY(y);
        this.width = 50;
        this.height = 50;
        this.health = 15;
        this.maxHealth = 15;
        this.solid = false;
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Entity) {
            Entity entity = (Entity) box;
            if (entity.team != this.team) {
                Titan titan = context.titanByID(caster).get();
                //context.effectPool.addCasterUniqueEffect(new EmptyEffect(5000, entity, EffectId.ATTACKED), titan);
                entity.damage(context, 1.25);
            }
        }
    }

    public Trap(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}
