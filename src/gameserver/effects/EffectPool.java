package gameserver.effects;

import gameserver.Game;
import gameserver.effects.effects.Effect;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

public class EffectPool{
    public EffectPool(){
        this.pool = new ArrayList<>();
        this.targetPool = new ArrayList<>();
        this.castBy = new ArrayList<>();
    }

    public void tickAll(Game context){
        List<Effect> rm = new ArrayList<>();
        for(int i=0; i<pool.size(); i++){
            Effect e = pool.get(i);
            //System.out.println(e);
            if(e.everActive && !e.active){
                rm.add(e);
            }
            else{
                e.tick(context);
            }
        }

        for(int i=0; i<rm.size(); i++){
            int idx = pool.indexOf(rm.get(i));
            while(true) {
                try {
                    pool.remove(idx);
                    targetPool.remove(idx);
                    castBy.remove(idx);
                    break;
                } catch (ConcurrentModificationException comod) {
                    // handle exception
                    System.out.println("retrying effect removal per comod");
                }
            }
        }
    }

    public boolean containsSingletonEffect(EffectId id){
        //Only one of this effect per game
        for(Effect e : pool){
            if(e.getEffect() == id){
                return true;
            }
        }
        return false;
    }

    public boolean containsUniqueEffect(EffectId id, Entity target){
        //One of this per target
        for(int i=0; i<pool.size(); i++){
            Effect e = pool.get(i);
            Entity t = targetPool.get(i);
            if(e.getEffect().equals(id) && t.id.equals(target.id)){
                return true;
            }
        }
        return false;
    }

    public boolean addSingletonEffect(Effect eff){
        //One of this per target
        EffectId id = eff.getEffect();
        Entity target = eff.on;
        for(int i=0; i<pool.size(); i++){
            Effect e = pool.get(i);
            if(e.getEffect() == id && e.active){
                return false;
            }
        }
        pool.add(eff);
        targetPool.add(target);
        castBy.add(null);
        return true;
    }

    public boolean addCasterUniqueEffect(Effect eff, Entity caster){
        //One of this per target
        EffectId id = eff.getEffect();
        Entity target = eff.on;
        for(int i=0; i<pool.size(); i++){
            Effect e = pool.get(i);
            if(e.getEffect() == id && caster.id.equals(castBy.get(i).id) &&  e.active){
                return false;
            }
        }
        pool.add(eff);
        targetPool.add(target);
        castBy.add(caster);
        return true;
    }

    public boolean addUniqueEffect(Effect eff){
        //One of this per target
        EffectId id = eff.getEffect();
        Entity target = eff.on;
        for(int i=0; i<pool.size(); i++){
            Effect e = pool.get(i);
            Entity t = targetPool.get(i);
            if(e.getEffect() == id && t.id.equals(target.id) && e.active){
                return false;
            }
        }
        pool.add(eff);
        targetPool.add(target);
        castBy.add(null);
        return true;
    }

    public boolean addStackingEffect(Effect eff){
        pool.add(eff);
        targetPool.add(eff.on);
        castBy.add(null);
        return true;
    }

    public boolean isStunned(Titan t){
        return hasEffect(t, EffectId.STUN);
    }

    private List<Effect> pool;
    private List<Entity> targetPool; //Wrapper around a list
    private List<Entity> castBy;

    public List<Effect> getEffects() {
        return pool;
    }

    public List<Entity> getOn() {
        return targetPool;
    }

    public boolean hasEffect(Titan caster, EffectId queryType) {
        for(int i=0; i<pool.size(); i++){
            //For sure it's in there we're jusy checking the wrong
            //System.out.println(targetPool.get(i).id + "" + t.id);
            if(pool.get(i).getEffect() == queryType
                    && targetPool.get(i).id.equals(caster.id)){
                return true;
            }
        }
        return false;
    }

    public boolean isRooted(Titan t) {
        return hasEffect(t, EffectId.ROOT) || hasEffect(t, EffectId.STUN);
    }

    public void cullAllOn(Game context, Entity on) {
        for(int i=0; i<pool.size(); i++){
            Effect e = pool.get(i);
            Entity t = targetPool.get(i);
            if(e.getEffect() != EffectId.DEAD && t.id.equals(on.id) && e.active){
                e.cull(context);
            }
        }
    }
}
