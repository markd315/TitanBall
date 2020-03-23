package gameserver.entity.minions;

import gameserver.effects.effects.FlareEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

public class Fire extends gameserver.entity.Entity implements Collidable {

    private String caster;

    public Fire(Titan caster, int x, int y) {
        super(caster.team);
        this.caster = caster.id.toString();
        this.setX(x);
        this.setY(y);
        this.width = 140;
        this.height = 140;
        this.health = 15;
        this.maxHealth = 15;
        this.solid = false;
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Entity) {
            Entity entity = (Entity) box;
            if (entity.team != this.team) {
                context.effectPool.addUniqueEffect(new FlareEffect(
                        1500, entity, 1.0, 0.2), context);
            }
        }
    }

    public Fire(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}
