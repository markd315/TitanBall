package networking;


import com.fasterxml.jackson.annotation.JsonProperty;
import gameserver.engine.Masteries;
import gameserver.entity.TitanType;

import java.io.Serializable;

public class ClientPacket implements Serializable {
    public ClientPacket(){
    }

    @JsonProperty
    public boolean UP = false, LEFT = false, DOWN = false, RIGHT= false;
    @JsonProperty
    public boolean E= false, R= false, CAM= false, STEAL= false, SWITCH= false;
    @JsonProperty
    public boolean BOOST = false;
    @JsonProperty
    public boolean BOOST_LOCK = false;
    @JsonProperty
    public boolean MV_CLICK = false, MV_BALL = false;
    @JsonProperty
    public boolean passBtn = false, shotBtn = false;
    @JsonProperty
    public int posX, posY;
    @JsonProperty
    public int camX, camY;
    @JsonProperty
    public TitanType classSelection;
    @JsonProperty
    public Masteries masteries;
    @JsonProperty
    public String token;
    @JsonProperty
    public String gameID;
    public enum ARTISAN_SHOT{
        SHOT,
        RIGHT,
        LEFT
    }
    public ARTISAN_SHOT artisanShot = ARTISAN_SHOT.SHOT;
}
