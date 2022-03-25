package networking;

import com.esotericsoftware.kryonet.Connection;

import java.io.Serializable;
import java.util.List;

public class PlayerConnection extends PlayerDivider  implements Serializable {
    public PlayerConnection(List<Integer> possibleSelection, Connection client, String email){
        super(possibleSelection);
        this.possibleSelection = possibleSelection;
        selection = possibleSelection.get(0);
        this.id = client.getID();
        this.client = client;
        this.email = email;
    }
    public Connection client;
    public PlayerConnection(){}
    public Connection getClient() {
        return client;
    }

    public void setClient(Connection client) {
        this.client = client;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString(){
        return "PC: {" + email + " " + client.getID() + "}";
    }
}
