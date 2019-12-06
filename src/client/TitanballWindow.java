package client;
/* loginClient.src.TitanballWindow for Java main client
 * Chaosball Alpha testing version
 * */

import client.forms.LoginForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gameserver.gamemanager.GamePhase;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

public class TitanballWindow extends JFrame {
    private static HttpClient loginClient;
    private int xSize = 1920, ySize = 1080;
    private double scl = 1.5;
    private HashMap<String, String> keymap;
    private TitanballClient client = null;
    private boolean darkTheme = false;

    public TitanballWindow(HttpClient loginClient) {
        this.loginClient = loginClient;
        initUI();
    }

    public void reset(boolean menu) throws IOException {
        boolean restarting = false;
        if (client != null) { //reset
            remove(client);
            System.gc();
            restarting = true;
        } else { //initial
            setTitle("Chaosball Alpha");
            setUndecorated(true);
        }
        //reconstruct
        client = new TitanballClient(this, xSize, ySize, scl, loginClient, keymap, !restarting, darkTheme);

        add(client);
        setSize(xSize, ySize);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setResizable(false);
        if(restarting){
            if(menu){
                client.phase = GamePhase.SHOW_GAME_MODES;
                client.initSurface(true); //convoluted, but we can only do this once
            }
        }
    }

    public void toggleFullscreen(boolean fullscreen){
        setResizable(!fullscreen);
        if(!fullscreen){
            setExtendedState(JFrame.NORMAL);
            setSize((int)(1920*.7), (int)(1080*.7));
        }else{
            setExtendedState(JFrame.MAXIMIZED_BOTH);

        }
    }

    private void initUI() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            keymap = mapper.readValue(new File("res/config.yaml"), HashMap.class);
            xSize = Integer.parseInt(keymap.get("Xres").replaceAll("px", ""));
            ySize = Integer.parseInt(keymap.get("Yres").replaceAll("px", ""));
            scl = Double.parseDouble(keymap.get("SCALE").replaceAll("%", "")) / 100.0;
            xSize *= 1.5 / (scl);
            ySize *= 1.5 / (scl);
            String theme = keymap.get("theme");
            if(theme.toLowerCase().equals("dark")){
                this.darkTheme = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            reset(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeRefreshToken(String refreshToken) throws IOException {
        FileOutputStream fio = new FileOutputStream(new File("session.jwt"));
        fio.write(refreshToken.getBytes());
        fio.close();
    }

    public static void main(String[] args) throws IOException {
        loginClient = new HttpClient();
        try {
            Scanner sc = new Scanner(new File("session.jwt"));
            loginClient.refreshToken = sc.nextLine();
            sc.close();
            if (loginClient.refresh(loginClient.refreshToken) != 200) {
                throw new Exception("Refresh token invalid!");
            }
            writeRefreshToken(loginClient.refreshToken);
        } catch (Exception ex) {
            LoginForm authFrame = new LoginForm(loginClient);
            authFrame.setVisible(false);
            authFrame.dispose();
            authFrame = null;
            System.gc();
            writeRefreshToken(loginClient.refreshToken);
        }
        String finalToken = loginClient.token;
        String finalRefreshToken = loginClient.refreshToken;
        System.out.println(finalToken + " bearer");
        System.out.println(finalRefreshToken + " refresh");
        EventQueue.invokeLater(() -> {
            TitanballWindow fr = new TitanballWindow(loginClient);
            fr.setVisible(true);
        });
    }
}

//field locations: Y=618 is center Y
//400 units wide (too short on top as is)

//---------------------------------------------------------------------
