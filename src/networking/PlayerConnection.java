package networking;


import io.netty.channel.Channel;

import java.io.Serializable;
import java.util.List;

public class PlayerConnection extends PlayerDivider  implements Serializable {
    public PlayerConnection(List<Integer> possibleSelection, Channel client, String email){
        super(possibleSelection);
        this.possibleSelection = possibleSelection;
        selection = possibleSelection.get(0);
        this.id = client.id();
        this.client = client;
        this.email = email;
    }
    public Channel client;
    public PlayerConnection(){}
    public Channel getClient() {
        return client;
    }

    public void setClient(Channel client) {
        this.client = client;
    }

    public void setId(Channel channel) {
        this.id = channel.id();
    }

    @Override
    public String toString(){
        return "PC: {" + email + " " + client.id() + "}";
    }
}
