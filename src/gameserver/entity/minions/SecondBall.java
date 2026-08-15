package gameserver.entity.minions;

import gameserver.engine.GameEngine;
import gameserver.engine.GoalHoop;
import gameserver.engine.Team;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Box;
import gameserver.entity.Collidable;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.io.Serializable;
import java.util.Random;

public class SecondBall extends Entity implements Tickable, Collidable, Serializable {
    public static final long serialVersionUID = 1L;

    public double vx = 2.0;
    public double vy = 1.0;
    private static final Random rand = new Random();

    public SecondBall() {
        super(TeamAffiliation.UNAFFILIATED);
    }

    public SecondBall(int x, int y) {
        super(TeamAffiliation.UNAFFILIATED);
        this.setX(x);
        this.setY(y);
        this.width = 30;
        this.height = 30;
        this.health = 99999;
        this.maxHealth = 99999;
        this.solid = false;
        
        // Randomize initial direction
        double angle = rand.nextDouble() * 2 * Math.PI;
        double speed = 3.0;
        vx = Math.cos(angle) * speed;
        vy = Math.sin(angle) * speed;
    }

    @Override
    public void tick(GameEngine context) {
        // Move
        this.X += vx;
        this.Y += vy;

        // Bounce off field boundaries
        if (this.X < context.c.MIN_X) {
            this.X = context.c.MIN_X;
            vx = -vx;
        }
        if (this.X > context.c.MAX_X - this.width) {
            this.X = context.c.MAX_X - this.width;
            vx = -vx;
        }
        if (this.Y < context.c.MIN_Y) {
            this.Y = context.c.MIN_Y;
            vy = -vy;
        }
        if (this.Y > context.c.MAX_Y - this.height) {
            this.Y = context.c.MAX_Y - this.height;
            vy = -vy;
        }

        // Check bounce off solid entities in pool (like spawned walls)
        for (Entity e : context.entityPool) {
            if (e != this && e.solid && e.getHealth() > 0.0) {
                if (this.asBounds().intersects(e.asBounds())) {
                    if (e.ballNearestEdgeisX(this)) {
                        vx = -vx;
                        this.X += vx; // step away
                    } else {
                        vy = -vy;
                        this.Y += vy;
                    }
                }
            }
        }

        // Check interaction with players (Titans)
        for (Titan t : context.players) {
            if (t.health > 0.0 && this.asBounds().intersects(t.asBounds())) {
                // Kick the ball
                double angleRad = Math.toRadians(t.facing);
                double speed = context.c.getD("guardian.multiball.speed");
                if (speed <= 0.0) speed = 4.0;
                
                vx = Math.cos(angleRad) * speed;
                vy = Math.sin(angleRad) * speed;

                // Push ball slightly out of intersection
                this.X += vx * 2.0;
                this.Y += vy * 2.0;

                // Deal minor chip damage to enemies
                t.damage(context, 0.05);
            }
        }

        // Check scoring on low goals (sidegoals)
        for (GoalHoop goal : context.lowGoals) {
            if (goal.checkReady() && ballIntersectsGoal(goal)) {
                // Award points
                Team scoringTeam = (goal.team == TeamAffiliation.HOME) ? context.away : context.home;
                scoringTeam.score += 0.25;
                goal.trigger();

                // Reset ball
                this.X = 1040;
                this.Y = 609;
                double angle = rand.nextDouble() * 2 * Math.PI;
                double speed = 3.0;
                vx = Math.cos(angle) * speed;
                vy = Math.sin(angle) * speed;
                break;
            }
        }

        // Check scoring on high goals (center goals)
        for (GoalHoop goal : context.hiGoals) {
            if (goal.checkReady() && ballIntersectsGoal(goal)) {
                Team us, enemy;
                if (goal.team == TeamAffiliation.HOME) {
                    us = context.away;
                    enemy = context.home;
                } else {
                    us = context.home;
                    enemy = context.away;
                }
                goal.trigger();
                
                // Cash in all ghost/combo points for a full point
                long iPart = (long) us.score;
                double fPart = us.score - iPart;
                us.score = Math.floor(us.score);
                us.score += fPart * 4 + 1;
                
                // Reset enemy team ghost points
                boolean saveProgressHi = (enemy == context.home)
                    ? context.homeGoaliePurchasedUpgrades.contains("siege.t5.saveprogress")
                    : context.awayGoaliePurchasedUpgrades.contains("siege.t5.saveprogress");
                if (!saveProgressHi) {
                    enemy.score = Math.floor(enemy.score);
                }
                
                us.hasBall = true;
                enemy.hasBall = false;
                context.ballVisible = false;
                context.inGame = false;
                context.goalVisible = true;
                
                context.checkWinCondition(false);
                context.serverDelayReset();

                // Reset this second ball to the center
                this.X = 1040;
                this.Y = 609;
                double angle = rand.nextDouble() * 2 * Math.PI;
                double speed = 3.0;
                vx = Math.cos(angle) * speed;
                vy = Math.sin(angle) * speed;
                break;
            }
        }
    }

    private boolean ballIntersectsGoal(GoalHoop goal) {
        gameserver.engine.CollisionMath.EllipseData g = goal.ellipseData();
        gameserver.engine.CollisionMath.EllipseData b = new gameserver.engine.CollisionMath.EllipseData(
            this.X + this.width / 2.0,
            this.Y + this.height / 2.0,
            this.width / 2.0,
            this.height / 2.0
        );
        return gameserver.engine.CollisionMath.ellipseBoundsIntersect(b, g);
    }

    @Override
    public void triggerCollide(GameEngine context, Box entity) {
    }
}
