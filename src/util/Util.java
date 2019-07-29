package util;

import org.json.JSONObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.io.*;
import java.util.Base64;

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
}
