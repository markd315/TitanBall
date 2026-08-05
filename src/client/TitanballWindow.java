package client;

import client.forms.LoginForm;
import client.forms.LoginListener;
import gameserver.gamemanager.GamePhase;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;


public class TitanballWindow extends Application {
    private static final Logger log = LoggerFactory.getLogger(TitanballWindow.class);
    private static AuthServerInterface loginClient;
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
        boolean restarting = false;
        if (client != null) { //reset
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
        if (restarting) {
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
        scene.setOnMouseDragged(client::handle);
        primaryStage.setScene(scene);
        primaryStage.show();
        startGameLoop();
    }

    private static void writeRefreshToken(String refreshToken) throws IOException {
        FileOutputStream fio = new FileOutputStream(new File("session.jwt"));
        fio.write(refreshToken.getBytes());
        fio.close();
    }

    public static void main(String[] args) {
        loginClient = new AuthServerInterface();

        // Create a CountDownLatch to wait for the login process
        CountDownLatch latch = new CountDownLatch(1);

        // Runnable to save refresh token after login
        Runnable saveRefreshToken = () -> {
            try {
                writeRefreshToken(loginClient.refreshToken);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            latch.countDown();  // Count down to signal that the token is saved and login is complete
        };

        // Create the login listener
        LoginListener loginListener = new LoginListener(loginClient, null, null, null, saveRefreshToken);

        // Try reading the refresh token from a file and refresh if valid
        try {
            Scanner sc = new Scanner(new File("session.jwt"));
            loginClient.refreshToken = sc.nextLine();
            sc.close();
            if (loginClient.refresh(loginClient.refreshToken) != 200) {
                throw new Exception("Refresh token invalid!");
            }
            writeRefreshToken(loginClient.refreshToken);
        } catch (Exception ex) {
            // Show the login form to the user on the JavaFX thread
            Platform.runLater(() -> {
                LoginForm authForm = new LoginForm(loginClient, saveRefreshToken);
                loginListener.setForm(authForm);
            });

            // Wait until the latch is counted down, indicating that the login process is complete
            try {
                latch.await();  // Block until the form is submitted and the refresh token is saved
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // Proceed once login is successful and the refresh token is saved
        String finalToken = loginClient.token;
        String finalRefreshToken = loginClient.refreshToken;
        System.out.println(finalToken + " bearer");
        System.out.println(finalRefreshToken + " refresh");
        launch(args);
    }

}