package gameserver.engine;


import gameserver.entity.Titan;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Team  implements Serializable {
    public double score;
    public boolean hasBall;
    public TeamAffiliation which;
    List<GoalHoop> toScore = new ArrayList<>();
    List<Titan> players = new ArrayList<>();
    public Team(TeamAffiliation which, double score, Object... playersAndHoops){
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

    public Team(){
    }
}
