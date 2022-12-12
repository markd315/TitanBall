package networking;

import io.socket.socketio.server.SocketIoSocket;

import java.io.Serializable;
import java.util.List;

public class PlayerConnection extends PlayerDivider  implements Serializable {
    public PlayerConnection(List<Integer> possibleSelection, SocketIoSocket client, String email){
        super(possibleSelection);
        this.possibleSelection = possibleSelection;
        selection = possibleSelection.get(0);
        this.client = client;
        this.email = email;
    }
    public SocketIoSocket client;
    public PlayerConnection(){}
    public SocketIoSocket getClient() {
        return client;
    }

    public void setClient(SocketIoSocket client) {
        this.client = client;
    }


    @Override
    public String toString(){
        return "PC: {" + email + " " + client + "}";
    }
}
