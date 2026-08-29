package gameserver.engine;

import gameserver.Const;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.cooldowns.CooldownSteal;
import gameserver.effects.cooldowns.CooldownW;
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

import com.fasterxml.jackson.annotation.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AbilityStrategy    {
    public AbilityStrategy() {}

    protected Selector sel;
    protected CollisionMath.Bounds shape;
    protected Set<Entity> appliedTo;
    protected Effect eff;
    protected CollisionMath.Bounds corners;
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

    public AbilityStrategy(GameEngine context, Titan caster) {
        this.context = context;
        this.caster = caster;
        this.c = context.c;
        int clientIndex = context.clientIndex(caster);
        x = context.lastControlPacket[clientIndex].posX + context.lastControlPacket[clientIndex].camX;
        y = context.lastControlPacket[clientIndex].posY + context.lastControlPacket[clientIndex].camY;
    }

     public void goOnCooldown(Titan caster, String cdKey, char qOrW) {
        // For Q, CD key is i
        switch (qOrW) {
            case 'Q':
                context.effectPool.addUniqueEffect(
                        new CooldownQ((int) (caster.cooldownFactor * c.getI(cdKey)), caster), context);
                break;
            case 'W':
                context.effectPool.addUniqueEffect(
                        new CooldownW((int) (caster.cooldownFactor * c.getI(cdKey)), caster), context);
                break;
            case 'S':
                context.effectPool.addUniqueEffect(
                        new CooldownSteal((int) (caster.cooldownFactor * c.getI(cdKey)), caster), context);
                break;
        }
    }

    public void parameterizedFlash(double cdSeconds, int dist) {
        int cd = (int) (caster.cooldownFactor * cdSeconds * 1000);
        dist *= caster.rangeFactor;
        context.effectPool.addUniqueEffect(new CooldownW(cd, caster), context);
        shape = new CollisionMath.Bounds(0, 0, 2, 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, c.FAR_RANGE);
        new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        CollisionMath.Bounds re = sel.latestCollider;
        int limitt = 0;
        while (limitt < dist) {
            double ang = Util.degreesFromCoords(re.minX() - caster.X - 35, re.minY() - caster.Y - 35);
            double dx = Math.cos(Math.toRadians((ang)));
            double dy = Math.sin(Math.toRadians((ang)));
            if (!caster.collidesSolid(context, context.allSolids, (int) dx, (int) dy)) { //collision
                caster.translateBounded(context, dx, dy);
            }
            limitt++;
        }
        if (caster.possession == 1) {
            context.ball.X = caster.X + 35 - context.ball.centerDist;
            context.ball.Y = caster.Y + 35 - context.ball.centerDist;
        }
        caster.pushMove();
    }

    public void ignite(double cd, double dur, double initialD, double recurringD) {
        cd *= caster.cooldownFactor;
        dur *= caster.durationsFactor;
        initialD *= caster.damageFactor;
        recurringD *= caster.damageFactor;
        int range = (int) (c.getI("titan.ignite.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, notFriendly, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }
        for (Entity e : appliedTo) {
            if (initialD + recurringD > 0.0) {
                context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            }
            context.effectPool.addUniqueEffect(new CooldownW((int) (cd * 1000), caster), context);
            context.effectPool.addStackingEffect(new FlareEffect((int) (dur * 1000), e, initialD, recurringD));
        }
    }

    public void circleSlash(double dmg, double cdMs) {
        dmg *= caster.damageFactor;
        double range = c.getI("titan.slash.range") * caster.rangeFactor;
        shape = new CollisionMath.Bounds(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        appliedTo = new Targeting(sel, notFriendly, unlimited, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }
        context.effectPool.addUniqueEffect(new CooldownQ((int) (cdMs * caster.cooldownFactor), caster), context);
        for (Entity e : appliedTo) {
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            e.damage(context, dmg);
        }
        caster.pushMove();
    }

    public void kickSelectedTarget() {
        int range = (int) (c.getI("titan.kick.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, range);
        appliedTo = new Targeting(sel, champions, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (!appliedTo.isEmpty()) {
            context.effectPool.addUniqueEffect(new CooldownW((int) (c.getI("titan.kick.cdms") * caster.cooldownFactor), caster), context);
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
        shape = new CollisionMath.Bounds(0, 0, 12, 120);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.width() > 0 && inBoundsNotRedzone(corners)) {
            goOnCooldown(caster, "titan.wall.cdms", 'W');
            Wall w = new Wall(context, (int) corners.minX(), (int) corners.minY());
            w.team = caster.team;
            context.entityPool.add(w);
        }
    }

    private boolean inBoundsNotRedzone(CollisionMath.Bounds corners) {
        CollisionMath.Bounds goalH = new CollisionMath.Bounds(context.c.GOALIE_XH_MIN + 50,
                (context.c.GOALIE_Y_MIN + 24),
                context.c.GOALIE_XH_MAX - context.c.GOALIE_XH_MIN,
                context.c.GOALIE_Y_MAX - (context.c.GOALIE_Y_MIN) + 10);
        CollisionMath.Bounds goalA = new CollisionMath.Bounds(context.c.GOALIE_XA_MIN - 4,
                (context.c.GOALIE_Y_MIN + 24),
                context.c.GOALIE_XA_MAX - context.c.GOALIE_XA_MIN + 29,
                context.c.GOALIE_Y_MAX - (context.c.GOALIE_Y_MIN) + 10);
        if (goalA.intersects(corners) ||
        goalH.intersects(corners) ||
        goalA.contains(new CollisionMath.Point2D(corners.minX(), corners.minY())) ||
        goalA.contains(new CollisionMath.Point2D(corners.minX() + corners.width(), corners.minY() + corners.height())) ||
        goalH.contains(new CollisionMath.Point2D(corners.minX(), corners.minY())) ||
        goalH.contains(new CollisionMath.Point2D(corners.minX() + corners.width(), corners.minY() + corners.height()))) {
            return false; //redzone
        }
        return inBounds(corners);
    }

    private boolean inBounds(CollisionMath.Bounds corners) {
        CollisionMath.Bounds bounds = new CollisionMath.Bounds(context.c.MIN_X, context.c.MIN_Y,
                context.c.MAX_X - context.c.MIN_X,
                context.c.MAX_Y - context.c.MIN_Y);
        return corners.intersects(bounds) || 
                bounds.contains(new CollisionMath.Point2D(corners.minX(), corners.minY())) ||
                bounds.contains(new CollisionMath.Point2D(corners.minX(), corners.minY()));
    }

    public void scatter(int rangeIn, int scatterDist, int cdms) {
        int range = (int) (rangeIn * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        appliedTo = new Targeting(sel, champions, unlimited, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }
        context.effectPool.addUniqueEffect(
                new CooldownW((int) (caster.cooldownFactor * cdms), caster), context);
        int limit = 0;
        while (limit < scatterDist) {
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
        shape = new CollisionMath.Bounds(0, 0, 50, 50);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            goOnCooldown(caster, "titan.bportal.cdms", 'W');
            context.entityPool.add(new BallPortal(caster.team, caster, context.entityPool,
                    (int) corners.getX(),
                    (int) corners.getY(), context));
        }
    }

    public void heal() {
        int dur = (int) (c.getI("titan.heal.dur") * caster.durationsFactor);
        int range = (int) (c.getI("titan.heal.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, friendlyIncSelf, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }
        for (Entity e : appliedTo) {
            goOnCooldown(caster, "titan.heal.cdms", 'W');
            eff = new HealEffect(dur, e, c.getD("titan.heal.initd"), c.getD("titan.heal.recurd"));
            context.effectPool.addStackingEffect(eff); //also unique and singleton
        }
    }

    public void chargeShot() {
        int dur = (int) (c.getI("titan.shoot.dur") * caster.durationsFactor);
        goOnCooldown(caster, "titan.shoot.cdms", 'W');
        context.effectPool.addUniqueEffect(
                new ShootEffect(dur, caster, c.getD("titan.shoot.ratio")), context);
    }

    public void spawnPortal() {
        int range = (int) (c.getI("titan.portal.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, 50, 50);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            goOnCooldown(caster, "titan.portal.cdms", 'Q');
            System.out.println("-1 hit");
            context.entityPool.add(new Portal(caster.team, caster,
                    context.entityPool, (int) corners.getX(), (int) corners.getY(), context));
        }
    }

    public void spawnTrap() {
        int range = (int) (c.getI("titan.trap.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, 100, 100);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            goOnCooldown(caster, "titan.trap.cdms", 'Q');
            context.entityPool.add(new Trap(caster, context, (int) corners.getX(), (int) corners.getY()));
        }
    }

    public void slow() {
        int dur = (int) (c.getI("titan.slow.dur") * caster.durationsFactor);
        int range = (int) (c.getI("titan.slow.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, champions, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }
        for (Entity e : appliedTo) {
            goOnCooldown(caster, "titan.slow.cdms", 'Q');
            eff = new RatioEffect(dur, e, EffectId.SLOW, c.getD("titan.slow.ratio"));
            context.effectPool.addUniqueEffect(
                    eff, context);
        }
    }

    public void suckBall() {
        int range = (int) (c.getI("titan.suck.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        if (sel.latestCollider.intersects(context.ball.asBounds()) && !context.anyPoss()) {
            goOnCooldown(caster, "titan.suck.cdms", 'Q');
            if (context.lastPossessed != null && context.lastPossessed.equals(caster.id)) {
                context.lastPossessed = null;
            }
            double casterCenterX = caster.X + (caster.width > 0 ? caster.width / 2.0 : 35.0);
            double casterCenterY = caster.Y + (caster.height > 0 ? caster.height / 2.0 : 35.0);

            double ballCenterX = context.ball.X + (context.ball.width > 0 ? context.ball.width / 2.0 : 10.0);
            double ballCenterY = context.ball.Y + (context.ball.height > 0 ? context.ball.height / 2.0 : 10.0);

            double dx = casterCenterX - ballCenterX;
            double dy = casterCenterY - ballCenterY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            double dirX = 0.0;
            double dirY = 0.0;
            if (dist > 0) {
                dirX = dx / dist;
                dirY = dy / dist;
            }

            int limit = 0;
            int maxDist = (int)(range * 1.5); // Enough to pull from max range
            while (!context.anyPoss() && limit < maxDist) {
                context.ball.X += 3.0 * dirX;
                context.ball.Y += 3.0 * dirY;
                if (!context.contactExemptBall()) {
                    for (int n = context.players.length - 1; n >= 0; n--) {
                        Titan p = context.players[n];
                        if (p.id.equals(caster.id) || p.team != caster.team) {
                            context.intersectBall(n + 1, (int) p.X, (int) p.Y);
                        }
                    }
                    Entity[] arr = new Entity[1];
                    context.ball.collidesSolid(context, context.entityPool.toArray(arr));
                }
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
        shape = new CollisionMath.Bounds(0, 0, 70, 70);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, c.FAR_RANGE);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            goOnCooldown(caster, "titan.cage.cdms", 'Q');
            context.entityPool.add(new Cage(caster.team, caster,
                    (int) corners.getX(), (int) corners.getY(), context));
        }
    }

    public void releaseCages() {
        goOnCooldown(caster, "titan.wolf.cdms", 'W');
        ArrayList<Cage> cages = new ArrayList<Cage>();
        for (Entity e : context.entityPool) {
            if (e instanceof Cage &&
                    ((Cage) e).getCreatedById().equals(caster.id)) {
                cages.add((Cage) e);
            }
        }
        for (Cage c : cages) {
            c.open(context, cages.size());
        }
    }

    public void flashbang(double durMillis) {
        int range = (int) (c.getI("titan.flashbang.range") * caster.rangeFactor);
        int dur = (int) (durMillis * caster.durationsFactor);
        shape = new CollisionMath.Bounds(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        appliedTo = new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }
        goOnCooldown(caster, "titan.flashbang.cdms", 'Q');
        for (Entity e : appliedTo) {
            eff = new EmptyEffect(dur, e, EffectId.BLIND);
            context.effectPool.addCasterUniqueEffect(eff, caster);
        }
    }

    public void molotov() {
        int range = (int) (c.getI("titan.molotov.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        //To update the region to caster loc
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();

        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            goOnCooldown(caster, "titan.molotov.cdms", 'W');
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
        shape = new CollisionMath.Bounds(0, 0, range, range);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, c.FAR_RANGE);
        appliedTo = new Targeting(sel, champions, nearest, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }
        goOnCooldown(caster, "titan.stun.cdms", 'Q');
        for (Entity e : appliedTo) {
            eff = new EmptyEffect(dur, e, EffectId.STUN);
            context.effectPool.addCasterUniqueEffect(eff, caster);
        }
    }

    public void shootArrow(double dmg) {
        int range = (int) (c.getI("titan.arrow.range") * caster.rangeFactor);
        dmg *= caster.damageFactor;
        shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                range);
        appliedTo = new Targeting(sel, notFriendly, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }
        goOnCooldown(caster, "titan.arrow.cdms", 'Q');
        for (Entity e : appliedTo) {
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            e.damage(context, dmg);
        }
    }

    public boolean stealBall() {
        if (context.titanInPossession().isEmpty() || !context.titanInPossession().get().id.equals(caster.id)) {
            goOnCooldown(caster, "titan.steal.cdms", 'S');
            if (context.titanInPossession().isPresent()) {
                Titan tip = context.titanInPossession().get();
                boolean isOccupyingParapet = false;
                for (Entity e : context.entityPool) {
                    if (e instanceof Parapet p && p.isMounted(tip.id)) {
                        isOccupyingParapet = true;
                        break;
                    }
                }
                if (!isOccupyingParapet) {
                    double cCtrX = caster.X + caster.width / 2;
                    double cCtrY = caster.Y + caster.height / 2;
                    if (context.ball.intersectCircle(cCtrX, cCtrY, caster.stealRad) && context.ballVisible) {
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
            }
            caster.pushMove();
        }
        return false;
    }

    public void captainShoot() {
        if (caster.ammo <= 0) {
            return;
        }
        int range = (int) (c.getI("titan.captain.shot.range") * caster.rangeFactor);
        double dmgChamp = 5.0 * caster.damageFactor;
        double dmgMinion = 10.0 * caster.damageFactor;
        shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, range);
        appliedTo = new Targeting(sel, notFriendly, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if (appliedTo.isEmpty()) {
            return;
        }

        for (Entity e : appliedTo) {
            context.effectPool.addStackingEffect(caster, new EmptyEffect(5000, e, EffectId.ATTACKED));
            if (e instanceof Titan) {
                e.damage(context, dmgChamp);
            } else {
                e.damage(context, dmgMinion);
            }
        }

        caster.ammo--;
        if (caster.ammo > 0) {
            goOnCooldown(caster, "titan.captain.shot.cdms", 'Q');
        } else {
            goOnCooldown(caster, "titan.captain.reload.cdms", 'Q');
        }
    }

    public void captainSlideBomb() {
        int maxDist = (int) (c.getI("titan.captain.slide.range") * caster.rangeFactor);
        goOnCooldown(caster, "titan.captain.slide.cdms", 'W');

        // Drop 3s timebomb at origin
        context.entityPool.add(new Bomb(caster, (int) caster.X, (int) caster.Y, context));

        // Slide toward target direction up to maxDist
        double ang = Util.degreesFromCoords(x - caster.X - 35, y - caster.Y - 35);
        double dx = Math.cos(Math.toRadians(ang));
        double dy = Math.sin(Math.toRadians(ang));
        int limit = 0;
        while (limit < maxDist) {
            if (!caster.collidesSolid(context, context.allSolids, 0, (int) dy)) {
                caster.translateBounded(context, 0, dy);
            }
            if (!caster.collidesSolid(context, context.allSolids, (int) dx, 0)) {
                caster.translateBounded(context, dx, 0);
            }
            limit++;
        }
    }

    public void spiderWeb() {
        int range = (int) (c.getI("titan.spider.web.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, 110, 110);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, range);
        sel.select(Collections.EMPTY_SET, x, y, caster);
        corners = sel.getLatestColliderBounds();
        if (corners.getWidth() > 0 && inBoundsNotRedzone(corners)) {
            goOnCooldown(caster, "titan.spider.web.cdms", 'Q');
            context.entityPool.add(new Web(caster, context, (int) corners.getX(), (int) corners.getY()));
        }
    }

    public void spiderCocoon() {
        int range = (int) (c.getI("titan.spider.cocoon.range") * caster.rangeFactor);
        shape = new CollisionMath.Bounds(0, 0, range * 2, range * 2);
        sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, range);
        appliedTo = new Targeting(sel, all, mouseNear, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);

        Entity targetHero = null;
        for (Entity e : appliedTo) {
            if (e instanceof Titan && !e.id.equals(caster.id)) {
                targetHero = e;
                break;
            }
        }
        if (targetHero == null) {
            return;
        }

        goOnCooldown(caster, "titan.spider.cocoon.cdms", 'W');
        caster.actionState = Titan.TitanState.A2;
        context.effectPool.addUniqueEffect(new EmptyEffect(1000, caster, EffectId.ROOT), context);
        final double origX = caster.X + 35.0;
        final double origY = caster.Y + 35.0;
        final double targetCastX = targetHero.X + 35.0;
        final double targetCastY = targetHero.Y + 35.0;

        double dx = targetCastX - origX;
        double dy = targetCastY - origY;
        double dist = Math.hypot(dx, dy);
        if (dist < 1.0) {
            dx = 1.0;
            dy = 0.0;
            dist = 1.0;
        }
        double rawDestX = targetCastX + (dx / dist) * 70.0 - 35.0;
        double rawDestY = targetCastY + (dy / dist) * 70.0 - 35.0;

        final double destX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X - 70, rawDestX));
        final double destY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y - 70, rawDestY));
        final double finalDx = dx;
        final double finalDy = dy;

        context.effectPool.addStackingEffect(new CallbackEffect(1000, caster, EffectId.COOLDOWN_W, () -> {
            double finalX = destX;
            double finalY = destY;

            // Find overlapping player if any to determine preferred push-out direction
            double prefAngle = Math.atan2(finalDy, finalDx);
            if (context.players != null) {
                for (Titan other : context.players) {
                    if (other != null && other.health > 0 && !other.id.equals(caster.id)) {
                        int otherW = other.width > 0 ? other.width : 70;
                        int otherH = other.height > 0 ? other.height : 70;
                        if (finalX + 70 > other.X && finalX < other.X + otherW &&
                            finalY + 70 > other.Y && finalY < other.Y + otherH) {
                            double pdx = (finalX + 35.0) - (other.X + otherW / 2.0);
                            double pdy = (finalY + 35.0) - (other.Y + otherH / 2.0);
                            if (Math.hypot(pdx, pdy) > 0.1) {
                                prefAngle = Math.atan2(pdy, pdx);
                            }
                            break;
                        }
                    }
                }
            }

            double[] safePos = findClosestUnoccupiedPosition(finalX, finalY, caster, context, prefAngle);
            caster.setX((int) safePos[0]);
            caster.setY((int) safePos[1]);
            caster.actionState = Titan.TitanState.IDLE;
        }));
    }

    private boolean isPositionOccupied(double testX, double testY, Titan caster, GameEngine context) {
        if (testX < context.c.MIN_X || testX > context.c.MAX_X - 70 ||
            testY < context.c.MIN_Y || testY > context.c.MAX_Y - 70) {
            return true;
        }
        double prevX = caster.X;
        double prevY = caster.Y;
        caster.setX((int) testX);
        caster.setY((int) testY);

        boolean collides = false;
        if (caster.collidesSolid(context, context.allSolids)) {
            collides = true;
        } else if (context.players != null) {
            for (Titan other : context.players) {
                if (other != null && other.health > 0 && !other.id.equals(caster.id)) {
                    int otherW = other.width > 0 ? other.width : 70;
                    int otherH = other.height > 0 ? other.height : 70;
                    if (testX + 70 > other.X && testX < other.X + otherW &&
                        testY + 70 > other.Y && testY < other.Y + otherH) {
                        collides = true;
                        break;
                    }
                }
            }
        }
        caster.setX((int) prevX);
        caster.setY((int) prevY);
        return collides;
    }

    private double[] findClosestUnoccupiedPosition(double initialX, double initialY, Titan caster, GameEngine context, double prefAngle) {
        if (!isPositionOccupied(initialX, initialY, caster, context)) {
            return new double[]{initialX, initialY};
        }

        for (int dist = 5; dist <= 300; dist += 5) {
            for (int i = 0; i < 16; i++) {
                int sign = (i % 2 == 0) ? 1 : -1;
                double angleOffset = sign * (i / 2) * (Math.PI / 8.0);
                double angle = prefAngle + angleOffset;

                double testX = initialX + Math.cos(angle) * dist;
                double testY = initialY + Math.sin(angle) * dist;

                testX = Math.max(context.c.MIN_X, Math.min(context.c.MAX_X - 70, testX));
                testY = Math.max(context.c.MIN_Y, Math.min(context.c.MAX_Y - 70, testY));

                if (!isPositionOccupied(testX, testY, caster, context)) {
                    return new double[]{testX, testY};
                }
            }
        }
        return new double[]{initialX, initialY};
    }
}
