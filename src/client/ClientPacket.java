package client;


import gameserver.entity.TitanType;

import java.io.Serializable;

public class ClientPacket implements Serializable {
    public ClientPacket(){
    }

    public boolean W = false, A= false, S= false, D= false, E= false, R= false, SPACE= false, Q= false, Z= false;
    public int btn, posX, posY;
    public int camX, camY;
    public TitanType classSelecton;
    public String token;
    public String gameID;
}
