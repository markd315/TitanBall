package gameserver.entity.minions;

import gameserver.Const;
import gameserver.effects.effects.FlareEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

public class Fire extends gameserver.entity.Entity implements Collidable {

    private String caster;
    private static Const c = new Const("res/game.cfg");

    public Fire(Titan caster, int x, int y) {
        super(caster.team);
        this.caster = caster.id.toString();
        this.setX(x);
        this.setY(y);
        this.width = 140;
        this.height = 140;
        this.health = c.getD("titan.molotov.hp");
        this.maxHealth = health;
        this.solid = false;
    }

    @Override
    public void triggerCollide(GameEngine context, Box box) {
        if (box instanceof Entity) {
            Entity entity = (Entity) box;
            if (entity.team != this.team) {
                context.effectPool.addUniqueEffect(new FlareEffect(
                        c.getI("titan.molotov.dur"),
                        entity, c.getD("titan.molotov.initd"),
                        c.getD("titan.molotov.recurd")),
                        context);
            }
        }
    }

    public Fire(){
        super(TeamAffiliation.UNAFFILIATED);
    }
}
