package util;

import gameserver.engine.GoalHoop;
import gameserver.entity.Entity;
import org.json.JSONObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.awt.geom.Point2D;
import java.io.*;
import java.util.Base64;
import java.util.Random;

public class Util {

    public static double typesafeNumeric(Object o){
        if(o instanceof Integer){
            return ((Integer) o).doubleValue();
        }
        if(o instanceof Double){
            return ((Double) o);
        }
        else throw new IllegalArgumentException("Unrecognized numeric type");
    }

    public static double dist(
            double x1,
            double y1,
            double x2,
            double y2) {
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }

    public static double degreesFromCoords(double xClick, double yClick) {
        if(xClick == 0){
            xClick = 1; //error handling for DIV/0 stuff
        }
        if(yClick == 0){
            yClick = 1;
        }
        double add = 0.0;
        if(xClick < 0.0){
            add = 180;
        }
        if(yClick < 0.0 && xClick > 0.0){
            add = 360;
        }
        return java.lang.Math.toDegrees(java.lang.Math.atan(yClick/xClick)) + add;
    }

    public static boolean isRole(Authentication auth, String role) {
        for(GrantedAuthority r : auth.getAuthorities()){
            if(r.getAuthority().equals(role)){
                return true;
            }
        }
        return false;
    }

    public static String jwtExtractEmail(String token) {
        try {
            String body = token.split("\\.")[1];
            String json = new String(Base64.getDecoder().decode(body.getBytes()));
            JSONObject j = new JSONObject(json);
            String ret = (String) j.get("sub");
            return ret;
        }catch (Exception e){
            return "error";//especially for the first tick
        }
    }

    public static void writeLog(String jwtExtractEmail) {
        File file = new File("log.out");
        if(!file.isFile()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        OutputStream f = null;
        try {
            f = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            f.write(Util.jwtExtractEmail(jwtExtractEmail).getBytes());
            f.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String randomKey(){
        Random rng = new Random();
        String s = "";
        for(int i=0; i<10; i++){
            char c = (char) (65+ rng.nextInt(26));
            s+=c;
        }
        return s;
    }

    public static double calculatePain(Entity e, GoalHoop pain) {
        int d = (int) Point2D.distance(e.X + 35, e.Y + 35,
                pain.x + (pain.w / 2.0),
                pain.y + (pain.h / 2.0));
        return (-3.96893 / 100000000.0) * Math.pow(d, 3) // e-8
                + Math.pow(d, 2) * 0.0000603779
                - (0.0326137) * d
                + 6.92514;
        //0 at 710 distance
        //https://www.wolframalpha.com/input/?i=model+cubic&assumption=%7B%22F%22%2C+%22CubicFitCalculator%22%2C+%22data2%22%7D+-%3E%22%7B%7B1000%2C+-5%7D%2C+%7B400%2C1%7D%2C+%7B200%2C+2.5%7D%2C+%7B30%2C+6%7D%7D%22
    }
}
