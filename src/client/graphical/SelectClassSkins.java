package client.graphical;

import gameserver.entity.TitanType;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

public class SelectClassSkins extends Images {
	int cursor =0;
	int frame=0;
	static HashMap<String, Image> cache = new HashMap<>();
	public SelectClassSkins(int cursor, int frame) {
		this.cursor = cursor;
		this.frame = frame;
		initTeam();
	}

    public static Image pullImage(TitanType type, int frame){
        return pullImage(type, frame, 70, 70);
    }

	public static Image pullImage(TitanType type, int frame, int x, int y){
	    int cursor = 0;
        if(type == TitanType.GOALIE){
            cursor = 1;
        }
        if(type == TitanType.WARRIOR){
            cursor = 2;
        }
	    if(type == TitanType.RANGER){
	        cursor = 3;
        }
        if(type == TitanType.DASHER){
            cursor = 4;
        }
        if(type == TitanType.MARKSMAN){
            cursor = 5;
        }
        if(type == TitanType.ARTISAN){
            cursor = 6;
        }
        if(type == TitanType.SUPPORT){
            cursor = 7;
        }
        if(type == TitanType.STEALTH){
            cursor = 8;
        }
        if(type == TitanType.GOLEM){
            cursor = 9;
        }
        if(type == TitanType.MAGE){
            cursor = 10;
        }
        if(type == TitanType.BUILDER){
            cursor = 11;
        }
        String imageKey = decodeImage(cursor, frame);
        if (cache.containsKey(imageKey)){
            return cache.get(imageKey);
        }
        ImageIcon rsi = new ImageIcon(imageKey);
        Image im = rsi.getImage();
        return getBufferedFrom(im, x, y);
    }

	public static String decodeImage(int cursor, int frame){
        String sprite = "", frameCode = "";
        switch(frame){
            case 1:
                frameCode = "standR";
                break;
            case 2:
                frameCode = "runAR";
                break;
            case 3:
                frameCode = "runBR";
                break;
            case 4:
                frameCode = "passR";
                break;
            case 5:
                frameCode = "atk1R";
                break;
            case 6:
                frameCode = "atk2R";
                break;
            case 8:
                frameCode = "shotR";
                break;
            case 9:
                frameCode = "standL";
                break;
            case 10:
                frameCode = "runAL";
                break;
            case 11:
                frameCode = "runBL";
                break;
            case 12:
                frameCode = "passL";
                break;
            case 13:
                frameCode = "atk1L";
                break;
            case 14:
                frameCode = "atk2L";
                break;
            case 15:
                frameCode = "dieL";
                break;
            case 16:
                frameCode = "shotL";
                break;
            case 17:
                frameCode = "standR";//these two are for goalie
                break;
            case 18:
                frameCode = "standL";
                break;
        }
        switch(cursor){
            case 1:
                sprite = "Guardian";
                break;
            case 2:
                sprite = "Warrior";
                break;
            case 3:
                sprite = "Ranger";
                break;
            case 4:
                sprite = "Slasher";
                break;
            case 5:
                sprite = "Marksman";
                break;
            case 6:
                sprite = "Artisan";
                break;
            case 7:
                sprite = "Support";
                break;
            case 8:
                sprite = "Stealth";
                break;
            case 9:
                sprite = "Post";
                break;
            case 10:
                sprite = "Mage";
                break;
            case 11:
            default:
                sprite = "Builder";
                break;
        }
        String fileName = "res/Sprite"+sprite+"/"+frameCode+".png";
        return fileName;
    }

	public void initTeam() {
        //decode cursor
        String fileName = decodeImage(cursor, frame);
        loadImage (fileName);
	}

}
			
		
	
	
	
