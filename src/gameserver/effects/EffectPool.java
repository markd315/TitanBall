package gameserver.effects;

import gameserver.effects.effects.Effect;
import gameserver.engine.GameEngine;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import org.joda.time.Instant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

public class EffectPool {
    public EffectPool() {
        this.pool = new ArrayList<>();
        this.targetPool = new ArrayList<>();
        this.castBy = new ArrayList<>();
    }

    public boolean containsSingletonEffect(EffectId id) {
        //Only one of this effect per game
        for (Effect e : pool) {
            if (e.getEffect() == id) {
                return true;
            }
        }
        return false;
    }

    public boolean containsUniqueEffect(EffectId id, Entity target) {
        //One of this per target
        for (int i = 0; i < pool.size(); i++) {
            Effect e = pool.get(i);
            Entity t = targetPool.get(i);
            if (e.getEffect().equals(id) && t.id.equals(target.id)) {
                return true;
            }
        }
        return false;
    }

    public boolean addSingletonEffect(Titan caster, Effect eff) {
        //One of this per target
        EffectId id = eff.getEffect();
        Entity target = eff.on;
        for (int i = 0; i < pool.size(); i++) {
            Effect e = pool.get(i);
            if (e.getEffect() == id && e.active) {
                return false;
            }
        }
        pool.add(eff);
        targetPool.add(target);
        castBy.add(caster);
        return true;
    }

    public boolean addCasterUniqueEffect(Effect eff, Titan caster) {
        //One of this per target
        EffectId id = eff.getEffect();
        Entity target = eff.on;
        for (int i = 0; i < pool.size(); i++) {
            Effect e = pool.get(i);
            if (e.getEffect() == id && caster.id.equals(castBy.get(i).id) && e.active) {
                pool.remove(e);
                i--;
            }
        }
        pool.add(eff);
        targetPool.add(target);
        castBy.add(caster);
        return true;
    }

    public boolean addUniqueEffect(Titan caster, Effect eff, GameEngine context) {
        //One of this per target-caster pair, renews if duplicates found
        EffectId id = eff.getEffect();
        Entity target = eff.on;
        List<Integer> rm = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            Effect e = pool.get(i);
            Entity t = targetPool.get(i);
            Entity cb = castBy.get(i);
            if (e.getEffect() == id &&
                    t != null &&
                    t.id.equals(target.id) &&
                    (cb == null || caster == null || cb.id.equals(caster.id)) &&
                    e.end.isAfter(Instant.now())) {
                rm.add(i);
            }
        }
        Collections.reverse(rm);
        for(Integer i : rm){
            Effect toKill = pool.get(i);
            System.out.println("ceasing trap");
            toKill.onCease(context);
            pool.remove(toKill);
            targetPool.remove(i.intValue());
            castBy.remove(i.intValue());
        }
        pool.add(eff);
        targetPool.add(target);
        castBy.add(caster);
        return true;
    }

    public boolean addUniqueEffect(Effect eff, GameEngine context) {
        return addUniqueEffect(null, eff, context);
    }

    public boolean addStackingEffect(Effect eff) {
        return addStackingEffect(null, eff);
    }

    public boolean isStunned(Titan t) {
        return hasEffect(t, EffectId.STUN) || hasEffect(t, EffectId.STEAL);
    }

    private List<Effect> pool;
    private List<Entity> targetPool; //Wrappers around a list
    private List<Titan> castBy;

    public List<Effect> getEffects() {
        return pool;
    }

    public List<Entity> getOn() {
        return targetPool;
    }

    public List<Titan> getCastBy() {
        return castBy;
    }

    public boolean hasEffect(Titan target, EffectId queryType) {
        for (int i = 0; i < pool.size(); i++) {
            if (pool.get(i).getEffect() == queryType
                    && (targetPool.get(i).id.equals(target.id) || pool.get(i).on.id.equals(target.id))) {
                return true;
            }
        }
        return false;
    }

    public boolean isRooted(Titan t) {
        return hasEffect(t, EffectId.ROOT) ||
                hasEffect(t, EffectId.STUN) || hasEffect(t, EffectId.STEAL);
    }

    public void cullAllOn(GameEngine context, Entity on) {
        for (int i = 0; i < pool.size(); i++) {
            Effect e = pool.get(i);
            Entity t = targetPool.get(i);
            if (e.getEffect() != EffectId.DEAD && t.id.equals(on.id) && e.active) {
                e.cull(context);
            }
        }
    }

    public boolean addStackingEffect(Titan caster, Effect eff) {
        pool.add(eff);
        targetPool.add(eff.on);
        castBy.add(caster);
        return true;
    }

    public void tickAll(GameEngine context) {
        List<Effect> rm = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            Effect e = pool.get(i);
            //System.out.println(e);
            if (e.everActive && !e.active) {
                rm.add(e);
            } else {
                e.tick(context);
            }
        }

        for (int i = 0; i < rm.size(); i++) {
            int idx = pool.indexOf(rm.get(i));
            while (true) {
                try {
                    pool.remove(idx);
                    break;
                } catch (ConcurrentModificationException comod) {
                    // handle exception
                    System.out.println("retrying effect removal per comod");
                }
            }
            while (true) {
                try {
                    targetPool.remove(idx);
                    break;
                } catch (ConcurrentModificationException comod) {
                    // handle exception
                    System.out.println("retrying effect removal per comod");
                }
            }
            while (true) {
                try {
                    castBy.remove(idx);
                    break;
                } catch (ConcurrentModificationException comod) {
                    // handle exception
                    System.out.println("retrying effect removal per comod");
                }
            }
        }
    }

    public void cullOnly(Effect e) {
        int idx = getEffects().indexOf(e);
        getEffects().remove(idx);
        getOn().remove(idx);
        getCastBy().remove(idx);
    }
}
