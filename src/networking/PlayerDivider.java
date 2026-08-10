package networking;

import gameserver.engine.GameEngine;
import com.fasterxml.jackson.annotation.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerDivider {
    public String email = "";
    public boolean ready = false;
    public double newRating;
    public int id;
    public int selection;
    public List<Integer> possibleSelection = new ArrayList<>();
    public int wasVictorious = 0;

    public PlayerDivider(List<Integer> possibleSelection) {
        setPossibleSelectionSafe(possibleSelection);
    }
    
    public PlayerDivider() {}
    
    public PlayerDivider(WebSocketPlayerConnection pc) {
        setPossibleSelectionSafe(pc.possibleSelection);
        this.id = pc.id;
        this.email = pc.email;
    }

    public void setPossibleSelectionSafe(List<Integer> list) {
        if (list == null || list.isEmpty()) {
            this.possibleSelection = new ArrayList<>(Collections.singletonList(1));
        } else {
            // Enforce strict 1:1 mapping, but use ArrayList so GameEngine can mutate it
            this.possibleSelection = new ArrayList<>(Collections.singletonList(list.get(0)));
        }
        this.selection = this.possibleSelection.get(0);
    }

    public int getSelection() { return selection; }
    public int getId() { return id; }

    /**
     * Legacy unchecked setter. 
     */
    public void setSelection(int selection) {
        this.selection = selection;
    }

    public List<Integer> getPossibleSelection() {
        return possibleSelection;
    }
    
    public void setPossibleSelection(List<Integer> possibleSelection) {
        setPossibleSelectionSafe(possibleSelection);
    }

    /**
     * Attempts to claim candidateId. With strict 1:1 mapping, players can only 
     * select their assigned titan. Overlaps are impossible by design.
     */
    public boolean setSelectionExclusive(GameEngine context, int candidateId) {
        if (!possibleSelection.contains(candidateId)) {
            return false;
        }
        this.selection = candidateId;
        if (context != null && context.players != null) {
            context.players[this.selection - 1].sel = 1;
        }
        return true;
    }

    /**
     * Cycles selection. As we operate on a strict 1:1 mapping, this is now a no-op.
     */
    public void incSel(GameEngine context) {
        // No-op for strict total mapping.
    }

    private boolean isClaimedByOther(GameEngine context, int candidateId) {
        return false; // Total mapping guarantees no overlaps.
    }

    public void setEmail(String jwtExtractEmail) {
        this.email = jwtExtractEmail;
    }
    public String getEmail() {
        return this.email == null ? "" : this.email;
    }
    public void setId(int id) {
        this.id = id;
    }
}