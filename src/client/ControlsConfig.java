package client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gameserver.engine.GameEngine;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import networking.ClientPacket;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static networking.ClientPacket.ARTISAN_SHOT.*;

public class ControlsConfig {

    public ControlsConfig(){
        this(false);
    }
    protected Map<String, String> keymap = null;

    public ControlsConfig(boolean useRtsConfig){
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            if(useRtsConfig) {
                keymap = mapper.readValue(new File("res/ctrls_example_rts.yaml"), HashMap.class);
            }
            else {
                keymap = mapper.readValue(new File("res/config.yaml"), HashMap.class);
            }

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public boolean mapKeyPress(GameEngine game, ClientPacket prior, int newKey, Sound shotSound){
        return mapKeyPress(game, prior, ""+newKey, shotSound);
    }

    //returns if detected, but also updates packet
    public boolean mapKeyPress(GameEngine game, ClientPacket prior, String newKey, Sound shotSound){
        try{ //overrides for number/letters in config file
            int i = Integer.parseInt(newKey);
            if(i >= 48 && i <= 96){
                newKey = "" + (char) i;
            }
        }catch(Exception ex1){}
        if(keymap.containsKey(newKey)){
            String value = keymap.get(newKey);
            switch(value){
                case "E":
                    if(game.underControl.getType() == TitanType.ARTISAN && game.underControl.possession == 1){
                        switch(prior.artisanShot){
                            case LEFT:
                                prior.artisanShot = RIGHT;
                                break;
                            case RIGHT:
                                prior.artisanShot = SHOT;
                                break;
                            case SHOT:
                                prior.artisanShot = LEFT;
                                break;
                        }
                    }
                    prior.E = true;
                    break;
                case "R":
                    prior.R = true;
                    break;
                case "SHOT":
                    Optional<Titan> tip = game.titanInPossession();
                    if(tip.isPresent() && tip.get().id.equals(game.underControl.id)) {
                        shotSound.rewindStart();
                    }
                    prior.shotBtn = true;
                    break;
                case "LOB":
                    tip = game.titanInPossession();
                    if(tip.isPresent() && tip.get().id.equals(game.underControl.id)) {
                        shotSound.rewindStart();
                    }
                    prior.lobBtn = true;
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
        try{ //overrides for number/letters in config file
            int i = Integer.parseInt(newKey);
            if(i >= 48 && i <= 96){
                newKey = "" + (char) i;
            }
        }catch(Exception ex1){}
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
                case "LOB":
                    prior.lobBtn = false;
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
    public boolean fullScreen(String newKey){
        if(keymap.containsKey(newKey)){
            if(keymap.get(newKey).equals("FULL_SC")){
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
}
