package gameserver.engine;


import gameserver.entity.Titan;

import java.util.ArrayList;
import java.util.List;

public class Team {
    public double score;
    public boolean hasBall;
    public TeamAffiliation which;
    List<GoalHoop> toScore = new ArrayList<>();
    List<Titan> players = new ArrayList<>();
    public Team(TeamAffiliation which, double score, Object... playersAndHoops){
        allTeams.add(this);
        this.score = score;
        this.which = which;
        for(Object g : playersAndHoops){
            if(g instanceof GoalHoop){
                toScore.add((GoalHoop) g);
            }
            if(g instanceof Titan){
                players.add((Titan) g);
            }
        }
    }

    public Team readEnemyTeam(){

        for(int i=0; i < allTeams.size(); i++){
            if(!allTeams.get(i).equals(this)){
                return allTeams.get(i);
            }
        }
        return null;
    }

    public static Team getTeamFromAffilitation(TeamAffiliation enumerated){


        for(int i=0; i < allTeams.size(); i++){
            if(allTeams.get(i).which == enumerated){
                return allTeams.get(i);
            }
        }
        return null;
    }

    public static List<Team> allTeams = new ArrayList<>();

    public Team(){
    }
}
