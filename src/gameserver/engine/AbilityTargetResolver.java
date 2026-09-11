package gameserver.engine;

import gameserver.effects.EffectId;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;

/**
 * Resolves target coordinates (X, Y) for abilities cast by AI or human players.
 */
public class AbilityTargetResolver {

    public static int[] resolveTargetCoords(GameEngine context, Titan caster) {
        if (context == null || caster == null) {
            return new int[]{0, 0};
        }

        int clientIndex = context.clientIndex(caster);
        if (clientIndex >= 0 && context.lastControlPacket != null
                && clientIndex < context.lastControlPacket.length && context.lastControlPacket[clientIndex] != null) {
            int x = context.lastControlPacket[clientIndex].posX + context.lastControlPacket[clientIndex].camX;
            int y = context.lastControlPacket[clientIndex].posY + context.lastControlPacket[clientIndex].camY;
            return new int[]{x, y};
        }

        return resolveAiTargetCoords(context, caster);
    }

    public static int[] resolveAiTargetCoords(GameEngine context, Titan caster) {
        if (caster == null || context == null) {
            return new int[]{0, 0};
        }

        // Check blind status
        if (context.c != null && !context.c.AI_OMNISCIENCE_ENABLED
                && context.effectPool != null && context.effectPool.hasEffect(caster, EffectId.BLIND)) {
            int x = (int) (caster.X + caster.width / 2.0);
            int y = (int) (caster.Y + caster.height / 2.0);
            return new int[]{x, y};
        }

        // Goalie targeting
        if (caster.getType() == TitanType.GOALIE) {
            if (caster.aiTargetX >= 0 && caster.aiTargetY >= 0) {
                return new int[]{(int) caster.aiTargetX, (int) caster.aiTargetY};
            }
            if (context.ball != null) {
                return new int[]{
                    (int) (context.ball.X + context.ball.width / 2.0),
                    (int) (context.ball.Y + context.ball.height / 2.0)
                };
            }
        }

        // 1. If Support, target injured ally or self within range
        if (caster.getType() == TitanType.SUPPORT && context.players != null) {
            Titan mostInjuredAlly = null;
            double lowestHealth = Double.MAX_VALUE;
            for (Titan t : context.players) {
                if (t != null && t.team == caster.team && !context.effectPool.hasEffect(t, EffectId.DEAD)) {
                    if (t.getHealth() < t.maxHealth && t.getHealth() < lowestHealth) {
                        double d = Math.hypot(t.X - caster.X, t.Y - caster.Y);
                        if (d <= 250.0) {
                            lowestHealth = t.getHealth();
                            mostInjuredAlly = t;
                        }
                    }
                }
            }
            if (mostInjuredAlly != null) {
                return new int[]{
                    (int) (mostInjuredAlly.X + mostInjuredAlly.width / 2.0),
                    (int) (mostInjuredAlly.Y + mostInjuredAlly.height / 2.0)
                };
            }
        }

        // 2. Target enemy ball carrier if one exists
        TeamAffiliation enemyTeam = (caster.team == TeamAffiliation.HOME) ? TeamAffiliation.AWAY : TeamAffiliation.HOME;
        java.util.Optional<Titan> possessorOpt = context.titanInPossession();
        if (possessorOpt.isPresent() && possessorOpt.get().team == enemyTeam
                && !context.effectPool.hasEffect(possessorOpt.get(), EffectId.DEAD)) {
            Titan tip = possessorOpt.get();
            if (context.isTitanVisibleTo(caster, tip)) {
                return new int[]{
                    (int) (tip.X + tip.width / 2.0),
                    (int) (tip.Y + tip.height / 2.0)
                };
            }
        }

        // 3. Target nearest alive enemy
        Titan nearestEnemy = context.findNearestEnemy(caster, enemyTeam);
        if (nearestEnemy != null) {
            return new int[]{
                (int) (nearestEnemy.X + nearestEnemy.width / 2.0),
                (int) (nearestEnemy.Y + nearestEnemy.height / 2.0)
            };
        }

        // 4. Fallback to AI target, marching orders, or self center
        if (caster.aiTargetX >= 0 && caster.aiTargetY >= 0) {
            return new int[]{(int) caster.aiTargetX, (int) caster.aiTargetY};
        }

        if (caster.marchingOrderX != 0 || caster.marchingOrderY != 0) {
            return new int[]{caster.marchingOrderX, caster.marchingOrderY};
        }

        return new int[]{
            (int) (caster.X + caster.width / 2.0),
            (int) (caster.Y + caster.height / 2.0)
        };
    }
}
