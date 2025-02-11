package networking;

import com.esotericsoftware.kryonet.Connection;
import gameserver.engine.GameEngine;

import java.util.ArrayList;
import java.util.List;

public class PlayerDivider {
    public String email = "";
    public boolean ready = false;
    public double newRating;

    public PlayerDivider(List<Integer> possibleSelection){
        this.possibleSelection = possibleSelection;
        selection = possibleSelection.get(0);
    }
    public PlayerDivider(){}

    public PlayerDivider(PlayerConnection pc){
        this.possibleSelection = pc.possibleSelection;
        selection = possibleSelection.get(0);
        this.id = pc.id;
        this.email = pc.email;
    }

    public int id;
    public int selection;
    public List<Integer> possibleSelection = new ArrayList<>();
    public int wasVictorious = 0; // to decide the victory or defeat at the end of a match

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

    public void setId(Connection connection) {
        this.id = connection.getID();
    }
}
