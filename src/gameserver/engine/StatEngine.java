package gameserver.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import gameserver.effects.EffectId;
import gameserver.effects.EffectPool;
import gameserver.effects.effects.Effect;
import gameserver.entity.Titan;
import networking.PlayerDivider;
import org.json.JSONObject;

import java.util.*;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public class StatEngine {

    @JsonProperty
    private List<Map<String, Double>> gamestats;

    public boolean statConditionalMet(PlayerDivider pl, StatEnum category, double threshold){
        Map<String, Double> statmap = gamestats.get(category.index);
        if(statmap.containsKey(pl.email)){
            return statmap.get(pl.email) >= threshold;
        }
        return false;
    }

    public void grant(PlayerDivider pl, StatEnum en, double amount) {
        if (pl != null) {
            Map<String, Double> statmap = gamestats.get(en.index);
            if (statmap != null) {
                double previous = statmap.getOrDefault(pl.email, 0.0);
                gamestats.get(en.index).put(pl.email, previous + amount);
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

    public Map<String, Double> getStat(StatEnum en) {
        return gamestats.get(en.index);
    }

    public JSONObject statsOf(PlayerDivider t) {
        return statsOf(t.email);
    }

    public JSONObject statsOf(String email) {
        JSONObject stats = new JSONObject();
        for (Map<String, Double> category : gamestats) {
            String name = StatEnum.valueOf(gamestats.indexOf(category)).toString();
            if (category.containsKey(email)) {
                stats.put(name, category.get(email));
            } else {
                stats.put(name, 0.0);
            }
        }
        return stats;
    }

    public JSONObject ranksOf(PlayerDivider t) {
        JSONObject ranks = new JSONObject();
        for (Map<String, Double> category : gamestats) {
            String name = StatEnum.valueOf(gamestats.indexOf(category)).toString();
            double actual = category.getOrDefault(t.email, 0.0);
            List<Double> toSort = new ArrayList<>(category.values());
            if (actual == 0.0) {
                toSort.add(actual);
            }
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
        HashMap<String, Double> attackTimeMap = new HashMap();
        for (int i = 0; i < effectPool.getEffects().size(); i++) {
            Effect eff = effectPool.getEffects().get(i);
            Titan castBy = effectPool.getCastBy().get(i);
            //TODO Make sure caster is set by whatever call we do!
            if (castBy == null) {
                continue;
            }
            for (Titan t : context.players) {
                if (castBy != null) {
                    System.out.println("" + castBy.getType().toString());
                    if (eff.on.id.equals(dead.id) &&
                            eff.effect.toString().equals(EffectId.ATTACKED.toString())) {
                        attackTimeMap.put(castBy.id.toString(),
                                eff.getPercentLeft());
                    }
                }
            }
            double mostRecentTime = 0.0;
            String killRecipientId = null;
            double secMostRecent = -1.0;
            String assistRecipientId = null;
            for (String titanKey : attackTimeMap.keySet()) {
                double percent = attackTimeMap.get(titanKey);
                if (percent > mostRecentTime) {
                    assistRecipientId = killRecipientId;
                    killRecipientId = titanKey;
                    secMostRecent = mostRecentTime;
                    mostRecentTime = percent;
                } else if (percent > secMostRecent) {
                    secMostRecent = percent;
                    assistRecipientId = titanKey;
                }
            }
            System.out.println("kr " + killRecipientId);
            System.out.println("ar " + assistRecipientId);

            if (killRecipientId != null) {
                Optional<Titan> killer = context.titanByID(killRecipientId);
                if(killer.isPresent()){
                    grant(context, killer.get(), StatEnum.KILLS);
                }
            }

            if (assistRecipientId != null) {
                Optional<Titan> assister = context.titanByID(killRecipientId);
                if(assister.isPresent()) {
                    grant(context, assister.get(), StatEnum.KILLASSISTS);
                }
            }
        }
    }

    public enum StatEnum {
        GOALS(0), SIDEGOALS(1), POINTS(2),
        STEALS(3), BLOCKS(4), PASSES(5),
        KILLS(6), DEATHS(7), TURNOVERS(8),
        KILLASSISTS(9), GOALASSISTS(10), REBOUND(11);
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
    }

    public StatEngine() {
        reset();
    }
}
