package gameserver.engine;

import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownE;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.cooldowns.CooldownR;
import gameserver.effects.effects.*;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.*;
import gameserver.models.Game;
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

    public AbilityStrategy(GameEngine context, Titan caster){
        this.context = context;
        this.caster = caster;
        int clientIndex = context.clientIndex(caster);
        x = context.lastControlPacket[clientIndex].posX + context.lastControlPacket[clientIndex].camX;
        y = context.lastControlPacket[clientIndex].posY + context.lastControlPacket[clientIndex].camY;
    }

    public void parameterizedFlash(double cdSeconds, int dist){
        int cd = (int) (caster.cooldownFactor * cdSeconds*1000);
        dist*=caster.rangeFactor;
        context.effectPool.addUniqueEffect(new CooldownR(cd, caster), context);
        shape = new Ellipse2D.Double(0, 0, 2, 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, 9999);
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
        double range = 200 * caster.rangeFactor;
        context.effectPool.addUniqueEffect(new CooldownE((int) (cdMs*caster.cooldownFactor), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
        appliedTo = new Targeting(sel, notFriendly, unlimited, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            e.damage(context, dmg);
        }
    }

    public void kick() {
        shape = new Ellipse2D.Double(0, 0, 1, 1);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, (int) (60 * caster.rangeFactor));
        appliedTo = new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if(!appliedTo.isEmpty()){
            context.effectPool.addUniqueEffect(new CooldownR((int) (12000*caster.cooldownFactor), caster), context);
        }
        for (Entity e : appliedTo) {
            double tx = caster.X;
            double ty = caster.Y;
            double ang = Util.degreesFromCoords(tx - e.X, ty - e.Y);
            ang += 180; //Kick them away, not towards
            int limitt = 0;
            while (limitt < 105) {
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
        int range = (int) (250 * caster.rangeFactor);
        shape = new Rectangle(0, 0, 30, 120);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(new CooldownR((int) (caster.cooldownFactor *3500), caster), context);
            context.entityPool.add(new Wall(context, (int) corners.getX(), (int) corners.getY()));
        }
    }

    private boolean inBoundsNotRedzone(Rectangle corners) {
        Rectangle goalH = new Rectangle(Game.GOALIE_XH_MIN+50,
                (Game.GOALIE_Y_MIN+24),
                Game.GOALIE_XH_MAX - Game.GOALIE_XH_MIN,
                Game.GOALIE_Y_MAX - (Game.GOALIE_Y_MIN) + 10);
        Rectangle goalA = new Rectangle(Game.GOALIE_XA_MIN - 4,
                (Game.GOALIE_Y_MIN+24),
                Game.GOALIE_XA_MAX - Game.GOALIE_XA_MIN + 29,
                Game.GOALIE_Y_MAX - (Game.GOALIE_Y_MIN) + 10);
        if(goalA.intersects(corners) || goalH.intersects(corners)
            || goalA.contains(corners) || goalH.contains(corners)){
            return false; //redzone
        }
        return inBounds(corners);
    }

    private boolean inBounds(Rectangle corners){
        Rectangle bounds = new Rectangle(Game.MIN_X, Game.MIN_Y,
                Game.MAX_X - Game.MIN_X,
                Game.MAX_Y - Game.MIN_Y);
        return corners.intersects(bounds) || bounds.contains(corners);
    }

    public void scatter() {
        int range = (int) (180 * caster.rangeFactor);
        context.effectPool.addUniqueEffect(
                new CooldownR((int) (caster.cooldownFactor *12000), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
        appliedTo = new Targeting(sel, champions, unlimited, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        int limit = 0;
        while (limit < 200) {
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
        int range = (int) (200 * caster.rangeFactor);
        shape = new Rectangle(0, 0, 50, 50);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownR((int) (caster.cooldownFactor *7000), caster), context);
            context.entityPool.add(new BallPortal(TeamAffiliation.UNAFFILIATED, caster, context.entityPool,
                    (int) corners.getX(),
                    (int) corners.getY(), context));
        }
    }

    public void heal() {
        int dur = (int) (3000 * caster.durationsFactor);
        int range = (int) (250 * caster.rangeFactor);
        shape = new Ellipse2D.Double(0, 0, 1, 1);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, friendlyIncSelf, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            context.effectPool.addUniqueEffect(
                    new CooldownR((int) (caster.cooldownFactor *8000), caster), context);
            eff = new HealEffect(dur, e, 15, .35);
            context.effectPool.addStackingEffect(eff); //also unique and singleton
        }
    }

    public void chargeShot() {
        int dur = (int) (3000 * caster.durationsFactor);
        context.effectPool
                .addUniqueEffect(new CooldownR((int) (caster.cooldownFactor *7000), caster), context);
        context.effectPool.addUniqueEffect(
                new ShootEffect(dur, caster, 1.5), context);
    }

    public void spawnPortal() {
        int range = (int) (200 * caster.rangeFactor);
        shape = new Rectangle(0, 0, 50, 50);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *5500), caster), context);
            System.out.println("-1 hit");
            context.entityPool.add(new Portal(caster.team, caster,
                    context.entityPool, (int) corners.getX(), (int) corners.getY(), context));
        }
    }

    public void spawnTrap() {
        int range = (int) (200 * caster.rangeFactor);
        shape = new Rectangle(0, 0, 100, 100);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *15000), caster), context);
            context.entityPool.add(new Trap(caster, (int) corners.getX(), (int) corners.getY()));
        }
    }

    public void slow() {
        int dur = (int) (2000 * caster.durationsFactor);
        int range = (int) (150 * caster.rangeFactor);
        shape = new Rectangle(0, 0, 1, 1);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, champions, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *3000), caster), context);
            eff = new RatioEffect(dur, e, EffectId.SLOW, 1.4);
            context.effectPool.addUniqueEffect(
                    eff, context);
        }
    }

    public void suckBall() {
        int range = (int) (280 * caster.rangeFactor);
        context.effectPool.addUniqueEffect(
                new CooldownE((int) (caster.cooldownFactor *30000), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        if (sel.latestCollider.intersects(context.ball.asRect()) && !context.anyPoss()) {
            double tx = caster.X + 35 - context.ball.centerDist;
            double ty = caster.Y + 35 - context.ball.centerDist;
            double ang = Util.degreesFromCoords(tx - context.ball.X, ty - context.ball.Y);
            int limit = 0;
            while (!context.anyPoss() && limit < 65) {
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
        int range = (int) (120 * caster.rangeFactor);
        shape = new Rectangle(0, 0, 70, 70);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownE((int) (caster.cooldownFactor *10000), caster), context);
            context.entityPool.add(new Cage(caster.team, caster,
                    (int) corners.getX(), (int) corners.getY(), context));
        }
    }

    public void releaseCages() {
        context.effectPool.addUniqueEffect(
                new CooldownR((int) (caster.cooldownFactor *20000), caster), context);
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
        int range = (int) (260 * caster.rangeFactor);
        int dur = (int) (durMillis * caster.durationsFactor);
        context.effectPool.addUniqueEffect(
                new CooldownE((int) (caster.cooldownFactor *11000), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
        appliedTo = new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            eff = new EmptyEffect(dur, e, EffectId.BLIND);
            context.effectPool.addCasterUniqueEffect(eff, caster);
        }
    }

    public void molotov() {
        int range = (int) (140 * caster.rangeFactor);
        shape = new Rectangle(0, 0, 140, 140);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            context.effectPool.addUniqueEffect(
                    new CooldownR((int) (caster.cooldownFactor *15000), caster), context);
            context.entityPool.add(new Fire(caster, (int) corners.getX(), (int) corners.getY()));
            //41 ticks per second
            //8.2 tick DPS + 1 initial (more initials+duration if running through constantly)
            //13.3 TD every 15 seconds
            //0.887 DPS
        }
    }

    public void stunByRadius(double durMillis) {
        int range = (int) (130 * caster.rangeFactor);
        int dur = (int) (durMillis * caster.durationsFactor);
        context.effectPool.addUniqueEffect(
                new CooldownE((int) (caster.cooldownFactor *7000), caster), context);
        shape = new Ellipse2D.Double(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
        appliedTo = new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        for (Entity e : appliedTo) {
            eff = new EmptyEffect(dur, e, EffectId.STUN);
            context.effectPool.addCasterUniqueEffect(eff, caster);
        }
    }

    public void shootArrow(double dmg, double cdMs) {
        int range = (int) (250 * caster.rangeFactor);
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
        int dur = (int) (500 * caster.durationsFactor);
        context.effectPool.addUniqueEffect(
                new CooldownQ((int) (caster.cooldownFactor *5000), caster), context);
        shape = new Ellipse2D.Double(0, 0, caster.stealRad*2, caster.stealRad*2);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
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
                eff = new EmptyEffect(dur, tip, EffectId.STEAL);
                context.effectPool.addStackingEffect(caster, eff);

                context.ball.X = caster.X + 35 - context.ball.centerDist;
                context.ball.Y = caster.Y + 35 - context.ball.centerDist;
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
