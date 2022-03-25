package networking;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CandidateGame  implements Serializable {

    private List<PlayerConnection> home, away;
    private double elogap = Double.MAX_VALUE;

    public void suggestTeams(List<PlayerConnection> home, List<PlayerConnection> away, Map<String, Double>  ratingMap) {
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

    private double avg(List<PlayerConnection> team, Map<String, Double> ratingMap) {
        double avg = 0.0;
        for(PlayerConnection pl : team){
            avg+=ratingMap.get(pl.email);
        }
        return avg/team.size();
    }

    public List<PlayerConnection> bestMonteCarloBalance(List<List<Integer>> availableSlots) {
        ArrayList<PlayerConnection> combined = new ArrayList<>();
        if(this.home != null){
            combined.addAll(this.home);
        }
        if(this.away != null){
            combined.addAll(this.away);
        }
        //print(combined);
        for(int i=0; i<combined.size(); i++){
            System.out.println(combined.get(i).id + "" +combined.get(i).email +" to "+availableSlots.get(i).get(0));
            combined.get(i).setPossibleSelection(availableSlots.get(i));
        }
        return combined;
    }

    public void print(List<PlayerConnection> pairing) {
        for(PlayerConnection pl : pairing){
            System.out.println(pl.email);
        }
    }
}
