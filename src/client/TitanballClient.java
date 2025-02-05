package client;

import client.graphical.*;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.exceptions.UnirestException;
import gameserver.Const;
import gameserver.TutorialOverrides;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.cooldowns.CooldownW;
import gameserver.effects.effects.*;
import gameserver.engine.*;
import gameserver.entity.Entity;
import gameserver.entity.RangeCircle;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.*;
import gameserver.models.Game;
import gameserver.targeting.ShapePayload;
import gameserver.gamemanager.GamePhase;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;
import networking.ClientPacket;
import networking.KryoRegistry;
import networking.PlayerDivider;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.json.JSONObject;
import util.Util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TitanballClient extends Pane implements EventHandler<KeyEvent> {
    protected final ScreenConst sconst;
    private Canvas canvas = new Canvas();
    private int RANGE_SIZE = 0;
    private int SHOT_WIDTH = 0;
    public ClientPacket controlsHeld = new ClientPacket();
    public Instant gamestart = null;
    public Random rand;
    public Sound shotSound;
    protected Client gameserverConn = new Client(1024 * 1024, 256 * 1024); // 1mb and 256k
    protected String gameID;
    protected GameEngine game;
    protected GamePhase phase = GamePhase.CREDITS;
    protected Kryo kryo = gameserverConn.getKryo();
    protected boolean camFollow = true;
    protected String token, refresh;
    protected HttpClient loginClient;
    protected boolean instructionToggle = false;
    protected int staticFrame = 0, staticFrameCounter = 0;
    protected Masteries masteries;
    protected String bottomText = "";
    protected int masteriesIndex = 0;
    protected String tournamentCode = "";
    int debugCamera = 0;
    int cursor = 1; // For deciding classes and everything else
    int camX = 0;
    int camY = 0;
    int round = 1;
    int ballFrame = 0;
    int ballFrameCounter = 0;
    File shotSoundFile = new File("res/Sound/shotsound.wav");
    //File t4File = new File("res/Sound/tut4.wav");
    int xSize, ySize;
    Image intro;
    Image ballTexture;
    Image ballBTexture;
    Image ballFTexture;
    Image ballFBTexture;
    Image ballLobTexture;
    Image ballPtr;
    Image ballFPtr;
    ControlsConfig controlsConfig = new ControlsConfig();
    Image select;
    Image lobby;
    Image classCursor;
    Image field;
    Image selector;
    Image goalScored;
    Image victory;
    Image defeat;
    Image backdrop;
    Image tie;
    Image tutorial;
    Image trap1;
    Image trap2;
    Image portal1;
    Image portal2;
    Image portalcd;
    Image bportal1;
    Image bportal2;
    Image bportalcd;
    Image fire1;
    Image fire2;
    Image cage;
    Image wall;
    Image wolf1L;
    Image wolf2L;
    Image wolf3L;
    Image wolf5L;
    Image wolf1R;
    Image wolf2R;
    Image wolf3R;
    Image wolf5R;
    private boolean diskLoadMasteries = false;
    private Map<String, Map<String, Integer>> masteriesMap;
    private TutorialOverrides tut;
    private boolean codeHide = false;
    private GameOptions tourneyOptions = new GameOptions();
    private int tourneyIndex = 0;
    private boolean fullScreen = true;
    private double scl;
    private boolean darkTheme = false;
    private boolean queued = false;
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
    private TeamAffiliation saved_team;
    private PlayerDivider saved_player_divider;

    public TitanballClient(TitanballWindow titanballWindow, int xSize, int ySize, double scl, HttpClient loginClient, Map<String, String> keymap, boolean createListeners, boolean darkTheme) {
        this.darkTheme = darkTheme;
        //this.parentWindow = titanballWindow;
        this.xSize = xSize;
        this.ySize = ySize;
        this.scl = scl;
        RANGE_SIZE = Integer.parseInt(keymap.get("rangewidth").replaceAll("px", ""));
        SHOT_WIDTH = Integer.parseInt(keymap.get("shotwidth").replaceAll("px", ""));
        sconst = new ScreenConst(xSize, ySize);
        this.loginClient = loginClient;
        intro = sconst.loadImage("res/Court/logo2.png");
        ballTexture = sconst.loadImage("res/Court/ballA.png");
        ballBTexture = sconst.loadImage("res/Court/ballB.png");
        Image tmp = getScaledImage(ballTexture, 30, 30);
        ballLobTexture = getScaledImage(tmp, 45, 45);
        ballFTexture = sconst.loadImage("res/Court/ballFA.png");
        ballFBTexture = sconst.loadImage("res/Court/ballFB.png");
        ballPtr = sconst.loadImage("res/Court/ballptr.png");
        ballFPtr = sconst.loadImage("res/Court/ballfptr.png");
        select = sconst.loadImage("res/Court/selectClass.png");
        lobby = sconst.loadImage("res/Court/lobby.png");
        field = sconst.loadImage("res/Court/field.png");
        selector = sconst.loadImage("res/Court/select.png");
        backdrop = sconst.loadImage("res/Court/backdrop.png");
        goalScored = sconst.loadImage("res/Court/goal.png");
        victory = sconst.loadImage("res/Court/victory.png");
        tie = sconst.loadImage("res/Court/tie.png");
        defeat = sconst.loadImage("res/Court/defeat.png");
        if(darkTheme) {
            tutorial = sconst.loadImage("res/Court/tutorial_whitetxt.png");
        } else {
            tutorial = sconst.loadImage("res/Court/tutorial.png");
        }
        wall = sconst.loadImage("res/Court/wall.png");
        portal1 = sconst.loadImage("res/Court/portal.png");
        portal2 = sconst.loadImage("res/Court/portal2.png");
        portalcd = sconst.loadImage("res/Court/portalcd.png");

        bportal1 = sconst.loadImage("res/Court/ballp.png");
        bportal2 = sconst.loadImage("res/Court/ballp2.png");
        bportalcd = sconst.loadImage("res/Court/ballpcd.png");

        fire1 = sconst.loadImage("res/Court/fireA.png");
        fire2 = sconst.loadImage("res/Court/fireB.png");
        cage = sconst.loadImage("res/Court/caged.png");

        trap1 = sconst.loadImage("res/Court/trap.png");
        trap2 = sconst.loadImage("res/Court/trap2.png");

        wolf1L = sconst.loadImage("res/Wolf/wolfL.png");
        wolf2L = sconst.loadImage("res/Wolf/wolf2L.png");
        wolf3L = sconst.loadImage("res/Wolf/wolf3L.png");
        wolf5L = sconst.loadImage("res/Wolf/wolf5L.png");
        wolf1R = sconst.loadImage("res/Wolf/wolfR.png");
        wolf2R = sconst.loadImage("res/Wolf/wolf2R.png");
        wolf3R = sconst.loadImage("res/Wolf/wolf3R.png");
        wolf5R = sconst.loadImage("res/Wolf/wolf5R.png");

        initSurface(createListeners);
        requestFocus();
        //requestFocusInWindow();
        //setLayout(new BorderLayout());
        System.out.println("creating new listeners");
        createListeners();
        fireReconnect();
        paint(canvas.getGraphicsContext2D());
    }

    Image getScaledImage(Image srcImg, int width, int height) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        sconst.drawImage(gc,srcImg, 0, 0, width, height);
        WritableImage scaledImage = new WritableImage(width, height);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        canvas.snapshot(params, scaledImage);
        return scaledImage;
    }

    private void createListeners() {
        // Handle mouse dragged and mouse moved events
        EventHandler<MouseEvent> mouseMvtHandler = e -> {
            controlsHeld.posX = sconst.invertMouseX((int) e.getX());
            controlsHeld.posY = sconst.invertMouseY((int) e.getY());
            controlsHeld.camX = camX;
            controlsHeld.camY = camY;
        };

        // Handle mouse pressed and mouse released events
        EventHandler<MouseEvent> mousePressedHandler = event -> {
            if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
                controlsHeld.posX = sconst.invertMouseX((int) event.getX());
                controlsHeld.posY = sconst.invertMouseY((int) event.getY());
                if (event.isPrimaryButtonDown()) {
                    controlsConfig.mapKeyPress(game, controlsHeld, "LMB", shotSound);
                }
                if (event.isSecondaryButtonDown()) {
                    controlsConfig.mapKeyPress(game, controlsHeld, "RMB", shotSound);
                }
                controlsHeld.camX = camX;
                controlsHeld.camY = camY;
            }
        };

        EventHandler<MouseEvent> mouseReleasedHandler = e -> {
            controlsConfig.mapKeyRelease(controlsHeld, "LMB");
            controlsConfig.mapKeyRelease(controlsHeld, "RMB");
        };

        // Add listeners to the JavaFX Pane (this)
        setOnMouseDragged(mouseMvtHandler);
        setOnMouseMoved(mouseMvtHandler);
        setOnMousePressed(mousePressedHandler);
        setOnMouseReleased(mouseReleasedHandler);

        // Example of adding a listener to a JavaFX Button
        Button button = new Button("Click me!");
        button.setOnMousePressed(mousePressedHandler);
        button.setOnMouseReleased(mouseReleasedHandler);

        // Add the button to the JavaFX Pane
        getChildren().add(button);
}

    public int indexSelected() {
        for (int i = 0; i < game.players.length; i++) {
            if (game.players[i].id.equals(game.underControl.id)) {
                return i + 1;
            }
        }
        return -1;
    }

    public void darkTheme(GraphicsContext gc) {
        if (darkTheme) {
            gc.setFill(Color.BLACK);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        } else {
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        }
    }


    private void paint(GraphicsContext gc) {
        // Clear the canvas
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (game != null && game.ended) {
            game.phase = GamePhase.ENDED;
            if (!exec.isShutdown()) {
                List<Runnable> canceled = exec.shutdownNow();
                System.out.println("cancelled " + canceled.size() + " tasks at end of game");
                exec = Executors.newScheduledThreadPool(1);
            }
            darkTheme(gc);

            Team team;
            Team enemy;
            try {
                team = teamFromUnderControl();
                enemy = enemyFromUnderControl();
            } catch (NullPointerException ex1) {
                System.out.println("Started receving dead packets, because the game is over");
            }

            if (queued) {
                try {
                    loginClient.retry401Catch503("leave", null);
                } catch (UnirestException e) {
                    // This is fine, server is in shutdown state so we have already been dequeued
                }
                queued = false;
            }
            if (saved_team == TeamAffiliation.HOME){
                team = game.home;
                enemy = game.away;
            }
            else  {
                team = game.away;
                enemy = game.home;
            }
            if (team.score > enemy.score) {
                sconst.drawImage(gc, victory, sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y);
            } else if (team.score == enemy.score) {
                sconst.drawImage(gc, tie, sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y);
            } else {
                sconst.drawImage(gc, defeat, sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y);
            }
            gameEndStatsAndRanks(gc, saved_player_divider);
            game.phase = GamePhase.ENDED;
            return;
        }
        if (phase == GamePhase.CREDITS) creditPanel(gc);
        if (phase == GamePhase.CONTROLS) tutorial(gc);
        if (phase == GamePhase.SHOW_GAME_MODES) showGameModes(gc);
        if (phase == GamePhase.DRAW_CLASS_SCREEN) drawClassScreen(gc, true); //Attack screen settings starting here!
        if (phase == GamePhase.SET_MASTERIES) {
            if (tournamentCode.equals("")) {
                tourneyOptions = new GameOptions();
                tournamentCode = tourneyOptions.toString();
            }
            if (masteries == null) {
                masteries = new Masteries();
            }
            if (masteriesMap == null) {
                masteriesMap = new HashMap<>();
            }
            if (!diskLoadMasteries) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    File file = new File("res/masteries.json");
                    masteriesMap = mapper.readValue(file, Map.class);
                    Map<String, Integer> json = masteriesMap.get(controlsHeld.classSelection.toString());
                    masteries = new Masteries(json);
                } catch (Exception ex1) {
                    ex1.printStackTrace();
                    this.masteries = new Masteries();
                }
            }
            diskLoadMasteries = true;
            //System.out.println(tournamentCode);
            drawSetMasteries(gc);
        }
        if (phase == GamePhase.TRANSITIONAL) {
            consumeCursorSelectClasses();
            try {
                clientInitialize();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (phase == GamePhase.WAIT_FOR_GAME) {
            try {
                gameID = requestOrQueueGame();
                lobby(gc);
                System.out.println("got game " + gameID);
            } catch (Exception e) {
                //Server is shutting down, not accepting new games
                phase = GamePhase.CANNOT_JOIN;
                e.printStackTrace();
            }
        }
        if (phase == GamePhase.CANNOT_JOIN) {
            cannotJoin(gc);
        }
        if (phase == GamePhase.COUNTDOWN) {
            starting(gc);
        }
        if (phase == GamePhase.DRAFT_HOMEBAN || phase == GamePhase.DRAFT_AWAYBAN ||
                phase == GamePhase.DRAFT_HOMEMID || phase == GamePhase.DRAFT_AWAYMID ||
                phase == GamePhase.DRAFT_HOMETOP || phase == GamePhase.DRAFT_AWAYTOP ||
                phase == GamePhase.DRAFT_HOMEBOT || phase == GamePhase.DRAFT_AWAYBOT) {
            draftClassScreen(gc);
        }
        if (phase == GamePhase.INGAME || phase == GamePhase.SCORE_FREEZE) {
            boolean over = game.checkWinCondition(true);
            if (over) {
                phase = GamePhase.ENDED;
                return;
            }
            else {
                if (this.game.effectPool.hasEffect(this.game.underControl, EffectId.BLIND)) {
                    sconst.setFont(gc, new Font("Verdana", 72));
                    gc.setStroke(Color.DARKGRAY);
                    sconst.drawString(gc, "Blind!", 450, 300);
                } else {
                    updateFrameBall();
                    doDrawing(gc); // do drawing of screen
                    displayBallArrow(gc);
                    displayScore(gc); // call the method to display the game score
                    if (game.phase == GamePhase.INGAME) { //recheck in case new packet came in
                        saved_team = game.underControl.team;
                        saved_player_divider = game.clientFromTitan(game.underControl);
                    }
                }
            }
        }
        if (phase == GamePhase.TUTORIAL_START) {
            //doesn't work for looping tut?
            tut = new TutorialOverrides();
            try {
                tut.detectAndUpdateState();
                tut.clients = Collections.singletonList(tut.client);
                tut.initializeServer();
                tut.gameTick();
                tut.processClientPacket(tut.client, controlsHeld);
                tut.inGame = true;
                tut.underControl = tut.players[2];
                Const c = new Const("res/game.cfg");
                ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
                TerminableExecutor terminableExecutor = new TerminableExecutor(tut, exec);
                exec.scheduleAtFixedRate(terminableExecutor, 0, c.GAMETICK_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.game = tut;
            phase = GamePhase.TUTORIAL;
        }
        if (phase == GamePhase.TUTORIAL) {
            this.game = tut;
            
            updateFrameBall();
            updateNarration(tut);
            if (this.game.phase == GamePhase.SHOW_GAME_MODES) { //finish tutorial
                //parentWindow.reset(true);
                controlsHeld.classSelection = null;
            } else {
                doDrawing(gc);
                displayBallArrow(gc);
                displayScore(gc); // call the method to display the game score
            }
        }
        if (phase == GamePhase.TOURNAMENT_CODE) {
            drawTournamentScreen(gc);
        }
        if (phase == GamePhase.TEAM_LAUNCH) {
            drawTeamScreen(gc);
        }
    }
    static File t0File = new File("res/Sound/tut0.wav");
    static File t1File = new File("res/Sound/tut1.wav");
    static File t2File = new File("res/Sound/tut2.wav");
    static File t3File = new File("res/Sound/tut3.wav");
    static File t4File = new File("res/Sound/tut4.wav");
    static Sound tut0 = new Sound();
    static Sound tut1 = new Sound();
    static Sound tut2 = new Sound();
    static Sound tut3 = new Sound();
    static Sound tut4 = new Sound();

    static {
        tut0.loadSound(t0File);
        tut1.loadSound(t1File);
        tut2.loadSound(t2File);
        tut3.loadSound(t3File);
        tut4.loadSound(t4File);
    }

    private void updateNarration(TutorialOverrides game) {
        if (game.tutorialPhase == 1 && game.narrationPhase == 0) {
            tut0.rewindStart();
            game.narrationPhase++;
        }
        if (game.tutorialPhase == 2 && game.narrationPhase == 1) {
            tut0.rewindStop();
            tut1.rewindStart();
            game.narrationPhase++;
        }
        if (game.tutorialPhase == 3 && game.narrationPhase == 2) {
            tut1.rewindStop();
            tut2.rewindStart();
            game.narrationPhase++;
        }
        if (game.tutorialPhase == 4 && game.narrationPhase == 3) {
            tut2.rewindStop();
            tut3.rewindStart();
            game.narrationPhase++;
        }
        if (game.tutorialPhase == 5 && game.narrationPhase == 4) {
            tut3.rewindStop();
            tut4.rewindStart();
            game.narrationPhase++;
        }
    }

    protected void drawSetMasteries(GraphicsContext gc) {
        gc.setStroke(Color.BLACK);
        darkTheme(gc);
        sconst.setFont(gc, new Font("Verdana", 32));
        sconst.drawString(gc, "Setting Masteries", 200, 50);
        sconst.setFont(gc, new Font("Verdana", 16));
        sconst.drawString(gc, "Remaining Points", 200, 90);
        sconst.drawString(gc, "WASD or arrows to spend points", 200, 420);
        sconst.drawString(gc, "Space to enter (And queue for game)", 200, 450);
        int x = 430;
        for (int i = masteries.validate(); i > 0; i--) {
            Ellipse ellipse = new Ellipse(x, 76, 16, 16);
            sconst.fill(gc, ellipse);
            x += 32;
        }
        int y = 150;
        int[] arr = masteries.asArray();
        for (int i = 0; i < arr.length; i++) {
            x = 250;
            gc.setStroke(Color.BLACK);
            darkTheme(gc);
            sconst.drawString(gc, Masteries.masteryFromIndex(i), x, y);
            if (masteriesIndex == i) {
                gc.setStroke(Color.RED);
                sconst.fill(gc, new Rectangle(x - 30, y - 14, 16, 16));
            }
            x = 430;
            for (int ballNum = 3; ballNum > 0; ballNum--) {
                setColorFromRank(gc, ballNum);
                if (3 - ballNum < arr[i]) {
                    sconst.fill(gc, new Ellipse(x, y - 14, 16, 16));
                }
                x += 25;
            }
            y += 25;
        }
    }

    protected void setColorFromRank(GraphicsContext gc, int rank) {
        if (rank == 1) {
            gc.setStroke(Color.GOLD); // gold
        }
        if (rank == 2) {
            gc.setStroke(Color.SILVER); // silver
        }
        if (rank == 3) {
            gc.setStroke(Color.web("#CD7F32")); // bronze
        }
    }

    protected void gameEndStatsAndRanks(GraphicsContext gc, PlayerDivider client) {
        JSONObject stats = game.stats.statsOf(client);
        JSONObject ranks = game.stats.ranksOf(client);
        int y = 425;
        Font font = new Font("Verdana", sconst.STATS_FONT);
        sconst.setFont(gc, font);
        for (String stat : stats.keySet()) {
            gc.setStroke(Color.BLACK);
            darkTheme(gc);
            sconst.drawString(gc, stat + ": " + stats.get(stat), 310, y);
            if (ranks.has(stat) && ((int) ranks.get(stat) >= 1)) {
                int rank = (int) ranks.get(stat);
                setColorFromRank(gc, rank);
                sconst.fill(gc,new Ellipse(sconst.STATS_MEDAL + 1, //subtractions for internal color circle
                        y - (sconst.STATS_FONT - 4) + 1,
                        sconst.STATS_FONT - 2, sconst.STATS_FONT - 2));
                gc.setStroke(Color.BLACK);
                
                sconst.fill(gc, new Ellipse(sconst.STATS_MEDAL,
                        y - (sconst.STATS_FONT - 4),
                        sconst.STATS_FONT, sconst.STATS_FONT));
            }
            y += sconst.STATS_FONT + 5;
        }
    }

    public void clientInitialize() throws IOException {
        ballFrame = 0;
        ballFrameCounter = 0;
        camX = 500;
        camY = 300;
        openConnection();
    }

    public void openConnection() throws IOException {
        //TODO replace this with a socketio connection
        gameserverConn.start();
        //gameserverConn.setHardy(true);
        KryoRegistry.register(kryo);
        if (!gameserverConn.isConnected()) {
            gameserverConn.connect(999999999, "zanzalaz.com", 54555);
            //gameserverConn.connect(5000, "127.0.0.1", 54555);

            Runnable updateServer = () -> {
                if (controlsHeld != null && gameserverConn.isConnected()) {
                    controlsHeld.gameID = gameID;
                    controlsHeld.token = token;
                    controlsHeld.masteries = masteries;
                    gameserverConn.sendTCP(controlsHeld);
                }
                else if(!gameserverConn.isConnected()) {
                    gameserverConn.close();
                    gameserverConn = new Client(8 * 1024 * 1024, 1024 * 1024); //8mb and 1mb
                }
                else{
                    System.out.println("null controls held");
                }
            };

            gameserverConn.addListener(new Listener() {
                public synchronized void received(Connection connection, Object object) {
                     System.out.println("type of object: " + object.getClass().getName());
                     if (object instanceof Game) {
                         game = (GameEngine) object;
                     }
                     else if (object instanceof byte[]) {
                        byte[] data = (byte[]) object;
                        game = kryo.readObject(new Input(data), GameEngine.class);
                     }
                     else if (object instanceof FrameworkMessage.KeepAlive) {
                        System.out.println("Got a keepalive from gameserver!");
                        return;
                    }
                     else {
                         System.out.println("Got a non-game from gameserver!");
                     }
                    game.began = true;
                    phase = game.phase;
                    controlsHeld.gameID = gameID;
                    controlsHeld.token = token;
                    controlsHeld.masteries = masteries;
                    controlsHeld.camX = camX;
                    controlsHeld.camY = camY;
                    
                    try {
                        System.out.println("Initial update sending");
                        gameserverConn.sendTCP(controlsHeld);
                        System.out.println("Initial update sent");
                    } catch (KryoException e) {
                        System.out.println("kryo end");
                        System.out.println(game.ended);
                    }
                }
            });
            exec.scheduleAtFixedRate(updateServer, 30, 30, TimeUnit.MILLISECONDS);
            System.out.println("Updates scheduled");
        }
    }

    protected String requestOrQueueGame() throws UnirestException {
        if (!queued) {
            loginClient.retry401Catch503("join", this.tournamentCode);
            token = loginClient.token;
            queued = true;
        } else {
            loginClient.retry401Catch503("check", null);
            token = loginClient.token;
        }
        if (loginClient.gameId != null && loginClient.gameId.equals("NOT QUEUED")) {
            throw new UnirestException("Server not accepting connections");
        }
        if (loginClient.gameId != null && !loginClient.gameId.equals("WAITING")) {
            this.gameID = loginClient.gameId;
            this.phase = GamePhase.COUNTDOWN;
        }
        return loginClient.gameId;
    }

    protected void creditPanel(GraphicsContext gc) {
        darkTheme(gc);
        sconst.drawImage(gc, intro, 0, 0);
        
        Font font = new Font("Verdana", 65);
        sconst.setFont(gc, font);
        sconst.drawString(gc, "Space to proceed", 370, 640);
    }

    public void rotTranslateArrow(GraphicsContext gc, int xt, int yt, double rot) {
        Affine transform = new Affine();
        // 1. Translate the object to rotate around the center
        transform.appendTranslation(-(sconst.BALL_PTR_X / 2), -(sconst.BALL_PTR_Y / 2));
        // 2. Rotate the object
        transform.appendRotation(rot, sconst.BALL_PTR_X / 2, sconst.BALL_PTR_Y / 2);
        // 3. Translate to the position on the canvas
        transform.appendTranslation(xt, yt);
    
        gc.setTransform(transform);
        
        if (game.anyPoss()) {
            sconst.drawImage(gc,ballPtr, 0, 0, sconst.BALL_PTR_X, sconst.BALL_PTR_Y);
        } else {
            sconst.drawImage(gc,ballFPtr, 0, 0, sconst.BALL_PTR_X, sconst.BALL_PTR_Y);
        }
    
        // Reset the transform to default
        gc.setTransform(new Affine());
    }
    
    public void displayBallArrow(GraphicsContext gc) {
        if (game == null) {
            //return;
        }
        int x = (int) (game.ball.X + game.ball.centerDist - camX);
        int y = (int) (game.ball.Y + game.ball.centerDist - camY);
        final int XCORR = xSize;
        final int YCORR = ySize;
        double rot = 0;
        if (x < 0) {
            rot = 180;
            int yt = y;
            if (y < 0) {//diag
                rot = 225;
                yt = 20;
            } else if (y > ySize - YCORR) {
                rot = 135;
                yt = ySize - YCORR - (sconst.BALL_PTR_X / 2);
            }
            rotTranslateArrow(gc, sconst.BALL_PTR_X / 2, yt, rot);
        } else if (x > xSize - XCORR) {
            int yt = y;
            if (y < 0) {//diag
                rot = 315;
                yt = 20;
            } else if (y > ySize - YCORR) {//diag
                rot = 45;
                yt = ySize - YCORR - (sconst.BALL_PTR_X / 2);
            }
            rotTranslateArrow(gc, xSize - XCORR - (sconst.BALL_PTR_X / 2), yt, rot);
        } else if (y < 0) {
            rot = 270;
            rotTranslateArrow(gc, x, (sconst.BALL_PTR_X / 2), rot);
        } else if (y > ySize - YCORR) {
            rot = 90;
            rotTranslateArrow(gc, x, ySize - YCORR - (sconst.BALL_PTR_X / 2), rot);
        }
    }

    public void postgoalReset() {
        ballFrame = 0;
        ballFrameCounter = 0;
        camX = 500;
        camY = 300;
    }

    public void reflectDraw(Line line, double mx, double my, GraphicsContext gc) {
        // Implement your reflection logic here
        // Example: drawing a reflection of the line
        gc.strokeLine(mx - (line.getEndX() - line.getStartX()), my - (line.getEndY() - line.getStartY()), mx, my);
    }
    
    protected void graphicsDrawCurve(GraphicsContext gc, QuadCurve eL){
        gc.beginPath();
        gc.moveTo(eL.getStartX(), eL.getStartY());
        gc.quadraticCurveTo(eL.getControlX(), eL.getControlY(), eL.getEndX(), eL.getEndY());
        gc.stroke();
    }

    protected void doDrawing(GraphicsContext gc) {
        if (game == null) {
            return;
        }
        if (instructionToggle) {
            tutorial(gc);
            return;
        }
        if (camFollow) {
            camX = (int) (game.underControl.X + 35 - (this.xSize / 3 / 1.5 * scl));
            //if (camX > 820) camX = 820;
            if (camX < 0) camX = 0;
            camY = (int) (game.underControl.Y + 35 - (this.ySize / 3 / 1.5 * scl));
            //if (camY > 480) camY = 480;
            if (camY < 0) camY = 0;
        }
        sconst.drawImage(gc, field, (1 - camX), (1 - camY));
        gc.setLineWidth(6.0); // Set the stroke width
        for (GoalHoop goalData : game.lowGoals) {
            GoalSprite goal = new GoalSprite(goalData, camX, camY, sconst);
            Team enemy;
            if (goal.team == TeamAffiliation.HOME) {
                enemy = game.away;
            } else { //(goal.team == TeamAffiliation.AWAY)
                enemy = game.home;
            }
            if (!goal.checkReady()) {
                gc.setStroke(Color.RED);
                if (goal.frozen) {
                    gc.setStroke(Color.web("#26ECEA")); // light blue
                }
            } else if (enemy.score % 1.0 == .75) {
                gc.setStroke(Color.web("#CFA120")); // gold-like color
            } else {
                gc.setStroke(Color.LIGHTGRAY);
            }
            goal.draw(gc);
        }
        for (GoalHoop goalData : game.hiGoals) {
            GoalSprite goal = new GoalSprite(goalData, camX, camY, sconst);
            Team enemy;
            if (goal.team == TeamAffiliation.HOME) {
                enemy = game.away;
            } else { //(goal.team == TeamAffiliation.AWAY)
                enemy = game.home;
            }
            gc.setStroke(Color.DARKGRAY);
            if (enemy.score % 1.0 == .75) {
                gc.setStroke(Color.GREEN);
            }
            goal.draw(gc);
        }
        drawPainHealIndicator(gc, game);
        ArrayList<RangeCircle> clientCircles = new ArrayList<>();
        for (RangeCircle ri : game.underControl.rangeIndicators) {
            clientCircles.add(ri);
        }
        gc.setLineWidth(SHOT_WIDTH);
        if (game.underControl.possession == 1) {
            double pow = game.underControl.throwPower;
            double mx = controlsHeld.posX + camX;
            double my = controlsHeld.posY + camY;
            double ox = game.ball.X + game.ball.width / 2 - camX;
            double oy = game.ball.Y + game.ball.height / 2 - camY;
            ox = sconst.adjX(ox);
            oy = sconst.adjY((int) oy);
            double angle = Math.toRadians(Util.degreesFromCoords(mx - game.ball.X - game.ball.width / 2,
                    my - game.ball.Y - game.ball.height / 2));
            final double LOB_DIST = sconst.adjX(230);
            final double SHOT_DIST = sconst.adjX(316);
            final double BALL_HALF = sconst.adjX(15);
            Line lobBlock = new Line(
                ox,
                oy,
                ox + ((.2 * LOB_DIST * pow + BALL_HALF) * Math.cos(angle)),
                oy + ((.2 * LOB_DIST * pow + BALL_HALF) * Math.sin(angle))
            );
    
            gc.setStroke(Color.YELLOW);
            
            // Call to reflectDraw method (you'll need to define this method)
            reflectDraw(lobBlock, mx, my, gc);
            
            // Draw the line
            gc.strokeLine(lobBlock.getStartX(), lobBlock.getStartY(), lobBlock.getEndX(), lobBlock.getEndY());

            Line lobFly = new Line(ox + ((.2 * LOB_DIST * pow + BALL_HALF) * Math.cos(angle)),
                    oy + ((.2 * LOB_DIST * pow + BALL_HALF) * Math.sin(angle)),
                    ox + ((.75 * LOB_DIST * pow - BALL_HALF) * Math.cos(angle)),
                    oy + ((.75 * LOB_DIST * pow - BALL_HALF) * Math.sin(angle)));
            gc.setStroke(Color.BLUE);
            reflectDraw(lobFly, mx, my, gc);

            Line lobCatch = new Line(ox + ((.75 * LOB_DIST * pow - BALL_HALF) * Math.cos(angle)),
                    oy + ((.75 * LOB_DIST * pow - BALL_HALF) * Math.sin(angle)),
                    ox + (LOB_DIST * pow * Math.cos(angle)),
                    oy + (LOB_DIST * pow * Math.sin(angle)));
            gc.setStroke(Color.YELLOW);
            reflectDraw(lobCatch, mx, my, gc);
            if (this.controlsHeld.artisanShot == ClientPacket.ARTISAN_SHOT.SHOT
                    || !game.underControl.getType().equals(TitanType.ARTISAN)) {
                Line shot = new Line(ox + (LOB_DIST * pow * Math.cos(angle)),
                        oy + (LOB_DIST * pow * Math.sin(angle)),
                        ox + (SHOT_DIST * pow * Math.cos(angle)),
                        oy + (SHOT_DIST * pow * Math.sin(angle)));
                gc.setStroke(Color.DARKRED);
                reflectDraw(shot, mx, my, gc);
            }
            double Q_CURVE_A = sconst.adjX(310);
            double Q_CURVE_B = sconst.adjX(186);
            if (game.underControl.possession == 1 && game.underControl.getType() == TitanType.ARTISAN) {
                if (this.controlsHeld.artisanShot == ClientPacket.ARTISAN_SHOT.LEFT) {
                    QuadCurve eL = new QuadCurve(ox, oy,
                            ox + (Q_CURVE_A * pow * Math.cos(angle - .97)),
                            oy + (Q_CURVE_A * pow * Math.sin(angle - .97)),
                            ox + (Q_CURVE_B * pow * Math.cos(angle)),
                            oy + (Q_CURVE_B * pow * Math.sin(angle)));
                    gc.setStroke(Color.GREEN);
                    graphicsDrawCurve(gc, eL);
                }
                if (this.controlsHeld.artisanShot == ClientPacket.ARTISAN_SHOT.RIGHT) {
                    QuadCurve eR = new QuadCurve(ox, oy,
                            ox + (Q_CURVE_A * pow * Math.cos(angle + .97)),
                            oy + (Q_CURVE_A * pow * Math.sin(angle + .97)),
                            ox + (Q_CURVE_B * pow * Math.cos(angle)),
                            oy + (Q_CURVE_B * pow * Math.sin(angle)));
                    gc.setStroke(Color.PURPLE);
                    graphicsDrawCurve(gc, eR);
                }
            }
        } else {
            RangeCircle steal = new RangeCircle(
                    Color.SKYBLUE, game.underControl.stealRad);
            clientCircles.add(steal);
        }

        drawEntities(gc);
        if (game.ballVisible == true) {
            if (game.anyPoss()) {
                if (ballFrame == 0) {
                    sconst.drawImage(gc, ballTexture, ((int) game.ball.X - camX), ((int) game.ball.Y - camY));
                }
                if (ballFrame == 1) {
                    sconst.drawImage(gc, ballBTexture, ((int) game.ball.X - camX), ((int) game.ball.Y - camY));
                }
            } else {
                if (ballLobMode())
                    sconst.drawImage(gc, ballLobTexture, ((int) game.ball.X - camX), ((int) game.ball.Y - camY));
                else if (ballFrame == 0) {
                    sconst.drawImage(gc, ballFTexture, ((int) game.ball.X - camX), ((int) game.ball.Y - camY));
                } else if (ballFrame == 1) {
                    sconst.drawImage(gc, ballFBTexture, ((int) game.ball.X - camX), ((int) game.ball.Y - camY));
                }
            }
        }
        for (RangeCircle ri : clientCircles) { //draw these on top of enemies, but the shot underneath
            gc.setLineWidth(RANGE_SIZE);
            gc.setStroke(ri.getColor());
            Titan t = game.underControl;
            if (ri.getRadius() > 0) {
                //don't show Artisan Suck with ball
                if (t.getType() != TitanType.ARTISAN || t.possession == 0 || ri.getColor().getGreen() < 250) {
                    int w = (int) (ri.getRadius() * 2 * t.rangeFactor);
                    int h = w;
                    int x = (int) t.X + (t.width / 2) - w / 2;
                    int y = (int) t.Y + (t.height / 2) - h / 2;
                    x = sconst.adjX(x - camX);
                    y = sconst.adjY(y - camY);
                    h = sconst.adjY(h);
                    w = sconst.adjX(w);
                    gc.fillOval(x, y, w, h);
                }
            }
        }
        drawPortalRanges(gc);
        if (game.goalVisible == true) {
            sconst.drawImage(gc,goalScored, sconst.GOAL_TXT_X, sconst.GOAL_TXT_Y);
        }
        if (game.colliders != null) {
            gc.setLineWidth(6);
            for (ShapePayload c : game.colliders) {
                gc.setStroke(c.getColor());
                Shape b = c.fromWithCamera(camX, camY, sconst);
                gc.fillOval(b.getBoundsInLocal().getMinX(),
                        b.getBoundsInLocal().getMinY(),
                        b.getBoundsInLocal().getWidth(),
                        b.getBoundsInLocal().getHeight());
            }
        }
    }

    private void drawPortalRanges(GraphicsContext gc) {
        for (Entity e : game.entityPool) {
            RangeCircle ri = null;
            if (e instanceof BallPortal && game.underControl.id.equals(((BallPortal) e).getCreatedById())) {
                ri = ((BallPortal) e).rangeCircle;
            }
            if (e instanceof Portal && game.underControl.id.equals(((Portal) e).getCreatedById())) {
                ri = ((Portal) e).rangeCircle;
            }
            if (ri != null) {
                int w = (int) (ri.getRadius() * 2);
                int h = w;
                int x = (int) e.X + (e.width / 2) - w / 2;
                int y = (int) e.Y + (e.height / 2) - h / 2;
                x = (int) sconst.adjX(x - camX);
                y = sconst.adjY(y - camY);
                h = sconst.adjY(h);
                w = (int) sconst.adjX(w);
                Ellipse ell = new Ellipse(x, y, w, h);
                ShapePayload c = new ShapePayload(ell);
                gc.setStroke(ri.getColor());
                Shape b = c.fromWithCamera(camX, camY, sconst);
                gc.fillOval(b.getBoundsInLocal().getMinX(),
                        b.getBoundsInLocal().getMinY(),
                        b.getBoundsInLocal().getWidth(),
                        b.getBoundsInLocal().getHeight());
            }
        }
    }

    private void drawPainHealIndicator(GraphicsContext gc, GameEngine game) {
        for (int sel = 1; sel <= game.players.length; sel++) {
            if (indexSelected() == sel) {
                Titan e = game.players[sel - 1];
                GoalHoop[] pains = game.getPainHoopsFromTeam(e.team);
                for (GoalHoop pain : pains) {
                    double delta = Util.calculatePain(e, pain);
                    gc.setLineWidth(4.0f);
                    if (delta > 0) {
                        double percent = 75 - (7.5 * delta);
                        if (percent < 0) {
                            percent = 0.1;
                        }
                        setColorBasedOnPercent(gc, percent, false);
                        gc.strokeLine(0, 0, 1920, 0);
                    }
                    final double FPS = 1000 / game.GAMETICK_MS;
                    double time = (game.framesSinceStart / FPS);
                    time = (int) (time * 10.0) / 10.0;
                    if (delta < -3 && time < game.PAIN_DISABLE_TIME) {
                        if (e.possession == 1) {
                            gc.setStroke(Color.BLUE);
                        } else {
                            gc.setStroke(Color.GREEN);
                        }
                        gc.strokeLine(0, 0, 1920, 0);
                    }
                }
            }
        }

    }

    private boolean ballLobMode() {
        for (int n = game.players.length - 1; n >= 0; n--) {
            if (game.players[n].actionState == Titan.TitanState.LOB
                    && game.players[n].actionFrame > 10 &&
                    game.players[n].actionFrame < 30) {
                return true;
            }
        }
        return false;
    }

    protected Team teamFromUnderControl() {
        TeamAffiliation affil = game.underControl.team;
        if (affil.equals(game.home.which)) {
            return game.home;
        }
        return game.away;
    }

    protected Team enemyFromUnderControl() {
        TeamAffiliation affil = game.underControl.team;
        if (affil.equals(game.home.which)) {
            return game.away;
        }
        return game.home;
    }

    protected void drawEntities(GraphicsContext gc) {
        //The selection flag
        for (int sel = 1; sel <= game.players.length; sel++) {
            if (indexSelected() == sel) {
                Titan p = game.players[sel - 1];
                sconst.drawImage(gc, selector, ((int) p.getX() - camX + 27), ((int) p.getY() - camY - 22));
            }
        }
        drawNontitans(gc, game.entityPool);
        //For the goalies
        for (int n = 0; n < 2; n++) {
            if (game.players[n].getX() + 35 <= game.ball.X) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.GOALIE, 17), ((int) game.players[n].getX() - camX), ((int) game.players[n].getY() - camY));
            }
            if (game.players[n].getX() + 35 > game.ball.X) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.GOALIE, 18), ((int) game.players[n].getX() - camX), ((int) game.players[n].getY() - camY));
            }
        }
        //Skip the goalies for the next display loop
        //addBoostIcons();
        for (int i = 2; i < game.players.length; i++) {
            Titan t = game.players[i];
            if (game.underControl.team != t.team &&
                    invisible(game.players[i])) {
                continue;
            }//Display NOTHING if stealthed
            int facing;
            if (t.facing >= 90 && t.facing < 270) {
                facing = 2;
            } else {
                facing = 1;
            }
            //Stills
            if (facing == 1 && t.runningFrame == 0 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 1), ((int) t.X - camX), ((int) t.Y - camY));
            }
            if (facing == 2 && t.runningFrame == 0 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 9), ((int) t.X - camX), ((int) t.Y - camY));
            }
            // Frame 1 run right
            if (facing == 1 && t.runningFrame == 1 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 2), ((int) t.X - camX), ((int) t.Y - camY));
            }
            // Frame 2 run right
            if (facing == 1 && t.runningFrame == 2 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 3), ((int) t.X - camX), ((int) t.Y - camY));
            }
            // Frame 1 run left
            if (facing == 2 && t.runningFrame == 1 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 10), ((int) t.X - camX), ((int) t.Y - camY));
            }
            // Frame 2 run right
            if (facing == 2 && t.runningFrame == 2 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 11), ((int) t.X - camX), ((int) t.Y - camY));
            }
            //Shoot
            if (facing == 1 && t.actionState == Titan.TitanState.SHOOT) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 8), ((int) t.X - camX), ((int) t.Y - camY));
            }
            if (facing == 2 && t.actionState == Titan.TitanState.SHOOT) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 16), ((int) t.X - camX), ((int) t.Y - camY));
            }
            //pass
            if (facing == 1 && t.actionState == Titan.TitanState.LOB) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 4), ((int) t.X - camX), ((int) t.Y - camY));
            }
            if (facing == 2 && t.actionState == Titan.TitanState.LOB) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 12), ((int) t.X - camX), ((int) t.Y - camY));
            }
            //attacks
            if (t.actionState == Titan.TitanState.A1 && facing == 2) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 5), ((int) t.X - camX), ((int) t.Y - camY));
            }
            if (t.actionState == Titan.TitanState.A2 && facing == 2) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 6), ((int) t.X - camX), ((int) t.Y - camY));
            }
            if (t.actionState == Titan.TitanState.STEAL && facing == 2) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 5), ((int) t.X - camX), ((int) t.Y - camY));
            }
            if (t.actionState == Titan.TitanState.A1 && facing == 1) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 13), ((int) t.X - camX), ((int) t.Y - camY));
            }
            if (t.actionState == Titan.TitanState.A2 && facing == 1) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 14), ((int) t.X - camX), ((int) t.Y - camY));
            }
            if (t.actionState == Titan.TitanState.STEAL && facing == 1) {
                sconst.drawImage(gc, SelectClassSkins.pullImage(gc, t.getType(), 13), ((int) t.X - camX), ((int) t.Y - camY));
            }
        }
        drawEffectIcons(gc);
        if (game.allSolids != null) {
            List<Entity> drawHp = new ArrayList<>(Arrays.asList(game.allSolids));
            if (game.underControl.team == TeamAffiliation.HOME)
                Collections.reverse(drawHp); //draw your team on top always
            for (Entity e : drawHp) {
                if (e.health > 0.0) {
                    if (e instanceof Titan) {
                        Titan t = (Titan) e;
                        if (game.underControl.team != t.team &&
                                invisible(t)) {
                            continue;
                        }
                    }
                    drawHealthBar(gc, e);
                }
            }
        }
    }

    protected boolean invisible(Entity t) {
        boolean opp = this.game.underControl.team != t.team;
        return opp && game.effectPool.hasEffect(t, EffectId.STEALTHED) && !game.effectPool.hasEffect(t, EffectId.FLARE);
    }

    protected void drawNontitans(GraphicsContext gc, java.util.List<Entity> draw) {
        staticFrame += 1;
        staticFrame %= 2;
        for (Entity e : draw) {
            Image f1 = trap1;
            Image f2 = trap2;
            if (e instanceof Wall) {
                f1 = wall;
                f2 = wall;
            }
            if (e instanceof BallPortal) {
                BallPortal p = (BallPortal) e;
                if (p.isCooldown(game.now)) {
                    f1 = bportalcd;
                    f2 = bportalcd;
                } else {
                    f1 = bportal1;
                    f2 = bportal2;
                }
            }
            if (e instanceof Portal) {
                Portal p = (Portal) e;
                if (p.isCooldown(game.now)) {
                    f1 = portalcd;
                    f2 = portalcd;
                } else {
                    f1 = portal1;
                    f2 = portal2;
                }
            }
            if (e instanceof Fire) {
                f1 = fire1;
                f2 = fire2;
            }
            if (e instanceof Cage) {
                f1 = cage;
                f2 = f1;
            }
            if (e instanceof Wolf) {
                Wolf w = (Wolf) e;
                if (w.wolfPower == 1) {
                    if (w.facingRight) {
                        f1 = wolf1R;
                    } else {
                        f1 = wolf1L;
                    }
                } else if (w.wolfPower == 2) {
                    if (w.facingRight) {
                        f1 = wolf2R;
                    } else {
                        f1 = wolf2L;
                    }
                } else if (w.wolfPower > 2 && w.wolfPower < 5) {
                    if (w.facingRight) {
                        f1 = wolf3R;
                    } else {
                        f1 = wolf3L;
                    }
                } else if (w.wolfPower >= 5) {
                    if (w.facingRight) {
                        f1 = wolf5R;
                    } else {
                        f1 = wolf5L;
                    }
                }
                f2 = f1;
            }
            if (!invisible(e)) {
                if (staticFrame % 2 == 1) {
                    sconst.drawImage(gc, f1, (int) e.getX() - camX, (int) e.getY() - camY);
                } else {
                    sconst.drawImage(gc, f2, (int) e.getX() - camX, (int) e.getY() - camY);
                }
            }
        }
    }

    protected void drawEffectIcons(GraphicsContext gc) {
        Map<UUID, Integer> offset = new HashMap<>();
        for (Titan t : game.players) {
            offset.put(t.id, -22);
        }
        for (Entity t : game.entityPool) {
            offset.put(t.id, -5);
        }
        for (int i = 0; i < game.effectPool.getEffects().size(); i++) {
            Effect e = game.effectPool.getEffects().get(i);
            Entity en = game.effectPool.getOn().get(i);
            //TODO evaluate whether or not to display cooldowns
            if (/*!e.toString().contains("COOLDOWN") &&*/ !e.toString().contains("ATTACKED")) {
                if (en instanceof Titan) {
                    Titan t = (Titan) en;
                    if (offset.containsKey(t.id) && !invisible(t)) {
                        sconst.drawImage(gc, e.getIconSmall(), (int) t.X + offset.get(t.id) - camX,
                                (int) t.Y - 29 - camY);
                        offset.put(t.id, offset.get(t.id) + 16);
                    }
                } else {
                    if (offset.containsKey(en.id) && !invisible(en)) {
                        sconst.drawImage(gc, e.getIconSmall(), (int) en.X + offset.get(en.id) - camX,
                                (int) en.Y - 25 - camY);
                        offset.put(en.id, offset.get(en.id) + 16);
                    }
                }

            }
        }
    }

    protected void drawHealthBar(GraphicsContext gc, Entity e) {
        int xOffset = -25;
        if (e.team == TeamAffiliation.AWAY) {
            gc.setStroke(Color.WHITE);
            xOffset = -21;
        }
        if (e.team == TeamAffiliation.HOME) {
            gc.setStroke(Color.BLUE);
            xOffset = -21;
        }
        if (e instanceof Titan) {
            if (!invisible(e)) {
                int x = (int) sconst.adjX((int) e.X + xOffset - camX);
                int y = sconst.adjY((int) e.Y - 13 - camY);
                Rectangle healthBar = new Rectangle(x, y,
                        (int) sconst.adjX(100), sconst.adjY(15));
                sconst.fill(gc,healthBar);
                int hpPercentage = (int) (100 * e.health / e.maxHealth);
                y = sconst.adjY((int) e.Y - 10 - camY);
                Rectangle healthStat = new Rectangle(x,
                        y,
                        (int) sconst.adjX(hpPercentage), sconst.adjY(9));
                setColorBasedOnPercent(gc, hpPercentage, false);
                sconst.fill(gc,healthStat);
                Titan t = (Titan) e;
                displayBuust(gc, t, x);
            }
        } else {
            if (!invisible(e)) {
                gc.setStroke(Color.DARKGRAY);
                xOffset = -9;
                if (e.team == TeamAffiliation.AWAY) {
                    gc.setStroke(Color.WHITE);
                    xOffset = -5;
                }
                if (e.team == TeamAffiliation.HOME) {
                    gc.setStroke(Color.BLUE);
                    xOffset = -5;
                }
                int x = (int) sconst.adjX((int) e.X + xOffset - camX);
                int y = sconst.adjY((int) e.Y - 9 - camY);
                Rectangle healthBar = new Rectangle(x, y,
                        (int) sconst.adjX(66), sconst.adjY(8));
                sconst.fill(gc,healthBar);
                int hpPercentage = (int) (100 * e.health / e.maxHealth);
                y = sconst.adjY((int) e.Y - 8 - camY);
                Rectangle healthStat = new Rectangle(x,
                        y,
                        (int) sconst.adjX(hpPercentage * 2 / 3), sconst.adjY(5));
                setColorBasedOnPercent(gc, hpPercentage, false);
                sconst.fill(gc,healthStat);
            }
        }
        if (e instanceof Portal) {
            Portal p = (Portal) e;
            if (p.isCooldown(game.now)) {
                double durSpent = p.cooldownPercentOver(game.now);
                setColorBasedOnPercent(gc, durSpent, false);
                Rectangle durBar = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 1 - camY, (int) sconst.adjX(durSpent * 2 / 3), sconst.adjY(2));
                sconst.fill(gc,durBar);
            }
        }
        if (e instanceof BallPortal) {
            BallPortal p = (BallPortal) e;
            if (p.isCooldown(game.now)) {
                double durSpent = p.cooldownPercentOver(game.now);
                setColorBasedOnPercent(gc, durSpent, false);
                Rectangle durBar = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 1 - camY, (int) sconst.adjX(durSpent * 2 / 3), sconst.adjY(2));
                sconst.fill(gc,durBar);
            }
        }
    }

    private void displayBuust(GraphicsContext gc, Titan t, int x) {
        int adjustedY = sconst.adjY((int) t.Y - 4 - (int) camY);

        // Set stroke color based on fuel level
        if (t.fuel > 25) {
            gc.setStroke(Color.rgb(128, 128, 255)); // RGB equivalent of (.5, .5, 1)
        } else {
            gc.setStroke(Color.rgb(166, 0, 0)); // RGB equivalent of (.65, 0, 0)
        }

        // Create and fill the boost status rectangle
        Rectangle boostStat = new Rectangle(x, adjustedY, sconst.adjX((int) t.fuel), sconst.adjY(3));
        sconst.fill(gc, boostStat);

        // Draw low boost warning if applicable
        if (t.fuel > 25) {
            gc.setStroke(Color.rgb(166, 0, 0)); // RGB equivalent of (.65, 0, 0)
            Rectangle lowBoostWarning = new Rectangle(x + 25 - 1, adjustedY, sconst.adjX(4), sconst.adjY(3));
            sconst.fill(gc, lowBoostWarning);
        }
    }

    public void classFacts(GraphicsContext gc) {
        updateSelected();
        if (controlsHeld.classSelection != TitanType.GOALIE) {
            double speed = Titan.normalOutOfTenFromStat(Titan.titanSpeed, controlsHeld.classSelection);
            double hp = Titan.normalOutOfTenFromStat(Titan.titanHealth, controlsHeld.classSelection);
            double shoot = Titan.normalOutOfTenFromStat(Titan.titanShoot, controlsHeld.classSelection);
            double steal = Titan.normalOutOfTenFromStat(Titan.titanStealRad, controlsHeld.classSelection);
            gc.setStroke(Color.BLUE);
            gc.setFont(new Font("Verdana", sconst.STAT_CAT_FONT));
            sconst.fill(gc,new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
            sconst.drawString(gc,"Speed", sconst.STAT_CAT_X, sconst.adjY(80));
            sconst.drawImage(gc,new RatioEffect(0, null, EffectId.FAST, 0)
                    .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL);
            sconst.fill(gc,new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 2, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
            sconst.drawString(gc,"Health", sconst.STAT_CAT_X, sconst.adjY(180));
            sconst.drawImage(gc,new HealEffect(0, null)
                    .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL * 2);
            sconst.fill(gc,new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 3, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
            sconst.drawString(gc,"Shot Power", sconst.STAT_CAT_X - 18, sconst.adjY(280));
            sconst.drawImage(gc,new ShootEffect(0, null)
                    .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL * 3);
            sconst.fill(gc,new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 4, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
            sconst.drawString(gc,"Steal Radius", sconst.STAT_CAT_X - 18, sconst.adjY(380));
            sconst.drawImage(gc,new EmptyEffect(0, null, EffectId.STEAL)
                    .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL * 4);

            setColorBasedOnPercent(gc, speed * 10.0, false);
            sconst.fill(gc,new Rectangle(sconst.STAT_INT_X, sconst.STAT_Y_SCL + 2,
                    (int) ((int) (speed * 10.0) * sconst.STAT_INTERNAL_SC), sconst.STAT_INTERNAL_H));
            setColorBasedOnPercent(gc, hp * 10.0, false);
            sconst.fill(gc,new Rectangle(sconst.STAT_INT_X, sconst.STAT_Y_SCL * 2 + 2,
                    (int) ((int) (hp * 10.0) * sconst.STAT_INTERNAL_SC), sconst.STAT_INTERNAL_H));
            setColorBasedOnPercent(gc, shoot * 10.0, false);
            sconst.fill(gc,new Rectangle(sconst.STAT_INT_X, sconst.STAT_Y_SCL * 3 + 2,
                    (int) ((int) (shoot * 10.0) * sconst.STAT_INTERNAL_SC), sconst.STAT_INTERNAL_H));
            setColorBasedOnPercent(gc, steal * 10.0, false);
            sconst.fill(gc,new Rectangle(sconst.STAT_INT_X, sconst.STAT_Y_SCL * 4 + 2,
                    (int) ((int) (steal * 10.0) * sconst.STAT_INTERNAL_SC), sconst.STAT_INTERNAL_H));


            String e = Titan.titanEText.get(controlsHeld.classSelection);
            String r = Titan.titanRText.get(controlsHeld.classSelection);
            String text = Titan.titanText.get(controlsHeld.classSelection);
            gc.setStroke(Color.GRAY);
            gc.setFont(new Font("Verdana", sconst.OVR_DESC_FONT));
            sconst.drawString(gc,controlsHeld.classSelection.toString(), sconst.OVR_DESC_FONT - 2, sconst.OVR_DESC_Y - sconst.OVR_DESC_FONT - 4);
            gc.setStroke(Color.BLACK);
            darkTheme(gc);
            sconst.drawString(gc,text, sconst.OVR_DESC_FONT - 2, sconst.OVR_DESC_Y);
            gc.setFont(new Font("Verdana", sconst.ABIL_DESC_FONT));

            sconst.drawImage(gc,new CooldownQ(0, null)
                    .getIcon(), sconst.ICON_ABIL_X, sconst.E_ABIL_Y);
            sconst.drawString(gc,e, sconst.DESC_ABIL_X, sconst.E_DESC_Y);
            sconst.drawImage(gc,new CooldownW(0, null)
                    .getIcon(), sconst.ICON_ABIL_X, sconst.R_ABIL_Y);
            sconst.drawString(gc,r, sconst.DESC_ABIL_X, sconst.R_DESC_Y);
        }
    }

    public void handleKeyReleased(KeyEvent ke) {
        KeyCode key = ke.getCode();
        if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
            controlsConfig.mapKeyRelease(controlsHeld, "" + key);
            //shotSound.rewindStart();
            if (controlsConfig.toggleInstructions(key)) {
                instructionToggle = false;
            }
            if (controlsConfig.movKey("" + key)) {
                for (Titan p : game.players) {
                    //todo only for controlled
                    p.runningFrame = 0;
                    p.diagonalRunDir = 0;
                }
            }
        }
    }

    @Override
    public void handle(KeyEvent ke) {
        if (ke.getEventType() == KeyEvent.KEY_RELEASED) {
            handleKeyReleased(ke);
        }
        KeyCode key = ke.getCode();
        if (game != null && (game.ended || game.phase == GamePhase.ENDED)) { // back to main after game
            if (key == KeyCode.BACK_SPACE || key == KeyCode.SPACE || key == KeyCode.ESCAPE || key == KeyCode.ENTER) {
                /*
                try {
                    parentWindow.reset(true); // TODO tournament feature here for next games
                } catch (IOException e) {
                    e.printStackTrace();
                }*/
                phase = GamePhase.SHOW_GAME_MODES;
            }
        }

        if (key == KeyCode.ESCAPE && phase == GamePhase.DRAW_CLASS_SCREEN) {
            phase = GamePhase.SHOW_GAME_MODES;
        }

        if (phase == GamePhase.TEAM_LAUNCH) {
            if (key == KeyCode.ESCAPE) {
                phase = GamePhase.SHOW_GAME_MODES;
            }
            if (key == KeyCode.BACK_SPACE || key == KeyCode.DELETE) {
                if (tournamentCode.length() > 0) {
                    tournamentCode = tournamentCode.substring(0, tournamentCode.length() - 1);
                }
            }
            if ((key.isLetterKey() || key.isDigitKey() || key == KeyCode.SPACE) && (key != KeyCode.ENTER)) {
                tournamentCode += key.getName();
            }
            if (key == KeyCode.SPACE || key == KeyCode.ENTER) {
                tourneyOptions = new GameOptions();
                tournamentCode += tourneyOptions.toString();
                phase = GamePhase.DRAFT_HOMEBAN;
            }
        }
        if (phase == GamePhase.TOURNAMENT_CODE) {
            if (key == KeyCode.ESCAPE) {
                phase = GamePhase.SHOW_GAME_MODES;
            }
            if (key == KeyCode.BACK_SPACE || key == KeyCode.DELETE) {
                if (tournamentCode.length() > 0) {
                    tournamentCode = tournamentCode.substring(0, tournamentCode.length() - 1);
                }
            }
            if (key == KeyCode.SHIFT) {
                codeHide = !codeHide;
            }
            if (key.isLetterKey() || key.isDigitKey()) {
                tournamentCode += key.getName();
            }
            if (key == KeyCode.DOWN) {
                tourneyIndex++;
                if (tourneyIndex >= 8) {
                    tourneyIndex -= 8;
                }
            }
            if (key == KeyCode.UP) {
                tourneyIndex--;
                if (tourneyIndex < 0) {
                    tourneyIndex += 8;
                }
            }
            if (key == KeyCode.LEFT) {
                tourneyOptions.advance(tourneyIndex, -1);
            }
            if (key == KeyCode.RIGHT) {
                tourneyOptions.advance(tourneyIndex, 1);
            }
            if (key == KeyCode.SPACE || key == KeyCode.ENTER) {
                tournamentCode += tourneyOptions.toString();
                phase = GamePhase.DRAW_CLASS_SCREEN;
            }
            return;
        }
        if (debugCamera == 1) {
            if (key == KeyCode.RIGHT && phase == GamePhase.INGAME) {
                camX += 10;
            }
            if (key == KeyCode.LEFT && phase == GamePhase.INGAME) {
                camX -= 10;
            }
            if (key == KeyCode.UP && phase == GamePhase.INGAME) {
                camY -= 10;
            }
            if (key == KeyCode.DOWN && phase == GamePhase.INGAME) {
                camY += 10;
            }
        }
        boolean advance = stateControls(key); //3 -> 4
        if (advance) {
            return;
        }
        advance = masteriesKeys(key); //3 -> 4
        if (advance) {
            return;
        }
        if (key == KeyCode.ESCAPE && phase == GamePhase.WAIT_FOR_GAME) {
            try {
                loginClient.retry401Catch503("leave", null);
            } catch (UnirestException e) {
                // This is fine, the server is in shutdown mode so we have already been dequeued
            }
            queued = false;
            phase = GamePhase.DRAW_CLASS_SCREEN;
        }
        if (key == KeyCode.ESCAPE && phase == GamePhase.SET_MASTERIES) {
            phase = GamePhase.DRAW_CLASS_SCREEN;
        }
        if (key == KeyCode.SPACE && (phase == GamePhase.INGAME || phase == GamePhase.SCORE_FREEZE || phase == GamePhase.TUTORIAL)) {
            camFollow = !camFollow;
            controlsHeld.CAM = true;
            //TODO play restart ding/click
        }
        if ((phase == GamePhase.INGAME) || phase == GamePhase.TUTORIAL) {
            controlsConfig.mapKeyPress(this.game, controlsHeld, key, this.shotSound);
            if (controlsConfig.toggleInstructions(key)) {
                instructionToggle = true;
            }
            if (controlsConfig.fullScreen(key)) {
                fullScreen = !fullScreen;
                //TODO revisit fullscreen potential
                //parentWindow.toggleFullscreen(fullScreen);
            }
        }
        classKeys(key);
    }


    private void classKeys(KeyCode key) {
        if ((key == KeyCode.LEFT || key == KeyCode.A)) {
            if (phase == GamePhase.DRAW_CLASS_SCREEN) {
                cursor -= 1;
            }
            if (phase == GamePhase.SHOW_GAME_MODES) {
                cursor -= 1;
            }
        }
        if ((key == KeyCode.RIGHT || key == KeyCode.D)) {
            if (phase == GamePhase.DRAW_CLASS_SCREEN) {
                cursor += 1;
            }
            if (phase == GamePhase.SHOW_GAME_MODES) {
                cursor += 1;
            }
        }
        if ((key == KeyCode.UP || key == KeyCode.W)) {
            if (phase == GamePhase.DRAW_CLASS_SCREEN) {
                cursor -= 4;
            }
            if (phase == GamePhase.SHOW_GAME_MODES) {
                cursor -= 1;
            }
        }
        if ((key == KeyCode.DOWN || key == KeyCode.S)) {
            if (phase == GamePhase.DRAW_CLASS_SCREEN) {
                cursor += 4;
            }
            if (phase == GamePhase.SHOW_GAME_MODES) {
                cursor += 1;
            }
        }
    }

    private boolean stateControls(KeyCode key) {
        if ((key == KeyCode.SPACE || key == KeyCode.ENTER)
                && phase == GamePhase.CREDITS) {
            controlsHeld.CAM = true;
            phase = GamePhase.CONTROLS;
            return true;
        }
        if ((key == KeyCode.SPACE || key == KeyCode.ENTER)
                && phase == GamePhase.CONTROLS) {
            phase = GamePhase.SHOW_GAME_MODES;
            controlsHeld.CAM = true;
            return true;
        }
        if ((key == KeyCode.SPACE || key == KeyCode.ENTER)
                && phase == GamePhase.SHOW_GAME_MODES) {
            controlsHeld.CAM = true;
            switch (cursor) {
                case 0:
                    phase = GamePhase.DRAW_CLASS_SCREEN;
                    cursor = 1;
                    tourneyOptions = new GameOptions();
                    tournamentCode = tourneyOptions.toString();
                    return true;
                case 1:
                    phase = GamePhase.DRAW_CLASS_SCREEN;
                    cursor = 1;
                    tourneyOptions = new GameOptions();
                    tourneyOptions.playerIndex = 4;
                    tournamentCode = tourneyOptions.toString();
                    return true;
                case 2:
                    phase = GamePhase.TEAM_LAUNCH;
                    cursor = 1;
                    return true;
                case 3:
                    phase = GamePhase.TOURNAMENT_CODE;
                    cursor = 1;
                    return true;
                case 4:
                    phase = GamePhase.TUTORIAL_START;
                    cursor = 1;
                    return true;
            }
            return true;
        }
        if ((key == KeyCode.SPACE || key == KeyCode.ENTER)
                && phase == GamePhase.DRAW_CLASS_SCREEN) {
            controlsHeld.CAM = true;
            phase = GamePhase.SET_MASTERIES;
            return true;
        }
        if ((key == KeyCode.SPACE || key == KeyCode.ENTER)
                && phase == GamePhase.SET_MASTERIES) {
            phase = GamePhase.TRANSITIONAL;
            controlsHeld.CAM = true;
            return true;
        }
        if ((key == KeyCode.SPACE || key == KeyCode.ENTER)
                && phase == GamePhase.TRANSITIONAL) {
            phase = GamePhase.WAIT_FOR_GAME;
            controlsHeld.CAM = true;
            return true;
        }
        return false;
    }

    protected boolean masteriesKeys(KeyCode key) {
        if ((key == KeyCode.UP || key == KeyCode.W) && phase == GamePhase.SET_MASTERIES) {
            masteriesIndex--;
            if (masteriesIndex < 0) {
                masteriesIndex = 9;
            }
        }
        if ((key == KeyCode.DOWN || key == KeyCode.S) && phase == GamePhase.SET_MASTERIES) {
            masteriesIndex++;
            if (masteriesIndex > 9) {
                masteriesIndex = 0;
            }
        }
        if ((key == KeyCode.RIGHT || key == KeyCode.D) && phase == GamePhase.SET_MASTERIES) {
            masteryDelta(masteriesIndex, 1);
        }
        if ((key == KeyCode.LEFT || key == KeyCode.A) && phase == GamePhase.SET_MASTERIES) {
            masteryDelta(masteriesIndex, -1);
        }
        return false;
    }

    protected void masteryDelta(int index, int delta) {
        System.out.println("mastery delta " + index + " " + delta);
        Masteries oldMasteries = new Masteries(masteries);
        switch (index) {
            case 0:
                masteries.health += delta;
                break;
            case 1:
                masteries.shot += delta;
                break;
            case 2:
                masteries.damage += delta;
                break;
            case 3:
                masteries.speed += delta;
                break;
            case 4:
                masteries.cooldowns += delta;
                break;
            case 5:
                masteries.effectDuration += delta;
                break;
            case 6:
                masteries.stealRadius += delta;
                break;
            case 7:
                masteries.abilityRange += delta;
                break;
            case 8:
                masteries.abilityLag += delta;
                break;
            case 9:
                masteries.painReduction += delta;
                break;
        }
        if (masteries.validate() == -1) {
            System.out.println("detected invalid mastery settings");
            masteries = oldMasteries;
        }
    }

    public void updateSelected() {
        // Pass the value of the cursor to the class that uses it to choose the team of the player to load
        if (cursor == 1) controlsHeld.classSelection = TitanType.WARRIOR;
        if (cursor == 2) controlsHeld.classSelection = TitanType.RANGER;
        if (cursor == 3) controlsHeld.classSelection = TitanType.MAGE;
        if (cursor == 4) controlsHeld.classSelection = TitanType.HOUNDMASTER;
        if (cursor == 5) controlsHeld.classSelection = TitanType.MARKSMAN;
        if (cursor == 6) controlsHeld.classSelection = TitanType.DASHER;
        if (cursor == 7) controlsHeld.classSelection = TitanType.GOLEM;
        if (cursor == 8) controlsHeld.classSelection = TitanType.STEALTH;
        if (cursor == 9) controlsHeld.classSelection = TitanType.SUPPORT;
        if (cursor == 10) controlsHeld.classSelection = TitanType.BUILDER;
        if (cursor == 11) controlsHeld.classSelection = TitanType.ARTISAN;
        if (cursor == 12) controlsHeld.classSelection = TitanType.GRENADIER;
        if (cursor > 12) controlsHeld.classSelection = TitanType.GRENADIER;
        //System.out.println(controlsHeld.classSelection);
        if (masteries != controlsHeld.masteries) {
            controlsHeld.masteries = masteries;
        }
    }

    public void consumeCursorSelectClasses() {
        updateSelected();
        ObjectMapper mapper = new ObjectMapper();
        masteriesMap.put(controlsHeld.classSelection.toString(), masteries.asMap());
        try {
            File file = new File("res/masteries.json");
            file.delete();
            file.createNewFile();
            PrintStream ps = new PrintStream(file);
            ps.println(mapper.writeValueAsString(masteriesMap));
            ps.close();
        } catch (Exception ex1) {
            ex1.printStackTrace();
        }
        phase = GamePhase.WAIT_FOR_GAME;
    }

    protected void fireReconnect() {
        try {//rejoin game logic
            loginClient.retry401Catch503("check", null);
            if (!loginClient.gameId.equals("NOT QUEUED")) {
                phase = GamePhase.COUNTDOWN;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void initSurface(boolean addListeners) {
        // Instantiation of sound classes
        shotSound = new Sound();
        // Load the sounds
        shotSound.loadSound(shotSoundFile);
        if (addListeners) {
            //addKeyListener(this);
        }

        this.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        //setFocusable(true); // recognizes obtaining focus from the keyboard
    }

    public void starting(GraphicsContext gc) {
        darkTheme(gc);
        if (gamestart == null) {
            gamestart = Instant.now().plus(new Duration(5100));
        }
        sconst.drawImage(gc, lobby, 1, 1);
        Font font = new Font("Verdana", 24);
        gc.setStroke(Color.YELLOW);
        sconst.setFont(gc, font);
        double milUntil = (new Duration(Instant.now(), gamestart)).getMillis();
        //System.out.println(milUntil);
        sconst.drawString(gc, String.format("Starting in %1.1f seconds", milUntil / 1000.0), 345, 220);
        font = new Font("Verdana", 48);
        gc.setStroke(Color.RED);
        sconst.setFont(gc, font);
        sconst.drawString(gc, "STARTING", 418, 480);
        if (game == null &&
                Instant.now().isAfter(gamestart.plus(new Duration(500)))) {
            //TODO this messes us up bad somehow for everyone but the last client to connect
            sconst.setFont(gc, new Font("Verdana", 12));
            sconst.drawString(gc, "(Client may be disconnected, retrying)", 80, 520);
            /*
            if (game == null &&
                    Instant.now().isAfter(gamestart.plus(new Duration(2500)))) {
                
                try {
                    parentWindow.reset(false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }*/
        }
    }

    public void cannotJoin(GraphicsContext gc) {
        darkTheme(gc);
        if (gamestart == null) {
            gamestart = Instant.now().plus(new Duration(5100));
        }
        sconst.drawImage(gc, lobby, 1, 1);
        Font font = new Font("Verdana", 24);
        gc.setStroke(Color.PINK);
        sconst.setFont(gc, font);
        double milUntil = (new Duration(Instant.now(), gamestart)).getMillis();
        //System.out.println(milUntil);
        sconst.drawString(gc, "Cannot join, server is in shutdown mode: no new games at this time", 345, 220);
        font = new Font("Verdana", 36);
        sconst.setFont(gc, font);
        sconst.drawString(gc, "Please close the client and try again later", 418, 480);
    }

    public void lobby(GraphicsContext gc) {
        darkTheme(gc);
        sconst.drawImage(gc, lobby, 1, 1);
        Font font = new Font("Verdana", 24);
        gc.setStroke(Color.YELLOW);
        sconst.setFont(gc, font);
        sconst.drawString(gc, "Waiting for players...", 342, 220);

        font = new Font("Verdana", 24);
        gc.setStroke(Color.GREEN);
        sconst.setFont(gc, font);
        sconst.drawString(gc, "Press ESC to cancel...", 342, 480);
    }


    public void updateFrameBall() {
        if (game.anyPoss() || game.anyBallMoveState()) {
            ballFrameCounter += 1;
            if (ballFrameCounter == 5) ballFrame = 1;
            if (ballFrameCounter == 10) {
                ballFrame = 0;
                ballFrameCounter = 0;
            }
        }
    }

    public void displayScore(GraphicsContext gc) {
        if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
            Font font = new Font("Verdana", 45);
            sconst.setFont(gc, font);
            String goalsHome = Integer.toString((int) game.home.score);
            String minorGoalsHome = Integer.toString((int) ((game.home.score - (int) game.home.score) * 4));
            String goalsAway = Integer.toString((int) game.away.score);
            String minorGoalsAway = Integer.toString((int) ((game.away.score - (int) game.away.score) * 4));

            gc.setStroke(new Color(0f, 0f, 1f, .5f));
            sconst.drawString(gc, goalsHome, 180, 704);
            gc.setStroke(new Color(1f, 1f, 1f, .5f));
            sconst.drawString(gc, goalsAway, 799, 704);

            //draw minor goals
            font = new Font("Verdana", 32);
            sconst.setFont(gc, font);
            setColorFromCharge(gc, minorGoalsHome);
            sconst.drawString(gc, minorGoalsHome + "/4", 230, 701);
            setColorFromCharge(gc, minorGoalsAway);
            sconst.drawString(gc, minorGoalsAway + "/4", 848, 701);

            sconst.setFont(gc, new Font("Verdana", 9));
            //get usernmame from titan via token decoded jwt
            String jwt = controlsHeld.token;
            String username = Util.jwtExtractEmail(jwt);
            sconst.drawString(gc, username, 920, 701);
            gc.setStroke(Color.RED);
            int x = xSize / 4;
            //addBoostIcons();
            for (int i = 0; i < game.effectPool.getEffects().size(); i++) {
                Effect e = game.effectPool.getEffects().get(i);
                Entity on = game.effectPool.getOn().get(i);
                if (game.underControl.id.equals(on.id) && !e.toString().contains("ATTACKED")) {
                    sconst.setFont(gc, new Font("Verdana", 72));
                    if (e.effect == EffectId.ROOT) {
                        gc.setStroke(new Color(.36f, .51f, .28f, .4f));
                        sconst.drawString(gc, "Rooted!", 450, 300);
                    }
                    if (e.effect == EffectId.SLOW) {
                        gc.setStroke(new Color(.45f, .9f, .75f, .4f));
                        sconst.drawString(gc, "Slowed!", 450, 300);
                    }
                    if (e.effect == EffectId.STUN) {
                        gc.setStroke(new Color(1f, .74f, 0f, .4f));
                        sconst.drawString(gc, "Stunned!", 450, 300);
                    }
                    if (e.effect == EffectId.STEAL) {
                        gc.setStroke(new Color(0f, 0f, 0f, .4f));
                        sconst.drawString(gc, "Stolen!", 450, 300);
                    }
                    if (e.getIcon() != null) {
                        BlendMode originalBlendMode = gc.getGlobalBlendMode();

                        // Set the new blend mode to handle transparency
                        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
                        gc.setGlobalAlpha(0.5); // Set the transparency level

                        // Draw the image
                        gc.drawImage(e.getIcon(), x, 689);

                        // Restore the original blend mode and opacity
                        gc.setGlobalBlendMode(originalBlendMode);
                        gc.setGlobalAlpha(1.0); // Reset transparency level to opaque
                        gc.setStroke(new Color(1f, 1f, 1f, .5f));
                        double xt = sconst.adjX(x);
                        double wt = sconst.adjX(32);
                        double yt = sconst.adjY(657);
                        double ht = sconst.adjY(32);
                        gc.fillArc(xt, yt, wt, ht, 90, -360, ArcType.ROUND);
                        double percentBar = 100.0 - e.getPercentLeft();
                        if (percentBar > 100) {
                            percentBar = 99.99999;
                        }
                        if (percentBar < 0) {
                            percentBar = 0.000001;
                        }
                        setColorBasedOnPercent(gc, percentBar, true);
                        double coverage = e.getPercentLeft() / 100.0 * 360.0;
                        xt = sconst.adjX(x + 2);
                        wt = sconst.adjX(28);
                        yt = sconst.adjY(659);
                        ht = sconst.adjY(28);
                        gc.fillArc(xt, yt, wt, ht, 90, coverage, ArcType.ROUND);
                        x += 32;
                    }
                }
            }
            drawTimerWarnings(gc);
        }
    }

    private void drawTimerWarnings(GraphicsContext gc) {
        gc.setStroke(new Color(0f, 1f, 0f, .4f));
        Font font = new Font("Verdana", 32);
        sconst.setFont(gc, font);
        final double FPS = 1000 / game.GAMETICK_MS;
        String timeStr = "";
        double time = (game.framesSinceStart / FPS);
        time = (int) (time * 10.0) / 10.0;
        timeStr += time;
        sconst.drawString(gc, timeStr, 614, 711);
        gc.setStroke(new Color(0f, 0f, 0f, .4f));
        setBottomText(gc, (int) time);
        sconst.drawString(gc, bottomText, 480, 664);
    }

    private void setBottomText(GraphicsContext gc, int timer) {
        Font font = new Font("Verdana", 16);
        sconst.setFont(gc, font);
        bottomText = "";
        final int WARN = 30, FWARN = 10, CHWARN = 4;
        if (timer >= game.GOALIE_DISABLE_TIME - WARN && timer < game.GOALIE_DISABLE_TIME - FWARN) {
            gc.setStroke(new Color(.9f, .9f, 0f, .4f));
            bottomText = "GOALIES VANISHING WARNING";
        } else if (timer >= game.GOALIE_DISABLE_TIME - FWARN && timer < game.GOALIE_DISABLE_TIME) {
            gc.setStroke(new Color(1f, 0f, 0f, .4f));
            bottomText = "GOALIES VANISHING WARNING";
        } else if (timer >= game.GOALIE_DISABLE_TIME && timer < game.GOALIE_DISABLE_TIME - CHWARN) {
            gc.setStroke(new Color(1f, 0f, 0f, .4f));
            bottomText = "GOALIES VANISHED";
        } else if (timer >= (game.options.suddenDeathIndex * 60) - WARN && timer < (game.options.suddenDeathIndex * 60) - FWARN) {
            gc.setStroke(new Color(.9f, .9f, 0f, .6f));
            bottomText = "SUDDEN DEATH WARNING";
        } else if (timer >= (game.options.suddenDeathIndex * 60) - FWARN && timer < (game.options.suddenDeathIndex * 60)) {
            gc.setStroke(new Color(1f, 0f, 0f, .6f));
            bottomText = "SUDDEN DEATH WARNING";
        } else if (timer >= (game.options.suddenDeathIndex * 60) && timer < (game.options.suddenDeathIndex * 60) - CHWARN) {
            gc.setStroke(new Color(1f, 0f, 0f, 1f));
            bottomText = "SUDDEN DEATH ENABLED";
        } else if (timer >= (game.options.tieIndex * 60) - WARN && timer < (game.options.tieIndex * 60) - FWARN) {
            gc.setStroke(new Color(.9f, .9f, 0f, 1f));
            bottomText = "TIE GAME WARNING";
        } else if (timer >= (game.options.tieIndex * 60) - FWARN && timer < (game.options.tieIndex * 60)) {
            gc.setStroke(new Color(1f, 0f, 0f, 1f));
            bottomText = "TIE GAME WARNING";
        } else if (timer >= (game.options.tieIndex * 60) && timer < (game.options.tieIndex * 60) - CHWARN) {
            gc.setStroke(new Color(1f, 0f, 0f, 1f));
            bottomText = "TIE GAME";
        }
    }

    private void setColorFromCharge(GraphicsContext gc, String str) {
        switch (str) {
            case "1":
                gc.setStroke(new Color(.4f, 1f, 0f, .8f));
                break;
            case "2":
                gc.setStroke(new Color(.94f, .90f, .33f, .8f));
                break;
            case "3":
                gc.setStroke(new Color(1f, .15f, .15f, .8f));
                break;
            default:
                gc.setStroke(new Color(.6f, .6f, .6f, .8f));
        }
    }


    public void tutorial(GraphicsContext gc) {
        darkTheme(gc);
        sconst.drawImage(gc, tutorial, 1, 1);
        Font font = new Font("Verdana", 65);
        sconst.setFont(gc, font);
        sconst.drawString(gc, "Space to proceed", 5, 705);
    }

    protected void setColorBasedOnPercent(GraphicsContext gc, double inputPercent, boolean translucent) {
        gc.setStroke(new Color(redColorFromPercent(inputPercent),
                greenColorFromPercent(inputPercent), .54f, translucent ? .5f : 1f));
    }

    protected float redColorFromPercent(double inputPercent) {
        float rColorValue = (float) (.025f * (100f - inputPercent));
        if (rColorValue < 0f) {
            return 0f;
        }
        if (rColorValue > 1f) {
            return 1f;
        }
        return rColorValue;
    }

    protected float greenColorFromPercent(double inputPercent) {
        if (inputPercent < 0) {
            inputPercent = 0.0;
        }
        return (float) (.01f * (inputPercent));
    }

    private void drawTeamScreen(GraphicsContext gc) {
        darkTheme(gc);
        try {
            tournamentCode = tournamentCode.split("/")[0];
        } catch (Exception ex) {
        }
        gc.setStroke(Color.RED);
        sconst.setFont(gc, new Font("Verdana", 32));
        sconst.drawString(gc, "Enter the name of your team to queue up for a game with your teammates", 60, 100);
        gc.setStroke(Color.BLUE);
        sconst.drawString(gc, tournamentCode, 100, 200);
    }

    private void drawTournamentScreen(GraphicsContext gc) {
        darkTheme(gc);
        try {
            tournamentCode = tournamentCode.split("/")[0];
        } catch (Exception ex) {
        }
        gc.setStroke(Color.RED);
        sconst.setFont(gc, new Font("Verdana", 32));
        sconst.drawString(gc, "Type alphabetical game password. Space to proceed, shift to hide letters", 60, 100);
        gc.setStroke(Color.BLUE);
        if (codeHide) {
            String s = "";
            for (int i = 0; i < tournamentCode.length(); i++) {
                s += "*";
            }
            sconst.drawString(gc, s, 100, 200);
        } else {
            sconst.drawString(gc, tournamentCode, 100, 200);
        }
        sconst.setFont(gc, new Font("Verdana", 16));
        sconst.drawString(gc, "This feature is for avoiding random matchmaking. Set the same game password and rules as your friends to join a private lobby", 50, 400);
        sconst.drawString(gc, "Leave the password blank for the default/public lobby", 120, 500);

        gc.setStroke(Color.BLACK);
        darkTheme(gc);
        sconst.setFont(gc, new Font("Verdana", 32));
        sconst.drawString(gc, "Setting Tournament Options", 200, 50);
        sconst.setFont(gc, new Font("Verdana", 16));
        sconst.drawString(gc, "ARROW keys to set options", 200, 420);
        int y = 420;
        for (int i = 0; i < 8; i++) {
            int x = 650;
            gc.setStroke(Color.BLACK);
            darkTheme(gc);
            sconst.drawString(gc, tourneyOptions.disp(i), x, y);
            if (tourneyIndex == i) {
                gc.setStroke(Color.RED);
                double[] xpts = new double[]{sconst.adjX(x - 22), sconst.adjX(x - 30), sconst.adjX(x - 22)};
                double[] ypts = new double[]{sconst.adjY(y - 14), sconst.adjY(y - 6), sconst.adjY(y + 2)};

                Polygon leftArrow = new Polygon();
                leftArrow.getPoints().addAll(xpts[0], ypts[0], xpts[1], ypts[1], xpts[2], ypts[2]);
                sconst.fill(gc, leftArrow);

                double[] xptsRight = new double[]{sconst.adjX(x + 200), sconst.adjX(x + 208), sconst.adjX(x + 200)};
                double[] yptsRight = new double[]{sconst.adjY(y - 14), sconst.adjY(y - 6), sconst.adjY(y + 2)};

                Polygon rightArrow = new Polygon();
                rightArrow.getPoints().addAll(xptsRight[0], yptsRight[0], xptsRight[1], yptsRight[1], xptsRight[2], yptsRight[2]);
                sconst.fill(gc, rightArrow);
            }
            y += 25;
        }
    }

    private void showGameModes(GraphicsContext gc) {
        darkTheme(gc);
        tut = null;
        game = null;
        tut4.rewindStop();
        if (cursor > 4) {
            cursor = 0;
        }
        if (cursor < 0) {
            cursor = 4;
        }

        classCursor = sconst.loadImage("res/Court/draft/cursorflag.png");
        if (cursor == 0) sconst.drawImage(gc, classCursor, 250, 125);
        if (cursor == 1) sconst.drawImage(gc, classCursor, 250, 245);
        if (cursor == 2) sconst.drawImage(gc, classCursor, 250, 365);
        if (cursor == 3) sconst.drawImage(gc, classCursor, 250, 485);
        if (cursor == 4) sconst.drawImage(gc, classCursor, 250, 605);

        sconst.setFont(gc, new Font("Verdana", 24));
        gc.setStroke(Color.color(0.55, 0, 0)); // Equivalent to new Color(.55f, 0f, 0f)
        sconst.drawString(gc, "Navigate Options with W (up) S (down), confirm with SPACE", 350, 55);
        sconst.drawString(gc, "SOLO 3v3", 260, 180);

        gc.setStroke(Color.color(1, 0.1, 0)); // Equivalent to new Color(1f, .1f, 0f)
        sconst.drawString(gc, "SOLO 1v1", 260, 300);

        gc.setStroke(Color.color(1, 0.5, 0)); // Equivalent to new Color(1f, .5f, 0f)
        sconst.setFont(gc, new Font("Verdana", 20));
        sconst.drawString(gc, "TEAM DRAFT", 260, 420);

        gc.setStroke(Color.color(0, 0, 1)); // Equivalent to new Color(0f, 0f, 1f)
        sconst.drawString(gc, "TUTORIAL", 265, 660);

        gc.setStroke(Color.color(0, 1, 0)); // Equivalent to new Color(0f, 1f, 0f)
        sconst.drawString(gc, "CUSTOM GAME", 260, 540); // drawn after to save a refont

        drawTrophies(gc);

    }

    private void drawTrophies(GraphicsContext gc) {
        gc.setStroke(Color.BLACK);
        darkTheme(gc);

        String rankname = "res/Court/rnk/" + loginClient.getRank3v3().toString().toLowerCase() + ".png";
        String rank1v1name = "res/Court/rnk/" + loginClient.getRank1v1().toString().toLowerCase() + ".png";

        Image trophy = sconst.loadImage(rankname);
        Image trophy1v1 = sconst.loadImage(rank1v1name);
        
        // Scale the images to twice their original size
        ImageView trophyView = new ImageView(trophy);
        trophyView.setFitWidth(trophy.getWidth() * 2);
        trophyView.setFitHeight(trophy.getHeight() * 2);
        trophy = trophyView.getImage();
        
        ImageView trophy1v1View = new ImageView(trophy1v1);
        trophy1v1View.setFitWidth(trophy1v1.getWidth() * 2);
        trophy1v1View.setFitHeight(trophy1v1.getHeight() * 2);
        trophy1v1 = trophy1v1View.getImage();
        
        sconst.drawImage(gc, trophy, 458, 115);
        sconst.drawImage(gc, trophy1v1, 458, 235);
        gc.setStroke(Color.YELLOW);
        if (loginClient.getTopten() <= 10) {
            sconst.drawString(gc, "" + loginClient.getTopten(), 432, 170);
        }
        if (loginClient.getTopten1v1() <= 10) {
            sconst.drawString(gc, "" + loginClient.getTopten1v1(), 432, 290);
        }
    }

    private void draftClassScreen(GraphicsContext gc) {
        drawClassScreen(gc, false);
        if (game != null) {
            Image banCursor = sconst.loadImage("res/Court/draft/draftBanned.png");
            Image teamCursor = sconst.loadImage("res/Court/draft/draftPicked.png");
            Image oppCursor = sconst.loadImage( "res/Court/draft/draftPicked2.png");
            if (game.picksAndBans.containsKey("ban1")) {
                int banIndex = classSelIndex(game.picksAndBans.get("ban1"));
                sconst.drawImage(gc, banCursor, xCursor(banIndex), yCursor(banIndex));
            }
            if (game.picksAndBans.containsKey("ban2")) {
                int banIndex = classSelIndex(game.picksAndBans.get("ban2"));
                sconst.drawImage(gc, banCursor, xCursor(banIndex), yCursor(banIndex));
            }
            if (game.picksAndBans.containsKey("home1") && game.yourteam == TeamAffiliation.HOME ||
                    game.picksAndBans.containsKey("away1") && game.yourteam == TeamAffiliation.AWAY) {
                int pickIndex = game.yourteam == TeamAffiliation.HOME ?
                        classSelIndex(game.picksAndBans.get("home1")) :
                        classSelIndex(game.picksAndBans.get("away1"));
                sconst.drawImage(gc, teamCursor, xCursor(pickIndex), yCursor(pickIndex));
            }
            if (game.picksAndBans.containsKey("home1") && game.yourteam == TeamAffiliation.AWAY ||
                    game.picksAndBans.containsKey("away1") && game.yourteam == TeamAffiliation.HOME) {
                int pickIndex = game.yourteam == TeamAffiliation.HOME ?
                        classSelIndex(game.picksAndBans.get("away1")) :
                        classSelIndex(game.picksAndBans.get("home1"));
                sconst.drawImage(gc, oppCursor, xCursor(pickIndex), yCursor(pickIndex));
            }
            if (game.picksAndBans.containsKey("home2") && game.yourteam == TeamAffiliation.HOME ||
                    game.picksAndBans.containsKey("away2") && game.yourteam == TeamAffiliation.AWAY) {
                int pickIndex = game.yourteam == TeamAffiliation.HOME ?
                        classSelIndex(game.picksAndBans.get("home2")) :
                        classSelIndex(game.picksAndBans.get("away2"));
                sconst.drawImage(gc, teamCursor, xCursor(pickIndex), yCursor(pickIndex));
            }
            if (game.picksAndBans.containsKey("home2") && game.yourteam == TeamAffiliation.HOME ||
                    game.picksAndBans.containsKey("away2") && game.yourteam == TeamAffiliation.AWAY) {
                int pickIndex = game.yourteam == TeamAffiliation.HOME ?
                        classSelIndex(game.picksAndBans.get("away1")) :
                        classSelIndex(game.picksAndBans.get("home1"));
                sconst.drawImage(gc, oppCursor, xCursor(pickIndex), yCursor(pickIndex));
            }
            if (game.picksAndBans.containsKey("home3") && game.yourteam == TeamAffiliation.HOME) {
                int pickIndex = classSelIndex(game.picksAndBans.get("home3"));
                sconst.drawImage(gc, teamCursor, xCursor(pickIndex), yCursor(pickIndex));
            }
            if (game.picksAndBans.containsKey("home3") && game.yourteam == TeamAffiliation.AWAY) {
                int pickIndex = classSelIndex(game.picksAndBans.get("home3"));
                sconst.drawImage(gc, oppCursor, xCursor(pickIndex), yCursor(pickIndex));
            }
            if(game.phase == GamePhase.DRAFT_HOMEBAN || game.phase == GamePhase.DRAFT_AWAYBAN){
                classCursor = sconst.loadImage("res/Court/draft/draftBan.png");
            }else{
                classCursor = sconst.loadImage("res/Court/draft/draft.png");
            }
            for (int i = 0; i <= 16; i++) {
                if (cursor == i)
                    sconst.drawImage(gc, classCursor, xCursor(i), yCursor(i));
            }
        }
    }

    protected void drawClassScreen(GraphicsContext gc, boolean displayCursor) {
        tut = null;
        game = null;
        tut4.rewindStop();
        darkTheme(gc);
        sconst.drawImage(gc, select, 0, 0);
        sconst.setFont(gc, new Font("Verdana", 24));
        gc.setStroke(Color.RED);
        sconst.drawString(gc, "Navigate characters with WASD, confirm with SPACE", 350, 155);
        if (staticFrameCounter == 0) {
            if (staticFrame == 2) {
                staticFrame = 3;
            } else {
                staticFrame = 2;
            }
        }
        staticFrameCounter++;
        if (staticFrameCounter == 9) {
            staticFrameCounter = 0;
        }
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.WARRIOR, staticFrame), 164, 200);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.RANGER, staticFrame), 364, 200);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.MAGE, staticFrame), 564, 200);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.HOUNDMASTER, staticFrame), 764, 200);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.MARKSMAN, staticFrame), 164, 320);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.DASHER, staticFrame), 364, 320);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.GOLEM, staticFrame), 564, 320);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.STEALTH, staticFrame), 764, 320);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.SUPPORT, staticFrame), 164, 440);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.BUILDER, staticFrame), 364, 440);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.ARTISAN, staticFrame), 564, 440);
        sconst.drawImage(gc, SelectClassSkins.pullImage(gc, TitanType.GRENADIER, staticFrame), 764, 440);
        classFacts(gc);
        if (cursor > 12) {
            cursor %= 12;
        }
        if (cursor <= 0) {
            cursor = -1 * (cursor);
            if (cursor == 0) {
                cursor = 1;
            }
        }
        classCursor = sconst.loadImage("res/Court/draft/cursorflag.png");
        if (displayCursor) {
            for (int i = 0; i <= 16; i++) {
                if (cursor == i)
                    sconst.drawImage(gc, classCursor, xCursor(i), yCursor(i));
            }
        }
    }

    private int classSelIndex(TitanType index) {
        switch (index) {
            case WARRIOR:
                return 1;
            case RANGER:
                return 2;
            case MAGE:
                return 3;
            case HOUNDMASTER:
                return 4;
            case MARKSMAN:
                return 5;
            case DASHER:
                return 6;
            case GOLEM:
                return 7;
            case STEALTH:
                return 8;
            case SUPPORT:
                return 9;
            case BUILDER:
                return 10;
            case ARTISAN:
                return 11;
            case GRENADIER:
                return 12;
        }
        return 0;
    }

    private int xCursor(int index) {
        index -= 1;
        index %= 4;
        return 200 * index + 150;
    }

    private int yCursor(int index) {
        index -= 1;
        index /= 4;
        return 120 * index + 195;
    }

    protected class TerminableExecutor implements Runnable {
        GameEngine context;
        ScheduledExecutorService exec;

        TerminableExecutor(GameEngine gm, ScheduledExecutorService exec) {
            this.context = gm;
            this.exec = exec;
        }

        @Override
        public void run() {
            if (context.ended) {
                controlsHeld.classSelection = null;
                System.out.println("suspending game thread");
                exec.shutdown();
            } else {
                try {
                    controlsHeld.classSelection = TitanType.RANGER;
                    context.processClientPacket(
                            context.clients.get(0), controlsHeld);
                    context.gameTick();
                } catch (Exception ex1) {
                    ex1.printStackTrace();
                }
            }
        }
    }
}
