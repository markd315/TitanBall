package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ConstOperations  implements Serializable {

    public static final long serialVersionUID = 1L;
    private Map<String, String> cache = new HashMap();
    public ConstOperations(String s) {
        try (Scanner sc = new Scanner(new File(s))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                
                // Skip empty lines or lines without an equals sign
                if (line == null || line.trim().isEmpty() || !line.contains("=")) {
                    continue;
                }
                
                String[] parts = line.split("=");
                if (parts.length >= 2) {
                    cache.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public String getS(String key){
        return cache.get(key);
    }

    public boolean hasKey(String key){
        String s = getS(key);
        if(s == null || s.equals("")){
            return false;
        }
        return true;
    }

    public double getD(String key){
        String s = getS(key);
        if (s == null || s.trim().isEmpty()) {
            //System.err.println("Looked for null property " + key);
            return 0.0; // Alternatively, throw a custom exception like IllegalArgumentException
        }
        return Double.parseDouble(s);
    }

    public int getI(String key){
        String s = getS(key);
        if (s == null || s.trim().isEmpty()) {
            //System.err.println("Looked for null property " + key);
            return 0; // Alternatively, throw a custom exception
        }
        return Integer.parseInt(s);
    }

    public boolean getB(String key){
        String s = getS(key);
        if(s == null || s.equals("")){
            return false;
        }
        return s.toLowerCase().equals("true");
    }
}
