package gameserver.entity.minions;

import gameserver.effects.EffectId;
import gameserver.effects.effects.RatioEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.Titan;

import java.io.Serializable;

public class Dragon extends Entity implements Serializable {
    public static final long serialVersionUID = 1L;

    public double homeDamage = 0.0;
    public double awayDamage = 0.0;

    public Dragon() {
        super(TeamAffiliation.UNAFFILIATED);
    }

    public Dragon(int x, int y) {
        super(TeamAffiliation.UNAFFILIATED);
        this.setX(x);
        this.setY(y);
        this.width = 120;
        this.height = 120;
        this.health = 250;
        this.maxHealth = 250;
        this.solid = false;
    }

    @Override
    public void damage(GameEngine context, double amount) {
        TeamAffiliation damageTeam = findNearestPlayerTeam(context);
        double dmgTaken = amount / this.armorRatio;
        
        if (damageTeam == TeamAffiliation.HOME) {
            homeDamage += dmgTaken;
        } else if (damageTeam == TeamAffiliation.AWAY) {
            awayDamage += dmgTaken;
        }
        
        if (homeDamage > 250) homeDamage = 250;
        if (awayDamage > 250) awayDamage = 250;

        this.health = 250.0 - Math.max(homeDamage, awayDamage);
        
        if (this.health <= 0.0) {
            this.health = 0.0;
            TeamAffiliation winningTeam = (homeDamage >= 250.0) ? TeamAffiliation.HOME : TeamAffiliation.AWAY;
            for (Titan t : context.players) {
                if (t.team == winningTeam && t.getType() != null && t.getType() != gameserver.entity.TitanType.GOALIE) {
                    context.effectPool.addUniqueEffect(
                        new RatioEffect(9999999, t, EffectId.FAST, 1.35),
                        context
                    );
                }
            }
        }
    }

    private TeamAffiliation findNearestPlayerTeam(GameEngine context) {
        double minDist = Double.MAX_VALUE;
        TeamAffiliation bestTeam = TeamAffiliation.UNAFFILIATED;
        double centerX = this.X + this.width / 2.0;
        double centerY = this.Y + this.height / 2.0;
        for (Titan t : context.players) {
            if (t.health <= 0.0) continue;
            double d = util.Util.dist(t.X + 35, t.Y + 35, centerX, centerY);
            if (d < minDist) {
                minDist = d;
                bestTeam = t.team;
            }
        }
        return bestTeam;
    }
}
