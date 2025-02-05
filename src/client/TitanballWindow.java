package client;

import client.forms.LoginForm;
import com.esotericsoftware.kryonet.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gameserver.gamemanager.GamePhase;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

public class TitanballWindow extends Application {
    private static HttpClient loginClient;
    private int xSize = 1920, ySize = 1080;
    private double scl = 1.5;
    private HashMap<String, String> keymap;
    private TitanballClient client = null;
    private boolean darkTheme = false;

    @Override
    public void start(Stage primaryStage) {
        this.loginClient = loginClient;
        initUI(primaryStage);
    }

    public void reset(Stage primaryStage, boolean menu) throws IOException {
        Client conn = null;
        boolean restarting = false;
        if (client != null) { //reset
            conn = client.gameserverConn;
            client = null;
            restarting = true;
        } else { //initial
            primaryStage.setTitle("Titanball Beta");
            primaryStage.setFullScreen(true);
            primaryStage.show();
        }
        //reconstruct
        client = new TitanballClient(this, xSize, ySize, scl, loginClient, keymap, !restarting, darkTheme);


        BorderPane root = new BorderPane();
        root.setCenter(client);
        primaryStage.setScene(new Scene(root, xSize, ySize));

        if (client.gameserverConn == null) {
            client.gameserverConn = conn;
        }
        if (restarting) {
            client.openConnection();
            if (menu) {
                client.phase = GamePhase.SHOW_GAME_MODES;
                client.initSurface(true); //convoluted, but we can only do this once
            }
        }
    }

    public void toggleFullscreen(Stage primaryStage, boolean fullscreen) {
        primaryStage.setFullScreen(fullscreen);
    }

    private void initUI(Stage primaryStage) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            keymap = mapper.readValue(new File("res/config.yaml"), HashMap.class);
            xSize = Integer.parseInt(keymap.get("Xres").replaceAll("px", ""));
            ySize = Integer.parseInt(keymap.get("Yres").replaceAll("px", ""));
            scl = Double.parseDouble(keymap.get("SCALE").replaceAll("%", "")) / 100.0;
            xSize *= 1.5 / (scl);
            ySize *= 1.5 / (scl);
            String theme = keymap.get("theme");
            if (theme.toLowerCase().equals("dark")) {
                this.darkTheme = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            reset(primaryStage, true);
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
            writeRefreshToken(loginClient.refreshToken);
        }
        String finalToken = loginClient.token;
        String finalRefreshToken = loginClient.refreshToken;
        System.out.println(finalToken + " bearer");
        System.out.println(finalRefreshToken + " refresh");
        launch(args);
    }
}