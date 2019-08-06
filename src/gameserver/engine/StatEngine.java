package gameserver.engine;

import gameserver.GameEngine;
import gameserver.entity.Titan;
import networking.PlayerDivider;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public class StatEngine {
    private List<Map<String, Double>> gamestats;

    public void grant(PlayerDivider pl, StatEnum en, double amount){
        if(pl != null) {
            Map<String, Double> statmap = gamestats.get(en.index);
            if(statmap != null) {
                double previous = statmap.getOrDefault(pl.email, 0.0);
                gamestats.get(en.index).put(pl.email, previous + amount);
                //System.out.println("incremented " + pl.email + " " + en.toString());
            }
        }
    }

    public void grant(PlayerDivider pl, StatEnum en){
        grant(pl, en, 1.0);
    }

    public void grant(GameEngine context, Titan t, StatEnum en){
        PlayerDivider pl = context.clientFromTitan(t);
        grant(pl, en, 1.0);
    }

    public Map<String, Double> getStat(StatEnum en){
        return gamestats.get(en.index);
    }

    public JSONObject statsOf(PlayerDivider t){
        return statsOf(t.email);
    }
    public JSONObject statsOf(String email){
        JSONObject stats = new JSONObject();
        for(Map<String, Double> category : gamestats){
            String name = StatEnum.valueOf(gamestats.indexOf(category)).toString();
            if(category.containsKey(email)) {
                stats.put(name, category.get(email));
            }else{
                stats.put(name, 0.0);
            }
        }
        return stats;
    }

    public JSONObject ranksOf(PlayerDivider t){
        JSONObject ranks = new JSONObject();
        for(Map<String, Double> category : gamestats){
            String name = StatEnum.valueOf(gamestats.indexOf(category)).toString();
            double actual = category.getOrDefault(t.email, 0.0);
            List<Double> toSort = new ArrayList<>(category.values());
            if(actual == 0.0){
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
        switch(statName){
            //Bad stats
            case "deaths":
            case "turnovers":
                //Sort in ascending
                sort.sort((o1, o2) -> (int) (o1 *500.0 - o2 *500.0));
                break;
            default:
                sort.sort((o1, o2) -> (int) (o2 *500.0 - o1 *500.0));
                break;

        }
        return;
    }

    public enum StatEnum {
        GOALS(0), SIDEGOALS(1), POINTS(2),
        STEALS(3), BLOCKS(4), PASSES(5),
        KILLS(6), //TODO
        DEATHS(7), TURNOVERS(8);
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

    public StatEngine(){
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
        gamestats.add(goals);
        gamestats.add(sidegoals);
        gamestats.add(points);
        gamestats.add(steals);
        gamestats.add(blocks);
        gamestats.add(passes);
        gamestats.add(kills);
        gamestats.add(deaths);
        gamestats.add(turnovers);
    }
}
