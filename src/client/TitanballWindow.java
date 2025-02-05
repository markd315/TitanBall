package client;

import client.forms.LoginForm;
import com.esotericsoftware.kryonet.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gameserver.gamemanager.GamePhase;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;


public class TitanballWindow extends Application {
    private static HttpClient loginClient;
    private int xSize = 1920, ySize = 1080;
    private double scl = 1.5;
    private Map<String, String> keymap;
    private TitanballClient client = null;
    private boolean darkTheme = true;
    private Canvas canvas; // Canvas for rendering

    @Override
    public void start(Stage primaryStage) {
        try {
            initUI(primaryStage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        keymap = new ControlsConfig().keymap;
        client = new TitanballClient(this, xSize, ySize, scl, loginClient, keymap, !restarting, darkTheme);

        client.requestFocus();

        if (client.gameserverConn == null) {
            client.gameserverConn = conn;
        }
        if (restarting) {
            client.openConnection();
            if (menu) {
                client.phase = GamePhase.SHOW_GAME_MODES;
                client.initSurface(); //convoluted, but we can only do this once
            }
        }
    }

    private void startGameLoop() {
        AnimationTimer gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                client.paint(canvas.getGraphicsContext2D()); // Call the custom painting method
            }
        };
        gameLoop.start();
    }

    public void toggleFullscreen(Stage primaryStage, boolean fullscreen) {
        primaryStage.setFullScreen(fullscreen);
    }

    private void initUI(Stage primaryStage) throws IOException {
        primaryStage.setTitle("Titanball Beta");
        primaryStage.setFullScreen(true);

        // Create root pane (Can hold both UI elements and Canvas)
        Pane root = new Pane();

        // Create Canvas for custom drawing
        canvas = new Canvas(xSize, ySize);

        // Add Canvas to the root Pane
        root.getChildren().add(canvas);

        // Set Scene
        Scene scene = new Scene(root, xSize, ySize);

        // Init client and start game loop
        reset(primaryStage, true);
        scene.setOnKeyPressed(client::handle);
        scene.setOnKeyReleased(client::handle);
        scene.setOnMousePressed(client::handle);
        scene.setOnMouseReleased(client::handle);
        scene.setOnMouseMoved(client::handle);
        primaryStage.setScene(scene);
        primaryStage.show();
        startGameLoop();
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