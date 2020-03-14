package client;
/* loginClient.src.TitanballWindow for Java main client
 * Chaosball Alpha testing version
 * */

import client.forms.LoginForm;
import com.esotericsoftware.kryonet.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

public class TitanballWindow extends JFrame {
    private static HttpClient loginClient;
    private int xSize, ySize;
    private HashMap<String, String> keymap;
    private TitanballClient client = null;

    public TitanballWindow(HttpClient loginClient) {
        this.loginClient = loginClient;
        initUI();
    }

    public void reset(boolean menu) throws IOException {
        Client conn = null;
        boolean restarting = false;
        if (client != null) { //reset
            conn = client.gameserverConn;
            remove(client);
            System.gc();
            restarting = true;
        } else { //initial
            setTitle("Chaosball Alpha");
            setUndecorated(true);
        }
        //reconstruct
        client = new TitanballClient(this, xSize, ySize, loginClient, keymap, !restarting);

        add(client);

        //GraphicsConfiguration config = this.getGraphicsConfiguration();
        //Rectangle usableBounds = SunGraphicsEnvironment.getUsableBounds(config.getDevice());
        setSize(xSize, ySize);
        //setAlwaysOnTop(true);
        //setOpaque(false);
        //getContentPane().add(BorderLayout.CENTER, this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setResizable(false);
        if (client.gameserverConn == null) {
            client.gameserverConn = conn;
        }
        if(restarting){
            client.openConnection();
            if(menu){
                client.phase = 15;
                client.initSurface(true); //convoluted, but we can only do this once
            }
        }
    }

    private void initUI() {
        xSize = 1920; //sane defaults
        ySize = 1080;
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            keymap = mapper.readValue(new File("res/config.yaml"), HashMap.class);
            xSize = Integer.parseInt(keymap.get("Xres").replaceAll("px", ""));
            ySize = Integer.parseInt(keymap.get("Yres").replaceAll("px", ""));
            double scl = Double.parseDouble(keymap.get("SCALE").replaceAll("%", ""));
            xSize *= 1.5 / (scl / 100.0);
            ySize *= 1.5 / (scl / 100.0);
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
