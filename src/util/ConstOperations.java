package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ConstOperations {
    private Map<String, String> cache = new HashMap();
    public ConstOperations(String s) {
        Scanner sc = null;
        try {
            sc = new Scanner(new File(s));
            while(sc.hasNextLine()){
                String line = sc.nextLine();
                String[] parts = line.split("=");
                cache.put(parts[0],parts[1].trim());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        sc.close();
    }

    public String getS(String key){
        return cache.get(key);
    }

    public double getD(String key){
        return Double.parseDouble(getS(key));
    }

    public boolean getB(String key){
        String s = getS(key);
        if(s == null || s.equals("")){
            return false;
        }
        return s.toLowerCase().equals("true");
    }

    public int getI(String key){
        return Integer.parseInt(getS(key));
    }

    public ConstOperations() {//kryo
        this("game.cfg");
    }
}
