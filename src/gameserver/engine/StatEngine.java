package gameserver.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gameserver.effects.EffectId;
import gameserver.effects.EffectPool;
import gameserver.effects.effects.Effect;
import gameserver.entity.Titan;
import networking.PlayerDivider;

import com.fasterxml.jackson.annotation.*;
import java.util.*;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StatEngine  {
    private List<Map<String, Double>> gamestats;
    private static final ObjectMapper mapper = new ObjectMapper();

    public boolean statConditionalMet(PlayerDivider pl, StatEnum category, double threshold){
        if (pl == null || pl.email == null || category == null) return false;
        if (gamestats == null || gamestats.size() <= category.index) return false;
        Map<String, Double> statmap = gamestats.get(category.index);
        if(statmap != null && statmap.containsKey(pl.email)){
            return statmap.get(pl.email) >= threshold;
        }
        return false;
    }

    public void grant(PlayerDivider pl, StatEnum en, double amount) {
        if (pl != null && pl.email != null && en != null) {
            if (gamestats == null || gamestats.size() <= en.index) {
                reset();
            }
            Map<String, Double> statmap = gamestats.get(en.index);
            if (statmap != null) {
                double previous = statmap.getOrDefault(pl.email, 0.0);
                statmap.put(pl.email, previous + amount);
                //System.out.println("incremented " + pl.email + " " + en.toString());
            }
        }
    }

    public void grant(PlayerDivider pl, StatEnum en) {
        grant(pl, en, 1.0);
    }

    public void grant(GameEngine context, Titan t, StatEnum en) {
        PlayerDivider pl = context.clientFromTitan(t);
        grant(pl, en, 1.0);
    }

    public void grant(GameEngine context, Titan t, StatEnum en, double amount) {
        PlayerDivider pl = context.clientFromTitan(t);
        grant(pl, en, amount);
    }

    public Map<String, Double> getStat(StatEnum en) {
        return gamestats.get(en.index);
    }

    public ObjectNode statsOf(PlayerDivider t) {
        return statsOf(t.email);
    }

    public ObjectNode statsOf(String email) {
        ObjectNode stats = mapper.createObjectNode();
        for (int i = 0; i < gamestats.size(); i++) {
            Map<String, Double> category = gamestats.get(i);
            String name = StatEnum.valueOf(i).toString();
            stats.put(name, category.getOrDefault(email, 0.0));
        }
        return stats;
    }

    public ObjectNode ranksOf(PlayerDivider t) {
        ObjectNode ranks = mapper.createObjectNode();
        for (int i = 0; i < gamestats.size(); i++) {
            Map<String, Double> category = gamestats.get(i);
            String name = StatEnum.valueOf(i).toString();
            double actual = category.getOrDefault(t.email, 0.0);

            List<Double> toSort = new ArrayList<>(category.values());
            if (actual == 0.0) toSort.add(actual);

            discernAndSortStat(name, toSort);
            int rank = toSort.indexOf(actual) + 1;

            ranks.put(name, rank);
        }
        return ranks;
    }

    private void discernAndSortStat(String statName, List<Double> sort) {
        statName = statName.toLowerCase();
        switch (statName) {
            //Bad stats
            case "deaths":
            case "turnovers":
                //Sort in ascending
                sort.sort((o1, o2) -> (int) (o1 * 500.0 - o2 * 500.0));
                break;
            default:
                sort.sort((o1, o2) -> (int) (o2 * 500.0 - o1 * 500.0));
                break;

        }
        return;
    }

    public void grantKillAssists(GameEngine context, Titan dead, EffectPool effectPool) {
        if (context == null || dead == null) {
            return;
        }
        Map<String, Double> attackTimeMap = new HashMap<>();
        if (effectPool != null && effectPool.getEffects() != null && effectPool.getCastBy() != null) {
            int count = Math.min(effectPool.getEffects().size(), effectPool.getCastBy().size());
            for (int i = 0; i < count; i++) {
                Effect eff = effectPool.getEffects().get(i);
                Titan castBy = effectPool.getCastBy().get(i);
                if (eff == null || castBy == null || eff.on == null || eff.on.id == null) {
                    continue;
                }
                // Must be an attack targeting the dead titan, from an enemy team (not self and not friendly)
                if (eff.on.id.equals(dead.id) &&
                        castBy.team != dead.team &&
                        !castBy.id.equals(dead.id) &&
                        EffectId.ATTACKED.equals(eff.getEffect())) {
                    double percent = eff.getPercentLeft();
                    String casterIdStr = castBy.id.toString();
                    double prev = attackTimeMap.getOrDefault(casterIdStr, -1.0);
                    if (percent > prev) {
                        attackTimeMap.put(casterIdStr, percent);
                    }
                }
            }
        }

        double mostRecentTime = -1.0;
        String killRecipientId = null;
        double secMostRecent = -1.0;
        String assistRecipientId = null;

        for (Map.Entry<String, Double> entry : attackTimeMap.entrySet()) {
            double percent = entry.getValue();
            String titanKey = entry.getKey();
            if (percent > mostRecentTime) {
                secMostRecent = mostRecentTime;
                assistRecipientId = killRecipientId;
                mostRecentTime = percent;
                killRecipientId = titanKey;
            } else if (percent > secMostRecent) {
                secMostRecent = percent;
                assistRecipientId = titanKey;
            }
        }

        // Prioritize dead.lastAttacker if they delivered lethal damage from the enemy team
        if (dead.lastAttacker != null && dead.lastAttacker.team != dead.team && !dead.lastAttacker.id.equals(dead.id)) {
            String lethalAttackerId = dead.lastAttacker.id.toString();
            if (killRecipientId == null) {
                killRecipientId = lethalAttackerId;
            } else if (!lethalAttackerId.equals(killRecipientId)) {
                assistRecipientId = killRecipientId;
                killRecipientId = lethalAttackerId;
            }
        }

        if (killRecipientId != null) {
            Optional<Titan> killer = context.titanByID(killRecipientId);
            if (killer.isPresent()) {
                grant(context, killer.get(), StatEnum.KILLS);
            }
        }

        if (assistRecipientId != null && !assistRecipientId.equals(killRecipientId)) {
            Optional<Titan> assister = context.titanByID(assistRecipientId);
            if (assister.isPresent()) {
                grant(context, assister.get(), StatEnum.KILLASSISTS);
            }
        }

        dead.lastAttacker = null;
        dead.lastAttackerTimeMs = 0L;
    }

    public enum StatEnum {
        GOALS(0), SIDEGOALS(1), POINTS(2),
        STEALS(3), BLOCKS(4), PASSES(5),
        KILLS(6), DEATHS(7), TURNOVERS(8),
        KILLASSISTS(9), GOALASSISTS(10), REBOUND(11),
        SAVES(12), LASTHITS(13), MINIONDAMAGE(14),
        UPGRADESGOLD(15), CONSUMABLESGOLD(16),
        SIDEGOAL_SAVES(17), CENTERGOAL_SAVES(18),
        SIDEGOALS_CONCEDED(19), GOALS_CONCEDED(20),
        MANASPENT(21);
        private final int index;

        private final static Map<Integer, StatEnum> map =
                stream(StatEnum.values()).collect(toMap(leg -> leg.index, leg -> leg));

        StatEnum(final int value) {
            this.index = value;
        }

        public static StatEnum valueOf(int index) {
            return map.get(index);
        }
    }

    public void reset(){
        gamestats = new ArrayList<>();
        Map<String, Double> goals = new HashMap<>();
        Map<String, Double> sidegoals = new HashMap<>();
        Map<String, Double> points = new HashMap<>();
        Map<String, Double> steals = new HashMap<>();
        Map<String, Double> blocks = new HashMap<>();
        Map<String, Double> kills = new HashMap<>();
        Map<String, Double> deaths = new HashMap<>();
        Map<String, Double> passes = new HashMap<>();
        Map<String, Double> turnovers = new HashMap<>();
        Map<String, Double> killassists = new HashMap<>();
        Map<String, Double> goalassists = new HashMap<>();
        Map<String, Double> rebounds = new HashMap<>();
        Map<String, Double> saves = new HashMap<>();
        Map<String, Double> lasthits = new HashMap<>();
        Map<String, Double> miniondamage = new HashMap<>();
        Map<String, Double> upgradesgold = new HashMap<>();
        Map<String, Double> consumablesgold = new HashMap<>();
        Map<String, Double> sidegoalsaves = new HashMap<>();
        Map<String, Double> centergoalsaves = new HashMap<>();
        Map<String, Double> sidegoalsconceded = new HashMap<>();
        Map<String, Double> goalsconceded = new HashMap<>();
        Map<String, Double> manaspent = new HashMap<>();
        gamestats.add(goals);
        gamestats.add(sidegoals);
        gamestats.add(points);
        gamestats.add(steals);
        gamestats.add(blocks);
        gamestats.add(passes);
        gamestats.add(kills);
        gamestats.add(deaths);
        gamestats.add(turnovers);
        gamestats.add(killassists);
        gamestats.add(goalassists);
        gamestats.add(rebounds);
        gamestats.add(saves);
        gamestats.add(lasthits);
        gamestats.add(miniondamage);
        gamestats.add(upgradesgold);
        gamestats.add(consumablesgold);
        gamestats.add(sidegoalsaves);
        gamestats.add(centergoalsaves);
        gamestats.add(sidegoalsconceded);
        gamestats.add(goalsconceded);
        gamestats.add(manaspent);
    }

    public List<Map<String, Double>> getGamestats() {
        return gamestats;
    }

    public StatEngine() {
        reset();
    }
}
