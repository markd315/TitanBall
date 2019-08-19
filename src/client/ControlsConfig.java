package client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import networking.ClientPacket;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ControlsConfig {
    static Map<String, String> keymap = null;

    public boolean mapKeyPress(ClientPacket prior, int newKey){
        return mapKeyPress(prior, ""+newKey);
    }

    //returns if detected, but also updates packet
    public boolean mapKeyPress(ClientPacket prior, String newKey){
        if(keymap.containsKey(newKey)){
            String value = keymap.get(newKey);
            switch(value){
                case "E":
                    prior.E = true;
                    break;
                case "R":
                    prior.R = true;
                    break;
                case "SHOT":
                    prior.shotBtn = true;
                    break;
                case "PASS":
                    prior.passBtn = true;
                    break;
                case "UP":
                    prior.UP = true;
                    break;
                case "DOWN":
                    prior.DOWN = true;
                    break;
                case "LEFT":
                    prior.LEFT = true;
                    break;
                case "RIGHT":
                    prior.RIGHT = true;
                    break;
                case "CAM":
                    prior.CAM = true;
                    break;
                case "STEAL":
                    prior.STEAL = true;
                    break;
                case "SWITCH":
                    prior.SWITCH = true;
                    break;
                case "BOOST":
                    prior.BOOST = true;
                    break;
                case "BOOST_LOCK":
                    prior.BOOST_LOCK = true;
                    break;
                case "MV_CLICK":
                    prior.MV_CLICK= true;
                    break;
                case "MV_BALL":
                    prior.MV_BALL = true;
                    break;
            }
        }
        return false;
    }

    //returns if detected, but also updates packet
    public boolean mapKeyRelease(ClientPacket prior, String newKey){
        if(keymap.containsKey(newKey)){
            String value = keymap.get(newKey);
            switch(value){
                case "E":
                    prior.E = false;
                    break;
                case "R":
                    prior.R = false;
                    break;
                case "SHOT":
                    prior.shotBtn = false;
                    break;
                case "PASS":
                    prior.passBtn = false;
                    break;
                case "UP":
                    prior.UP = false;
                    break;
                case "DOWN":
                    prior.DOWN = false;
                    break;
                case "LEFT":
                    prior.LEFT = false;
                    break;
                case "RIGHT":
                    prior.RIGHT = false;
                    break;
                case "CAM":
                    prior.CAM = false;
                    break;
                case "STEAL":
                    prior.STEAL = false;
                    break;
                case "SWITCH":
                    prior.SWITCH = false;
                    break;
                case "BOOST":
                    prior.BOOST = false;
                    break;
                case "BOOST_LOCK":
                    prior.BOOST_LOCK = false;
                    break;
                case "MV_CLICK":
                    prior.MV_CLICK= false;
                    break;
                case "MV_BALL":
                    prior.MV_BALL = false;
                    break;
            }
        }
        return false;
    }

    public boolean toggleInstructions(String newKey){
        if(keymap.containsKey(newKey)){
            if(keymap.get(newKey).equals("INSTR")){
                return true;
            }
        }
        return false;
    }

    public boolean movKey(String newKey){
        if(keymap.containsKey(newKey)){
            String v = keymap.get(newKey);
            if(v.equals("UP") || v.equals("DOWN") || v.equals("LEFT") || v.equals("RIGHT")){
                return true;
            }
        }
        return false;
    }

    static{
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            keymap = mapper.readValue(new File("keys.yaml"), HashMap.class);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
