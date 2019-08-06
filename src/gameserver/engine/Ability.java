package gameserver.engine;

import gameserver.GameEngine;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownCurve;
import gameserver.effects.cooldowns.CooldownE;
import gameserver.effects.cooldowns.CooldownR;
import gameserver.effects.effects.*;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.BallPortal;
import gameserver.entity.minions.Portal;
import gameserver.entity.minions.Trap;
import gameserver.entity.minions.Wall;
import gameserver.targeting.*;
import util.Util;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Ability {
    static Filter friendly = new Filter(TeamAffiliation.SAME, TitanType.ANY, false);
    static Filter friendlyIncSelf = new Filter(TeamAffiliation.SAME, TitanType.ANY, true);
    static Filter champions = new Filter(TeamAffiliation.OPPONENT, TitanType.ANY, false);
    static Filter championsNoGoalie = new Filter(TeamAffiliation.OPPONENT, TitanType.ANY, false);
    static Filter enemiesIncMinions = new Filter(TeamAffiliation.ENEMIES, TitanType.ANY, false);
    static Filter all = new Filter(TeamAffiliation.ANY, TitanType.ANY, true);
    static Filter notFriendly = new Filter(TeamAffiliation.ENEMIES, TitanType.ANY_ENTITY, false);

    static Limiter nearest = new Limiter(SortBy.NEAREST, 1);
    static Limiter unlimited = new Limiter(SortBy.NEAREST, 999);
    static Limiter mouseNear = new Limiter(SortBy.NEAREST_MOUSE, 1);

    public static boolean castE(GameEngine context, Titan caster) throws NullPointerException{
        int clientIndex = context.clientIndex(caster);
        int x = context.lastControlPacket[clientIndex].posX + context.lastControlPacket[clientIndex].camX;
        int y = context.lastControlPacket[clientIndex].posY + context.lastControlPacket[clientIndex].camY;
        if (caster.getType() == TitanType.ARTISAN && caster.possession == 1 &&
                !context.effectPool.hasEffect(caster, EffectId.COOLDOWN_CURVE)) {
            context.effectPool.addUniqueEffect(new CooldownCurve(7000, caster));
            Rectangle shape = new Rectangle(0, 0, 15, 15);
            Selector sel = new Selector(shape, SelectorOffset.CAST_TO_MOUSE, 300);
            new Targeting(sel, champions, nearest, context)
                    .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
            context.serverMouseRoutine(caster, x, y, 4, 0, 0);
            return false;
        } else if (!context.effectPool.hasEffect(caster, EffectId.COOLDOWN_E)) {
            Selector sel = null;
            Effect eff = null;
            Set<Entity> appliedTo = null;
            Shape shape = null;
            Rectangle corners;
            Set<Entity> updateSelectBounds;
            switch (caster.getType()) {
                case MAGE:
                    shape = new Rectangle(0, 0, 50, 50);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            200);
                    sel.select(Collections.EMPTY_SET, x, y, caster);
                    corners = sel.getLatestColliderBounds();
                    if (corners.getWidth() > 0) {
                        context.effectPool.addUniqueEffect(new CooldownE(5500, caster));
                        context.entityPool.add(new Portal(TeamAffiliation.UNAFFILIATED, caster,
                                context.entityPool, (int) corners.getX(), (int) corners.getY()));
                    }
                    break;
                case BUILDER:
                    shape = new Rectangle(0, 0, 50, 50);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            200);
                    //To update the region to caster loc
                    sel.select(Collections.EMPTY_SET, x, y, caster);
                    corners = sel.getLatestColliderBounds();
                    if (corners.getWidth() > 0) {
                        context.effectPool.addUniqueEffect(new CooldownE(15000, caster));
                        context.entityPool.add(new Trap(TeamAffiliation.UNAFFILIATED, (int) corners.getX(), (int) corners.getY()));
                    }
                    break;
                case MARKSMAN:
                    shape = new Rectangle(0, 0, 1, 1);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            150);
                    appliedTo = new Targeting(sel, champions, mouseNear, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    for (Entity e : appliedTo) {
                        context.effectPool.addUniqueEffect(new CooldownE(3000, caster));
                        eff = new SlowEffect(3000, e, 1.4);
                        context.effectPool.addUniqueEffect(eff);
                    }
                    break;
                case ARTISAN:
                    context.effectPool.addUniqueEffect(new CooldownE(30000, caster));
                    shape = new Ellipse2D.Double(0, 0, 280, 280);
                    sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
                    //To update the region to caster loc
                    sel.select(Collections.EMPTY_SET, x, y, caster);
                    if (sel.latestCollider.intersects(context.ball.asRect()) && !context.anyPoss()) {
                        double tx = caster.X + 35 - 7;
                        double ty = caster.Y + 35 - 7;
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
                    break;
                case SUPPORT:
                    context.effectPool.addUniqueEffect(new CooldownE(7000, caster));
                    shape = new Ellipse2D.Double(0, 0, 100, 100);
                    sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
                    appliedTo = new Targeting(sel, champions, nearest, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    for (Entity e : appliedTo) {
                        eff = new EmptyEffect(1200, e, EffectId.STUN);
                        context.effectPool.addCasterUniqueEffect(eff, caster);
                    }
                    break;
                case POST:
                    context.effectPool.addUniqueEffect(new CooldownE(18000, caster));
                    context.effectPool.addUniqueEffect(
                            new DefenseEffect(5000, caster, 10));
                    break;
                case STEALTH:
                    context.effectPool.addUniqueEffect(new CooldownE(18000, caster));
                    context.effectPool.addUniqueEffect(
                            new EmptyEffect(1800, caster, EffectId.STEALTHED));
                    break;
                case SLASHER:
                    context.effectPool.addUniqueEffect(new CooldownE(21000, caster));
                    context.effectPool.addUniqueEffect(new FastEffect(3000, caster, 2.5));
                    break;
                case RANGER:
                    shape = new Rectangle(0, 0, 20, 20);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            250);
                    appliedTo = new Targeting(sel, notFriendly, mouseNear, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    for (Entity e : appliedTo) {
                        context.effectPool.addUniqueEffect(new CooldownE(3000, caster));
                        e.damage(context, 24);
                    }
                    break;
                case WARRIOR:
                    context.effectPool.addUniqueEffect(new CooldownE(28000, caster));
                    shape = new Ellipse2D.Double(0, 0, 2, 2);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, 9999);
                    new Targeting(sel, champions, nearest, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    Rectangle re = sel.latestCollider.getBounds();
                    int limitt = 0;
                    while (limitt < 140) {
                        double ang = Util.degreesFromCoords(re.getX() - caster.X - 35, re.getY() - caster.Y -35);
                        double dx = Math.cos(Math.toRadians((ang)));
                        double dy = Math.sin(Math.toRadians((ang)));
                        if (!caster.collidesSolid(context, context.allSolids, (int) dx, (int) dy)) { //collision
                            caster.X += dx;
                            caster.Y += dy;
                        }
                        limitt++;
                    }
                    break;
            }
            if (sel != null && sel.latestCollider != null) {
                //sel has the bounds, shape has the correct class.
                //so we inject the sel bounds back into the shape class and eventually use the camera to render it
                context.colliders = new HashSet<>();
                Rectangle bounds = sel.latestCollider.getBounds();
                if (shape instanceof Ellipse2D.Double) {
                    context.colliders.add(
                            new ShapePayload(new Ellipse2D.Double(bounds.getX(), bounds.getY(),
                                    bounds.getWidth(), bounds.getHeight())));
                } else if (shape instanceof Polygon) {
                    //TODO this won't work probably
                    context.colliders.add(
                            new ShapePayload(sel.latestCollider));
                } else if (shape instanceof Rectangle) {
                    context.colliders.add(
                            new ShapePayload(new Rectangle((int) bounds.getX(), (int) bounds.getY(),
                                    (int) bounds.getWidth(), (int) bounds.getHeight())));
                }
            }
            return true;
        }
        return false;
    }

    public static boolean castR(GameEngine context, Titan caster) throws NullPointerException{
        int clientIndex = context.clientIndex(caster);
        int x = context.lastControlPacket[clientIndex].posX + context.lastControlPacket[clientIndex].camX;
        int y = context.lastControlPacket[clientIndex].posY + context.lastControlPacket[clientIndex].camY;
        if (caster.getType() == TitanType.ARTISAN && caster.possession == 1 &&
                !context.effectPool.hasEffect(caster, EffectId.COOLDOWN_CURVE)) {
            context.effectPool.addUniqueEffect(new CooldownCurve(7000, caster));
            Rectangle shape = new Rectangle(0, 0, 15, 15);
            Selector sel = new Selector(shape, SelectorOffset.CAST_TO_MOUSE, 300);
            new Targeting(sel, champions, nearest, context)
                    .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
            context.serverMouseRoutine(caster, x, y, 5, 0, 0);
            return false;
        } else if (!context.effectPool.hasEffect(caster, EffectId.COOLDOWN_R)) {
            Selector sel = null;
            Shape shape = null;
            Set<Entity> appliedTo = null;
            Effect eff = null;
            Rectangle corners;
            switch (caster.getType()) {
                case SLASHER:
                    shape = new Rectangle(0, 0, 20, 20);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            250);
                    appliedTo = new Targeting(sel, notFriendly, mouseNear, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    for (Entity e : appliedTo) {
                        context.effectPool.addUniqueEffect(new CooldownR(5000, caster));
                        context.effectPool.addStackingEffect(new FlareEffect(3000, e, 0, 0));
                    }
                    break;
                case MARKSMAN:
                    context.effectPool.addUniqueEffect(new CooldownR(7000, caster));
                    context.effectPool.addUniqueEffect(
                            new ShootEffect(3000, caster, 1.5));
                    break;
                case SUPPORT:
                    shape = new Ellipse2D.Double(0, 0, 1, 1);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            250);
                    appliedTo = new Targeting(sel, friendlyIncSelf, mouseNear, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    for (Entity e : appliedTo) {
                        context.effectPool.addUniqueEffect(new CooldownR(8000, caster));
                        eff = new HealEffect(3000, e, 15, .35);
                        context.effectPool.addStackingEffect(eff); //also unique and singleton
                    }
                    break;
                case ARTISAN:
                    shape = new Rectangle(0, 0, 50, 50);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            200);
                    sel.select(Collections.EMPTY_SET, x, y, caster);
                    corners = sel.getLatestColliderBounds();
                    if (corners.getWidth() > 0) {
                        context.effectPool.addUniqueEffect(new CooldownR(7000, caster));
                        context.entityPool.add(new BallPortal(TeamAffiliation.UNAFFILIATED, caster,
                                context.entityPool, (int) corners.getX(), (int) corners.getY()));
                    }
                    break;
                case POST:
                    context.effectPool.addUniqueEffect(new CooldownR(12000, caster));
                    shape = new Ellipse2D.Double(0, 0, 180, 180);
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
                            if (!e.collidesSolid(context, context.allSolids, (int) dx, (int) dy)) { //collision
                                e.X += dx;
                                e.Y += dy;
                            }
                        }
                        limit++;
                    }
                    break;
                case RANGER:
                    context.effectPool.addUniqueEffect(new CooldownR(12000, caster));
                    shape = new Ellipse2D.Double(0, 0, 120, 120);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, 9999);
                    appliedTo = new Targeting(sel, champions, nearest, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    for (Entity e : appliedTo) {
                        double tx = caster.X;
                        double ty = caster.Y;
                        double ang = Util.degreesFromCoords(tx - e.X, ty - e.Y);
                        ang += 180; //Kick them away, not towards
                        int limitt = 0;
                        while (limitt < 105) {
                            double dx = Math.cos(Math.toRadians((ang)));
                            double dy = Math.sin(Math.toRadians((ang)));
                            if (!e.collidesSolid(context, context.allSolids, (int) dx, (int) dy)) { //collision
                                e.X += dx;
                                e.Y += dy;
                            }
                            limitt++;
                        }
                    }
                    break;
                case MAGE:
                    context.effectPool.addUniqueEffect(new CooldownR(20000, caster));
                    shape = new Rectangle(0, 0, 20, 20);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            250);
                    appliedTo = new Targeting(sel, notFriendly, mouseNear, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    for (Entity e : appliedTo) {
                        eff = new FlareEffect(3000, e, .55, .55);
                        context.effectPool.addStackingEffect(eff); //also unique and singleto
                    }
                    break;
                case WARRIOR:
                    context.effectPool.addUniqueEffect(new CooldownR(4000, caster));
                    shape = new Ellipse2D.Double(0, 0, 200, 200);
                    sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
                    appliedTo = new Targeting(sel, notFriendly, unlimited, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    for (Entity e : appliedTo) {
                        e.damage(context, 40);
                    }
                    break;
                case BUILDER:
                    shape = new Rectangle(0, 0, 30, 30);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER,
                            250);
                    //To update the region to caster loc
                    sel.select(Collections.EMPTY_SET, x, y, caster);
                    corners = sel.getLatestColliderBounds();
                    if (corners.getWidth() > 0) {
                        context.effectPool.addUniqueEffect(new CooldownR(3500, caster));
                        context.entityPool.add(new Wall(context, (int) corners.getX(), (int) corners.getY()));
                    }
                    break;
                case STEALTH:
                    context.effectPool.addUniqueEffect(new CooldownR(28000, caster));
                    shape = new Ellipse2D.Double(0, 0, 2, 2);
                    sel = new Selector(shape, SelectorOffset.MOUSE_CENTER, 9999);
                    new Targeting(sel, champions, nearest, context)
                            .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
                    Rectangle re = sel.latestCollider.getBounds();
                    int limitt = 0;
                    while (limitt < 100) {
                        double ang = Util.degreesFromCoords(re.getX() - caster.X - 35, re.getY() - caster.Y -35);
                        double dx = Math.cos(Math.toRadians((ang)));
                        double dy = Math.sin(Math.toRadians((ang)));
                        if (!caster.collidesSolid(context, context.allSolids, (int) dx, (int) dy)) { //collision
                            caster.X += dx;
                            caster.Y += dy;
                        }
                        limitt++;
                    }
                    break;
            }
            if (sel != null && sel.latestCollider != null) {
                //sel has the bounds, shape has the correct class.
                //so we inject the sel bounds back into the shape class and eventually use the camera to render it
                context.colliders = new HashSet<>();
                Rectangle bounds = sel.latestCollider.getBounds();
                if (shape instanceof Ellipse2D.Double) {
                    context.colliders.add(
                            new ShapePayload(new Ellipse2D.Double(bounds.getX(), bounds.getY(),
                                    bounds.getWidth(), bounds.getHeight())));
                } else if (shape instanceof Polygon) {
                    //TODO this won't work probably
                    context.colliders.add(
                            new ShapePayload(sel.latestCollider));
                } else if (shape instanceof Rectangle) {
                    context.colliders.add(
                            new ShapePayload(new Rectangle((int) bounds.getX(), (int) bounds.getY(),
                                    (int) bounds.getWidth(), (int) bounds.getHeight())));
                }
            }return true;
        }
        return false;
    }

    public static boolean castQ(GameEngine context, Titan caster) throws NullPointerException{
        Selector sel = null;
        Shape shape = null;
        Set<Entity> appliedTo = null;
        Effect eff = null;
        Rectangle corners;
        int clientIndex = context.clientIndex(caster);
        int x = context.lastControlPacket[clientIndex].posX + context.lastControlPacket[clientIndex].camX;
        int y = context.lastControlPacket[clientIndex].posY + context.lastControlPacket[clientIndex].camY;
        //context.effectPool.addUniqueEffect(new CooldownQ(12000, caster));
        shape = new Ellipse2D.Double(0, 0, 160, 160);
        sel = new Selector(shape, SelectorOffset.CAST_CENTER, 9999);
        appliedTo = new Targeting(sel, all, unlimited, context)
                .process(x, y, caster, (int) context.ball.X, (int) context.ball.Y);
        if(appliedTo.size() >= 3){ //doesn't include self, so target and 1 others
            if(Titan.titanInPossession().isPresent()){
                Titan tip = Titan.titanInPossession().get();
                context.stats.grant(context, tip, StatEngine.StatEnum.TURNOVERS);
                context.stats.grant(context, caster, StatEngine.StatEnum.STEALS);
                for(Entity e : appliedTo){
                    if(e.id.equals(tip.id)){
                        tip.possession = 0;
                        context.ball.X += Math.random() * 100 - 50;
                        context.ball.Y += Math.random() * 100 - 50;

                    }
                }
            }
        }
        return true;
    }
}
