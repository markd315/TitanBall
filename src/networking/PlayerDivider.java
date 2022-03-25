package networking;

import com.fasterxml.jackson.annotation.JsonProperty;
import gameserver.engine.GameEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PlayerDivider implements Serializable {
    //At some points early in execution, "email" is actually the username
    @JsonProperty
    public String email = "";
    @JsonProperty
    public boolean ready = false;
    @JsonProperty
    public double newRating;
    @JsonProperty
    public int id;
    @JsonProperty
    public int selection; // The current titan selected (index of possibleSelection) since you can change during game
    @JsonProperty
    public List<Integer> possibleSelection = new ArrayList<>();
    @JsonProperty
    public int wasVictorious = 0; // to decide the victory or defeat at the end of a match

    public PlayerDivider(List<Integer> possibleSelection, String email){
        this.email = email;
        this.possibleSelection = possibleSelection;
        selection = possibleSelection.get(0);
    }
    public PlayerDivider(){}

    public int getSelection() {
        return selection;
    }

    public int getId(){return id;}

    public void setSelection(int selection) {
        this.selection = selection;
    }

    public List<Integer> getPossibleSelection() {
        return possibleSelection;
    }

    public void setPossibleSelection(List<Integer> possibleSelection) {
        this.possibleSelection = possibleSelection;
        this.selection = possibleSelection.get(0);
    }

    public void incSel(GameEngine context){
        context.players[this.selection - 1].sel = 0;
        //System.out.println("client updating from " + this.selection);
        int index = possibleSelection.indexOf(this.selection);
        index++;
        if(index >= possibleSelection.size()){
            index = 0;
        }
        selection = possibleSelection.get(index);
        //System.out.println("to " + this.selection);
        context.players[this.selection - 1].sel = 1;
    }

    public void setEmail(String jwtExtractEmail) {
        this.email = jwtExtractEmail;
    }

    public String getEmail(){
        return this.email;
    }
}
