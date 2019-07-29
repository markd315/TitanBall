package networking;


import gameserver.engine.Masteries;
import gameserver.entity.TitanType;

import java.io.Serializable;

public class ClientPacket implements Serializable {
    public ClientPacket(){
    }
    public boolean UP = false, LEFT = false, DOWN = false, RIGHT= false;
    public boolean E= false, R= false, CAM= false, STEAL= false, SWITCH= false;
    public boolean BOOST = false;
    public boolean BOOST_LOCK = false;
    public boolean MV_CLICK = false, MV_BALL = false;
    public boolean passBtn = false, shotBtn = false;
    public int posX, posY;
    public int camX, camY;
    public TitanType classSelection;
    public Masteries masteries;
    public String token;
    public String gameID;
}
