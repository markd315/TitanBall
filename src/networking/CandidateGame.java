package networking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CandidateGame {

    private List<PlayerDivider> home, away;
    private double elogap = Double.MAX_VALUE;

    public void suggestTeams(List<PlayerDivider> home, List<PlayerDivider> away, Map<PlayerDivider, Double>  ratingMap) {
        double elogap = Math.abs(avg(home, ratingMap) - avg(away, ratingMap));
        System.out.println("HOME");
        print(home);
        System.out.println("AWAY");
        print(away);
        System.out.println(elogap);
        if(elogap < this.elogap || home.size() + away.size() < 2){
            System.out.println("updating optimum match");
            this.elogap = elogap;
            this.home = home;
            this.away = away;
        }
        else{
            System.out.println("not updating");
        }
    }

    private double avg(List<PlayerDivider> team, Map<PlayerDivider, Double> ratingMap) {
        double avg = 0.0;
        for(PlayerDivider pl : team){
            avg+=ratingMap.get(pl);
        }
        return avg/team.size();
    }

    public List<PlayerDivider> bestMonteCarloBalance(List<List<Integer>> availableSlots, Map<PlayerDivider, Double> tempRating) {
        ArrayList<PlayerDivider> combined = new ArrayList<>();
        for(PlayerDivider pl: tempRating.keySet()){
            combined.add(pl);
        }
        int size = tempRating.size();
        final int MAX_MM = 10;
        for(int i=0; i< MAX_MM; i++) {
            Collections.shuffle(combined);
            List<PlayerDivider> tempHome = new ArrayList<>(combined.subList(0, (size + 1) / 2));
            List<PlayerDivider> tempAway = new ArrayList<>(combined.subList((size + 1) / 2, size));
            suggestTeams(tempHome, tempAway, tempRating);
        }
        for(int i=0; i<combined.size(); i++){
            System.out.println(combined.get(i).id + "" +combined.get(i).email +" to "+availableSlots.get(i).get(0));
            combined.get(i).setPossibleSelection(availableSlots.get(i));
        }
        return combined;
    }

    public void print(List<PlayerDivider> pairing) {
        for(PlayerDivider pl : pairing){
            System.out.println(pl.email);
        }
    }
}
