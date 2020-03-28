package gameserver.engine;

import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownE;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.cooldowns.CooldownR;
import gameserver.effects.effects.*;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.*;
import gameserver.targeting.SelectorOffset;
import gameserver.targeting.SortBy;
import gameserver.targeting.Targeting;
import gameserver.targeting.core.Filter;
import gameserver.targeting.core.Limiter;
import gameserver.targeting.core.Selector;
import util.Util;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class AbilityStrategy {
    protected Selector sel;
    protected Shape shape;
    protected Set<Entity> appliedTo;
    protected Effect eff;
    protected Rectangle corners;
    protected GameEngine context;
    protected Titan caster;
    protected int x, y;
    static final Filter friendly = new Filter(TeamAffiliation.SAME, TitanType.ANY, false);
    static final Filter friendlyIncSelf = new Filter(TeamAffiliation.SAME, TitanType.ANY, true);
    static final Filter champions = new Filter(TeamAffiliation.OPPONENT, TitanType.ANY, false);
    static final Filter championsNoGoalie = new Filter(TeamAffiliation.OPPONENT, TitanType.ANY, false);
    static final Filter enemiesIncMinions = new Filter(TeamAffiliation.ENEMIES, TitanType.ANY, false);
    static final Filter all = new Filter(TeamAffiliation.ANY, TitanType.ANY, true);
    static final Filter notFriendly = new Filter(TeamAffiliation.ENEMIES, TitanType.ANY_ENTITY, false);

    static final Limiter nearest = new Limiter(SortBy.NEAREST, 1);
    static final Limiter unlimited = new Limiter(SortBy.NEAREST, 999);
    static final Limiter mouseNear = new Limiter(SortBy.NEAREST_MOUSE, 1);

    protected Const c;
    public AbilityStrategy(GameEngine context, Titan caster){
        this.context = context;
        this.caster = caster;
        this.c = context.c;
        int clientIndex = context.clientIndex(caster);
        x = context.lastControlPacket[clientIndex].posX + context.lastControlPacket[clientIndex].camX;
        y = context.lastControlPacket[clientIndex].posY + context.lastControlPacket[clientIndex].camY;
    }

    public void parameterizedFlash(double cdSeconds, int dist){
        int cd = (int) (caster.cooldownFactor * cdSeconds*1000);
        dist*=caster.rangeFactor;
        context.effectPool.addUniqueEffect(new CooldownR(cd, caster), context);
        shape = new Ellipse2D.Double(0, 0, 2, 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, c.FAR_RANGE);
        new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        Rectangle re = sel.latestCollider.getBounds();
        int limitt = 0;
        while (limitt < dist) {
            double ang = Util.degreesFromCoords(re.getX() - caster.X - 35, re.getY() - caster.Y - 35);
            double dx = Math.cos(Math.toRadians((ang)));
            double dy = Math.sin(Math.toRadians((ang)));
            if (!caster.collidesSolid(context, context.allSolids, (int) dx, (int) dy)) { //collision
                caster.translateBounded(context, dx, dy);
            }
            limitt++;
        }
    }

    public void ignite(double cd, double dur, double initialD, double recurringD){
        cd*= caster.cooldownFactor;
        dur*= caster.durationsFactor;
        initialD*=caster.damageFactor;
        recurringD*=caster.damageFactor;
        int range = (int) (250 * caster.rangeFactor);
        shape = new Rectangle(0, 0, 20, 20);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, notFriendly, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            if(initialD + recurringD > 0.0){
                context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            }
            context.effectPool.addUniqueEffect(new CooldownR((int) (cd * 1000), caster), context);
            context.effectPool.addStackingEffect(new FlareEffect((int) (dur*1000), e, initialD, recurringD));
        }
    }

    public void circleSlash(double dmg, double cdMs){
        dmg *= caster.damageFactor;
        double range = c.getI("titan.slash.range") * caster.rangeFactor;
        context.effectPool.addUniqueEffect(new CooldownE((int) (cdMs*caster.cooldownFactor), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        appliedTo = new Targeting(sel, notFriendly, unlimited, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            e.damage(context, dmg);
        }
    }

    public void kick() {
        shape = new Ellipse2D.Double(0, 0, 1, 1);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, (int) (c.getI("titan.kick.range") * caster.rangeFactor));
        appliedTo = new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if(!appliedTo.isEmpty()){
            context.effectPool.addUniqueEffect(new CooldownR((int) (c.getI("titan.kick.range")*caster.cooldownFactor), caster), context);
        }
        for (Entity e : appliedTo) {
            double tx = caster.X;
            double ty = caster.Y;
            double ang = Util.degreesFromCoords(tx - e.X, ty - e.Y);
            ang += 180; //Kick them away, not towards
            int limitt = 0;
            while (limitt < c.getI("titan.kick.range")) {
                double dx = Math.cos(Math.toRadians((ang)));
                double dy = Math.sin(Math.toRadians((ang)));
                if (!e.collidesSolid(context, context.allSolids, 0, (int) dy)) { //collision
                    e.translateBounded(context, 0, dy);
                }
                if (!e.collidesSolid(context, context.allSolids, (int) dx, 0)) { //collision
                    e.translateBounded(context, dx, 0);
                }
                limitt++;
            }
        }
    }

    public void wall() {
        int range = (int) (c.getI("titan.wall.range") * caster.rangeFactor);
        shape = new Rectangle(0, 0, 12, 120);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(new CooldownR((int) (caster.cooldownFactor *c.getI("titan.wall.cdms")), caster), context);
            context.entityPool.add(new Wall(context, (int) corners.getX(), (int) corners.getY()));
        }
    }

    private boolean inBoundsNotRedzone(Rectangle corners) {
        Rectangle goalH = new Rectangle(context.c.GOALIE_XH_MIN+50,
                (context.c.GOALIE_Y_MIN+24),
                context.c.GOALIE_XH_MAX - context.c.GOALIE_XH_MIN,
                context.c.GOALIE_Y_MAX - (context.c.GOALIE_Y_MIN) + 10);
        Rectangle goalA = new Rectangle(context.c.GOALIE_XA_MIN - 4,
                (context.c.GOALIE_Y_MIN+24),
                context.c.GOALIE_XA_MAX - context.c.GOALIE_XA_MIN + 29,
                context.c.GOALIE_Y_MAX - (context.c.GOALIE_Y_MIN) + 10);
        if(goalA.intersects(corners) || goalH.intersects(corners)
            || goalA.contains(corners) || goalH.contains(corners)){
            return false; //redzone
        }
        return inBounds(corners);
    }

    private boolean inBounds(Rectangle corners){
        Rectangle bounds = new Rectangle(context.c.MIN_X, context.c.MIN_Y,
                context.c.MAX_X - context.c.MIN_X,
                context.c.MAX_Y - context.c.MIN_Y);
        return corners.intersects(bounds) || bounds.contains(corners);
    }

    public void scatter() {
        int range = (int) (c.getI("titan.scatter.range") * caster.rangeFactor);
        context.effectPool.addUniqueEffect(
                new CooldownR((int) (caster.cooldownFactor *c.getI("titan.scatter.cdms")), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        appliedTo = new Targeting(sel, champions, unlimited, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        int limit = 0;
        while (limit < c.getI("titan.scatter.dist")) {
            for (Entity e : appliedTo) {
                double tx = caster.X;
                double ty = caster.Y;
                double ang = Util.degreesFromCoords(tx - e.X, ty - e.Y);
                ang += 180; //Kick them away, not towards
                double dx = Math.cos(Math.toRadians((ang)));
                double dy = Math.sin(Math.toRadians((ang)));
                if (!e.collidesSolid(context, context.allSolids, 0, (int) dy)) { //collision
                    e.translateBounded(context, 0, dy);
                }
                if (!e.collidesSolid(context, context.allSolids, (int) dx, 0)) { //collision
                    e.translateBounded(context, dx, 0);
                }
            }
            limit++;
        }
    }

    public void spawnBallPortal() {
        int range = (int) (c.getI("titan.bportal.range") * caster.rangeFactor);
        shape = new Rectangle(0, 0, 50, 50);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownR((int) (caster.cooldownFactor *c.getI("titan.bportal.cdms")), caster), context);
            context.entityPool.add(new BallPortal(TeamAffiliation.UNAFFILIATED, caster, context.entityPool,
                    (int) corners.getX(),
                    (int) corners.getY(), context));
        }
    }

    public void heal() {
        int dur = (int) (c.getI("titan.heal.dur") * caster.durationsFactor);
        int range = (int) (c.getI("titan.heal.range") * caster.rangeFactor);
        shape = new Ellipse2D.Double(0, 0, 1, 1);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, friendlyIncSelf, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            context.effectPool.addUniqueEffect(
                    new CooldownR((int) (caster.cooldownFactor *c.getD("titan.heal.cdms")), caster), context);
            eff = new HealEffect(dur, e, c.getD("titan.heal.initd"), c.getD("titan.heal.recurd"));
            context.effectPool.addStackingEffect(eff); //also unique and singleton
        }
    }

    public void chargeShot() {
        int dur = (int) (c.getI("titan.shoot.dur") * caster.durationsFactor);
        context.effectPool
                .addUniqueEffect(new CooldownR((int) (caster.cooldownFactor *c.getI("titan.shoot.cdms")), caster), context);
        context.effectPool.addUniqueEffect(
                new ShootEffect(dur, caster, c.getD("titan.shoot.ratio")), context);
    }

    public void spawnPortal() {
        int range = (int) (c.getI("titan.portal.range") * caster.rangeFactor);
        shape = new Rectangle(0, 0, 50, 50);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *c.getI("titan.portal.range")), caster), context);
            System.out.println("-1 hit");
            context.entityPool.add(new Portal(caster.team, caster,
                    context.entityPool, (int) corners.getX(), (int) corners.getY(), context));
        }
    }

    public void spawnTrap() {
        int range = (int) (c.getI("titan.trap.range") * caster.rangeFactor);
        shape = new Rectangle(0, 0, 100, 100);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *c.getI("titan.trap.cdms")), caster), context);
            context.entityPool.add(new Trap(caster, context, (int) corners.getX(), (int) corners.getY()));
        }
    }

    public void slow() {
        int dur = (int) (c.getI("titan.trap.dur") * caster.durationsFactor);
        int range = (int) (c.getI("titan.trap.range") * caster.rangeFactor);
        shape = new Rectangle(0, 0, 1, 1);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, champions, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *c.getI("titan.trap.cdms")), caster), context);
            eff = new RatioEffect(dur, e, EffectId.SLOW, c.getD("titan.trap.ratio"));
            context.effectPool.addUniqueEffect(
                    eff, context);
        }
    }

    public void suckBall() {
        int range = (int) (c.getI("titan.suck.range") * caster.rangeFactor);
        context.effectPool.addUniqueEffect(
                new CooldownE((int) (caster.cooldownFactor *c.getI("titan.suck.cdms")), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        if (sel.latestCollider.intersects(context.ball.asRect()) && !context.anyPoss()) {
            double tx = caster.X + caster.centerDist - context.ball.centerDist;
            double ty = caster.Y + caster.centerDist - context.ball.centerDist;
            double ang = Util.degreesFromCoords(tx - context.ball.X, ty - context.ball.Y);
            int limit = 0;
            while (!context.anyPoss() && limit < c.getI("titan.suck.dist")) {
                context.ball.X += 3.0 * Math.cos(Math.toRadians((ang)));
                context.ball.Y += 3.0 * Math.sin(Math.toRadians((ang)));
                context.intersectAll();
                try {
                    context.detectGoals();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                limit++;
            }
        }
    }

    public void spawnCage() {
        int range = (int) (c.getI("titan.cage.range") * caster.rangeFactor);
        shape = new Rectangle(0, 0, 70, 70);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *c.getI("titan.cage.cdms")), caster), context);
            context.entityPool.add(new Cage(caster.team, caster,
                    (int) corners.getX(), (int) corners.getY(), context));
        }
    }

    public void releaseCages() {
        context.effectPool.addUniqueEffect(
                new CooldownR((int) (caster.cooldownFactor *c.getI("titan.wolf.cdms")), caster), context);
        ArrayList<Cage> cages = new ArrayList<Cage>();
        for(Entity e : context.entityPool){
            if(e instanceof Cage &&
                    ((Cage)e).getCreatedById().equals(caster.id)){
                cages.add((Cage) e);
            }
        }
        for(Cage c : cages){
            c.open(context, cages.size());
        }
    }

    public void flashbang(double durMillis) {
        int range = (int) (c.getI("titan.flashbang.range") * caster.rangeFactor);
        int dur = (int) (durMillis * caster.durationsFactor);
        context.effectPool.addUniqueEffect(
                new CooldownE((int) (caster.cooldownFactor *c.getI("titan.flashbang.cdms")), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        appliedTo = new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            eff = new EmptyEffect(dur, e, EffectId.BLIND);
            context.effectPool.addCasterUniqueEffect(eff, caster);
        }
    }

    public void molotov() {
        int range = (int) (c.getI("titan.molotov.range") * caster.rangeFactor);
        shape = new Rectangle(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownR((int) (caster.cooldownFactor *c.getI("titan.molotov.cdms")), caster), context);
            context.entityPool.add(new Fire(caster, (int) corners.getX(), (int) corners.getY()));
            //41 ticks per second
            //8.2 tick DPS + 1 initial (more initials+duration if running through constantly)
            //13.3 TD every 15 seconds
            //0.887 DPS
        }
    }

    public void stunByRadius(double durMillis) {
        int range = (int) (c.getI("titan.stun.range") * caster.rangeFactor);
        int dur = (int) (durMillis * caster.durationsFactor);
        context.effectPool.addUniqueEffect(
                new CooldownE((int) (caster.cooldownFactor *c.getI("titan.stun.cdms")), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        appliedTo = new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            eff = new EmptyEffect(dur, e, EffectId.STUN);
            context.effectPool.addCasterUniqueEffect(eff, caster);
        }
    }

    public void shootArrow(double dmg, double cdMs) {
        int range = (int) (c.getI("titan.arrow.range") * caster.rangeFactor);
        dmg *= caster.damageFactor;
        shape = new Rectangle(0, 0, 20, 20);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, notFriendly, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *cdMs), caster), context);
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            e.damage(context, dmg);
        }
    }

    public boolean stealBall() {
        context.effectPool.addUniqueEffect(
                new CooldownQ((int) (caster.cooldownFactor *c.STEAL_CD), caster), context);
        shape = new Ellipse2D.Double(0, 0, caster.stealRad*2, caster.stealRad*2);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        new Targeting(sel, champions, unlimited, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (context.titanInPossession().isPresent() &&
                !context.titanInPossession().get().id.equals(caster.id)) {
            //Change previous conditional to exclude teammates in prod.
            Titan tip = context.titanInPossession().get();
            double x = sel.getLatestColliderCircle().x;
            double y = sel.getLatestColliderCircle().y;
            double r = sel.getLatestColliderCircle().height / 2.0;
            if (context.ball.intersectCircle(x, y, r) && context.ballVisible) {
                context.stats.grant(context, tip, StatEngine.StatEnum.TURNOVERS);
                context.stats.grant(context, caster, StatEngine.StatEnum.STEALS);
                tip.possession = 0;
                eff = new EmptyEffect((int) (c.STOLEN_STUN * caster.durationsFactor), tip, EffectId.STEAL);
                context.effectPool.addStackingEffect(caster, eff);

                context.ball.X = caster.X + caster.centerDist - context.ball.centerDist;
                context.ball.Y = caster.Y + caster.centerDist - context.ball.centerDist;
                caster.actionState = Titan.TitanState.IDLE;
                caster.actionFrame = 0;
                caster.possession = 1;
                return true;
            }
        }
        caster.pushMove();
        return false;
    }
}
