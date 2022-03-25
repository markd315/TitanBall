package gameserver.engine;


import com.fasterxml.jackson.annotation.JsonProperty;
import gameserver.entity.Titan;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Team implements Serializable {
    @JsonProperty
    public double score;
    @JsonProperty
    public boolean hasBall;
    @JsonProperty
    public TeamAffiliation which;
    @JsonProperty
    List<GoalHoop> toScore = new ArrayList<>();
    @JsonProperty
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
