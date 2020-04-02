package client;

import client.graphical.*;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.rits.cloning.Cloner;
import gameserver.Const;
import gameserver.TutorialOverrides;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownE;
import gameserver.effects.cooldowns.CooldownR;
import gameserver.effects.effects.*;
import gameserver.engine.*;
import gameserver.entity.Entity;
import gameserver.entity.RangeCircle;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.*;
import gameserver.models.Game;
import gameserver.targeting.ShapePayload;
import gameserver.tenancy.GamePhase;
import networking.ClientPacket;
import networking.KryoRegistry;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.json.JSONObject;
import util.Util;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TitanballClient extends JPanel implements ActionListener, KeyListener {
    protected final ScreenConst sconst;
    protected final BufferedImage ballLobTexture;
    private final TitanballWindow parentWindow;
    private int RANGE_SIZE = 0;
    private int SHOT_WIDTH =0;
    public ClientPacket controlsHeld = new ClientPacket();
    public Timer timer = new Timer(25, this);
    public Instant gamestart = null;
    public Random rand;
    public Sound shotSound;
    protected Client gameserverConn = new Client(8192 * 8, 32768 * 8);
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
    int crowdFrame = 1;//not used yet
    int crowdFrameCount = 0;
    File shotSoundFile = new File("res/Sound/shotsound.wav");
    //File t4File = new File("res/Sound/tut4.wav");
    StaticImage intro = new StaticImage();
    Images ballTexture = new Images();
    Images ballBTexture = new Images();
    Images ballFTexture = new Images();
    Images ballFBTexture = new Images();
    Images ballPtr = new Images();
    Images ballFPtr = new Images();
    int xSize, ySize;
    ControlsConfig controlsConfig = new ControlsConfig();
    StaticImage select = new StaticImage();
    StaticImage lobby = new StaticImage();
    StaticImage classCursor = new StaticImage();
    StaticImage field = new StaticImage();
    StaticImage selector = new StaticImage();
    StaticImage goalScored = new StaticImage();
    StaticImage victory = new StaticImage();
    StaticImage defeat = new StaticImage();
    StaticImage backdrop = new StaticImage();
    StaticImage tie = new StaticImage();
    StaticImage tutorial = new StaticImage();
    StaticImage trap1 = new StaticImage();
    StaticImage trap2 = new StaticImage();
    StaticImage portal1 = new StaticImage();
    StaticImage portal2 = new StaticImage();
    StaticImage portalcd = new StaticImage();
    StaticImage bportal1 = new StaticImage();
    StaticImage bportal2 = new StaticImage();
    StaticImage bportalcd = new StaticImage();
    StaticImage fire1 = new StaticImage();
    StaticImage fire2 = new StaticImage();
    StaticImage cage = new StaticImage();
    StaticImage wall = new StaticImage();
    StaticImage wolf1L = new StaticImage();
    StaticImage wolf2L = new StaticImage();
    StaticImage wolf3L = new StaticImage();
    StaticImage wolf5L = new StaticImage();
    StaticImage wolf1R = new StaticImage();
    StaticImage wolf2R = new StaticImage();
    StaticImage wolf3R = new StaticImage();
    StaticImage wolf5R = new StaticImage();
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

    public TitanballClient(TitanballWindow titanballWindow, int xSize, int ySize, double scl, HttpClient loginClient, Map<String, String> keymap, boolean createListeners, boolean darkTheme) {
        this.darkTheme = darkTheme;
        this.parentWindow = titanballWindow;
        this.xSize = xSize;
        this.ySize = ySize;
        this.scl = scl;
        RANGE_SIZE = Integer.parseInt(keymap.get("rangewidth").replaceAll("px",""));
        SHOT_WIDTH = Integer.parseInt(keymap.get("shotwidth").replaceAll("px",""));
        sconst = new ScreenConst(xSize, ySize);
        this.loginClient = loginClient;
        sconst.loadImage(intro, "res/Court/logo2.png", this);
        sconst.loadImage(ballTexture, "res/Court/ballA.png", this);
        sconst.loadImage(ballBTexture, "res/Court/ballB.png", this);
        BufferedImage tmp = Images.getBufferedFrom(ballTexture.image, 30, 30);
        ballLobTexture = Images.resize(tmp, 45, 45);
        sconst.loadImage(ballFTexture,"res/Court/ballFA.png", this);
        sconst.loadImage(ballFBTexture,"res/Court/ballFB.png", this);
        sconst.loadImage(ballPtr,"res/Court/ballptr.png", this);
        sconst.loadImage(ballFPtr,"res/Court/ballfptr.png", this);
        sconst.loadImage(select,"res/Court/selectClass.png", this);
        sconst.loadImage(lobby,"res/Court/lobby.png", this);
        sconst.loadImage(field,"res/Court/field.png", this);
        sconst.loadImage(selector,"res/Court/select.png", this);
        sconst.loadImage(backdrop,"res/Court/backdrop.png", this);
        sconst.loadImage(goalScored,"res/Court/goal.png", this);
        sconst.loadImage(victory,"res/Court/victory.png", this);
        sconst.loadImage(tie,"res/Court/tie.png", this);
        sconst.loadImage(defeat,"res/Court/defeat.png", this);
        if(darkTheme){
            sconst.loadImage(tutorial,"res/Court/tutorial_whitetxt.png", this);
        }else{
            sconst.loadImage(tutorial,"res/Court/tutorial.png", this);
        }
        sconst.loadImage(wall,"res/Court/wall.png", this);
        sconst.loadImage(portal1,"res/Court/portal.png", this);
        sconst.loadImage(portal2,"res/Court/portal2.png", this);
        sconst.loadImage(portalcd,"res/Court/portalcd.png", this);

        sconst.loadImage(bportal1,"res/Court/ballp.png", this);
        sconst.loadImage(bportal2,"res/Court/ballp2.png", this);
        sconst.loadImage(bportalcd,"res/Court/ballpcd.png", this);

        sconst.loadImage(fire1,"res/Court/fireA.png", this);
        sconst.loadImage(fire2,"res/Court/fireB.png", this);
        sconst.loadImage(cage,"res/Court/caged.png", this);

        sconst.loadImage(trap1,"res/Court/trap.png", this);
        sconst.loadImage(trap2,"res/Court/trap2.png", this);

        sconst.loadImage(wolf1L,"res/Wolf/wolfL.png", this);
        sconst.loadImage(wolf2L,"res/Wolf/wolf2L.png", this);
        sconst.loadImage(wolf3L,"res/Wolf/wolf3L.png", this);
        sconst.loadImage(wolf5L,"res/Wolf/wolf5L.png", this);
        sconst.loadImage(wolf1R,"res/Wolf/wolfR.png", this);
        sconst.loadImage(wolf2R,"res/Wolf/wolf2R.png", this);
        sconst.loadImage(wolf3R,"res/Wolf/wolf3R.png", this);
        sconst.loadImage(wolf5R,"res/Wolf/wolf5R.png", this);
        initSurface(createListeners);
        requestFocus();
        requestFocusInWindow();
        setLayout(new BorderLayout());
        System.out.println("creating new listeners");
        createListeners();
        fireReconnect();
    }

    private void createListeners() {
        MouseMotionListener mouseMvtListener = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                controlsHeld.posX = sconst.invertMouseX(e.getPoint().x);
                controlsHeld.posY = sconst.invertMouseY(e.getPoint().y);
                controlsHeld.camX = camX;
                controlsHeld.camY = camY;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                controlsHeld.posX = sconst.invertMouseX(e.getPoint().x);
                controlsHeld.posY = sconst.invertMouseY(e.getPoint().y);
                controlsHeld.camX = camX;
                controlsHeld.camY = camY;
            }
        };

        TitanballClient clientSelf = this;
        MouseListener mouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
                    controlsHeld.posX = sconst.invertMouseX(event.getPoint().x);
                    controlsHeld.posY = sconst.invertMouseY(event.getPoint().y);
                    if (event.getButton() == 1) {
                        controlsConfig.mapKeyPress(clientSelf.game, controlsHeld, "LMB", clientSelf.shotSound);
                    }
                    if (event.getButton() == 3) {
                        controlsConfig.mapKeyPress(clientSelf.game, controlsHeld, "RMB", clientSelf.shotSound);
                    }
                    controlsHeld.camX = camX;
                    controlsHeld.camY = camY;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                    controlsConfig.mapKeyRelease(controlsHeld, "LMB");
                    controlsConfig.mapKeyRelease(controlsHeld, "RMB");
            }
        };
        addMouseListener(mouseListener);
        addMouseMotionListener(mouseMvtListener);
        JButton button = new JButton("Click me!");
        button.addMouseListener(mouseListener);
    }

    public int indexSelected() {
        for (int i = 0; i < game.players.length; i++) {
            if (game.players[i].id.equals(game.underControl.id)) {
                return i + 1;
            }
        }
        return -1;
    }

    public void darkTheme(Graphics2D g2D, boolean backdropShow){
        if(darkTheme && backdropShow){
            sconst.drawImage(g2D, backdrop.getImage(), 0, 0, this);
        }
        if(darkTheme){
            g2D.setColor(new Color(1f, 1f, 1f));
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2D = (Graphics2D) g;
        super.paintComponent(g);
        if (game != null && game.ended) {
            darkTheme(g2D, true);
            Team team = teamFromUnderControl();
            Team enemy = enemyFromUnderControl();
            if(queued){
                loginClient.leave();
                queued = false;
            }
            if (team.score > enemy.score) {
                sconst.drawImage(g2D, victory.getImage(), sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y, this);
            } else if(team.score == enemy.score){
                sconst.drawImage(g2D, tie.getImage(), sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y, this);
            } else{
                sconst.drawImage(g2D, defeat.getImage(), sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y, this);
            }
            gameEndStatsAndRanks(g2D, game.underControl);
            return;
        }
        repaint();
        if (phase == GamePhase.CREDITS) creditPanel(g2D);
        if (phase == GamePhase.CONTROLS) tutorial(g2D);
        if (phase == GamePhase.SHOW_GAME_MODES) showGameModes(g2D);
        if (phase == GamePhase.DRAW_CLASS_SCREEN) drawClassScreen(g2D); //Attack screen settings starting here!
        if (phase == GamePhase.SET_MASTERIES) {
            if(tournamentCode.equals("")){
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
            drawSetMasteries(g2D);
        }
        if (phase == GamePhase.TRANSITIONAL){
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
                lobby(g2D);
                System.out.println("got game " + gameID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (phase == GamePhase.COUNTDOWN) {
            starting(g2D);
        }
        if (phase == GamePhase.INGAME|| phase == GamePhase.SCORE_FREEZE) {
            if(this.game.effectPool.hasEffect(this.game.underControl, EffectId.BLIND)){
                sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 72));
                g2D.setColor(new Color(.2f, .2f, .2f));
                sconst.drawString(g2D,"Blind!", 450, 300);
            }else{
                updateFrameBall();
                doDrawing(g2D); // do drawing of screen
                displayBallArrow(g2D);
                displayScore(g2D); // call the method to display the game score
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
        if (phase ==  GamePhase.TUTORIAL) {
            this.game = tut;
            repaint();
            updateFrameBall();
            updateNarration(tut);
            if(this.game.phase == GamePhase.SHOW_GAME_MODES){ //finish tutorial
                try {
                    parentWindow.reset(true);
                    controlsHeld.classSelection = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else{
                doDrawing(g2D);
                displayBallArrow(g2D);
                displayScore(g2D); // call the method to display the game score
            }
        }
        if (phase == GamePhase.TOURNAMENT_CODE) {
            drawTournamentScreen(g2D);
        }
        Toolkit.getDefaultToolkit().sync();
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
    static{
        tut0.loadSound(t0File);
        tut1.loadSound(t1File);
        tut2.loadSound(t2File);
        tut3.loadSound(t3File);
        tut4.loadSound(t4File);
    }

    private void updateNarration(TutorialOverrides game) {
        if(game.tutorialPhase ==  1 && game.narrationPhase ==  0){
            tut0.rewindStart();
            game.narrationPhase++;
        }
        if(game.tutorialPhase ==  2 && game.narrationPhase ==  1){
            tut0.rewindStop();
            tut1.rewindStart();
            game.narrationPhase++;
        }
        if(game.tutorialPhase ==  3 && game.narrationPhase ==  2){
            tut1.rewindStop();
            tut2.rewindStart();
            game.narrationPhase++;
        }
        if(game.tutorialPhase ==  4 && game.narrationPhase ==  3){
            tut2.rewindStop();
            tut3.rewindStart();
            game.narrationPhase++;
        }
        if(game.tutorialPhase ==  5 && game.narrationPhase ==  4){
            tut3.rewindStop();
            tut4.rewindStart();
            game.narrationPhase++;
        }
    }

    protected void drawSetMasteries(Graphics2D g2D) {
        g2D.setColor(Color.BLACK);
        darkTheme(g2D, true);
        sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 32));
        sconst.drawString(g2D,"Setting Masteries", 200, 50);
        sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 16));
        sconst.drawString(g2D,"Remaining Points", 200, 90);
        sconst.drawString(g2D,"WASD or arrows to spend points", 200, 420);
        sconst.drawString(g2D,"Space to enter (And queue for game)", 200, 450);
        int x = 430;
        for (int i = masteries.skillsRemaining(); i > 0; i--) {
            g2D.fill(new Ellipse2D.Double(x, 76, 16, 16));
            x += 32;
        }
        int y = 150;
        int[] arr = masteries.asArray();
        for (int i = 0; i < arr.length; i++) {
            x = 250;
            g2D.setColor(Color.BLACK);
            darkTheme(g2D, false);
            sconst.drawString(g2D,Masteries.masteryFromIndex(i), x, y);
            if (masteriesIndex == i) {
                g2D.setColor(Color.RED);
                sconst.fill(g2D, new Rectangle(x - 30, y - 14, 16, 16));
            }
            x = 430;
            for (int ballNum = 3; ballNum > 0; ballNum--) {
                setColorFromRank(g2D, ballNum);
                if (3 - ballNum < arr[i]) {
                    sconst.fill(g2D,new Ellipse2D.Double(x, y - 14, 16, 16));
                }
                x += 25;
            }
            y += 25;
        }
    }

    protected void setColorFromRank(Graphics2D g2D, int rank) {
        if (rank == 1) {
            g2D.setColor(new Color(1f, .843f, 0f));//gold silver bronze
        }
        if (rank == 2) {
            g2D.setColor(new Color(.753f, .753f, .753f));//gold silver bronze
        }
        if (rank == 3) {
            g2D.setColor(new Color(.804f, .498f, .196f));//gold silver bronze
        }
    }

    protected void gameEndStatsAndRanks(Graphics2D g2D, Titan underControl) {
        JSONObject stats = game.stats.statsOf(game.clientFromTitan(underControl));
        JSONObject ranks = game.stats.ranksOf(game.clientFromTitan(underControl));
        int y = 425;
        Font font = new Font("Verdana", Font.PLAIN, sconst.STATS_FONT);
        sconst.setFont(g2D, font);
        for (String stat : stats.keySet()) {
            g2D.setColor(Color.BLACK);
            darkTheme(g2D, false);
            sconst.drawString(g2D,stat + ": " + stats.get(stat), 310, y);
            if (ranks.has(stat) && ((int) ranks.get(stat) >= 1)) {
                int rank = (int) ranks.get(stat);
                setColorFromRank(g2D, rank);
                g2D.fill(new Ellipse2D.Double(sconst.STATS_MEDAL + 1, //subtractions for internal color circle
                        y - (sconst.STATS_FONT - 4) + 1,
                        sconst.STATS_FONT - 2, sconst.STATS_FONT - 2));
                g2D.setColor(Color.BLACK);
                g2D.draw(new Ellipse2D.Double(sconst.STATS_MEDAL,
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
        gameserverConn.start();
        //gameserverConn.setHardy(true);
        if( !gameserverConn.isConnected()){
            gameserverConn.connect(999999999, "zanzalaz.com", 54555);
            //gameserverConn.connect(5000, "127.0.0.1", 54555);
            gameserverConn.addListener(new Listener() {
                public synchronized void received(Connection connection, Object object) {
                    if (object instanceof Game) {
                        game = (GameEngine) object;
                        game.began = true;
                        phase = game.phase;
                        controlsHeld.gameID = gameID;
                        controlsHeld.token = token;
                        controlsHeld.masteries = masteries;
                        repaint();
                        try {
                            controlsHeld.camX = camX;
                            controlsHeld.camY = camY;
                            gameserverConn.sendTCP(controlsHeld); //Automatically respond to the gameserver with tutorial when we get a new state
                        } catch (KryoException e) {
                            System.out.println("kryo end");
                            System.out.println(game.ended);
                        }
                    } else {
                        System.out.println("Didn't get a game from gameserver!");
                    }
                }
            });
            KryoRegistry.register(kryo);
            ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
            Runnable updateServer = () -> {
                controlsHeld.gameID = gameID;
                controlsHeld.token = token;
                controlsHeld.masteries = masteries;
                gameserverConn.sendTCP(controlsHeld);
            };
            exec.scheduleAtFixedRate(updateServer, 1, 30, TimeUnit.MILLISECONDS);
        }
    }

    protected String requestOrQueueGame() throws UnirestException {
        if(!queued){
            loginClient.join(this.tournamentCode);
            token = loginClient.token;
            queued = true;
        }else{
            loginClient.check();
            token = loginClient.token;
        }
        if (loginClient.gameId != null && loginClient.gameId.equals("NOT QUEUED")) {
            throw new UnirestException("Server not accepting connections");
        }
        if(loginClient.gameId != null && !loginClient.gameId.equals("WAITING")){
            this.gameID = loginClient.gameId;
            this.phase = GamePhase.COUNTDOWN;
        }
        return loginClient.gameId;
    }

    protected void creditPanel(Graphics2D g2D) {
        darkTheme(g2D, true);
        sconst.drawImage(g2D, intro.getImage(), 0, 0, this);
        Font font = new Font("Verdana", Font.PLAIN, 65);
        sconst.setFont(g2D, font);
        sconst.drawString(g2D,"Space to proceed", 370, 640);
    }

    public void rotTranslateArrow(Graphics2D g2D, int xt, int yt, double rot) {
        // create the transform, note that the transformations happen
        // in reversed order (so check them backwards)
        AffineTransform at = new AffineTransform();
        // 4. translate it to the center of the component
        at.translate(xt, yt);
        // 3. do the actual rotation
        at.rotate(Math.toRadians(rot));
        // 1. translate the object so that you rotate it around the center (easier :))
        at.translate(-(sconst.BALL_PTR_X / 2), -(sconst.BALL_PTR_Y / 2));
        if (game.anyPoss()) {
            sconst.drawImage(g2D, ballPtr.getImage(), at, this);
        } else {
            sconst.drawImage(g2D, ballFPtr.getImage(), at, this);
        }
    }

    public void displayBallArrow(Graphics2D g2D) {
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
            rotTranslateArrow(g2D, sconst.BALL_PTR_X / 2, yt, rot);
        } else if (x > xSize - XCORR) {
            int yt = y;
            if (y < 0) {//diag
                rot = 315;
                yt = 20;
            } else if (y > ySize - YCORR) {//diag
                rot = 45;
                yt = ySize - YCORR - (sconst.BALL_PTR_X / 2);
            }
            rotTranslateArrow(g2D, xSize - XCORR - (sconst.BALL_PTR_X / 2), yt, rot);
        } else if (y < 0) {
            rot = 270;
            rotTranslateArrow(g2D, x, (sconst.BALL_PTR_X / 2), rot);
        } else if (y > ySize - YCORR) {
            rot = 90;
            rotTranslateArrow(g2D, x, ySize - YCORR - (sconst.BALL_PTR_X / 2), rot);
        }
    }

    public void postgoalReset() {
        crowdFrame = 1;
        crowdFrameCount = 0;
        ballFrame = 0;
        ballFrameCounter = 0;
        camX = 500;
        camY = 300;
    }

    protected void doDrawing(Graphics2D g2D) {
        if (game == null) {
            return;
        }
        if (instructionToggle) {
            tutorial(g2D);
            return;
        }
        sconst.drawImage(g2D, field.getImage(), (1 - camX), (1 - camY), this);
        if (crowdFrame == 2) {
            //TODO animation
        }
        g2D.setStroke(new BasicStroke(6f));
        for (GoalHoop goalData : game.lowGoals) {
            GoalSprite goal = new GoalSprite(goalData, camX, camY, sconst);
            Team enemy;
            if (goal.team == TeamAffiliation.HOME) {
                enemy = game.away;
            } else { //(goal.team == TeamAffiliation.AWAY)
                enemy = game.home;
            }
            if (!goal.checkReady()) {
                g2D.setPaint(Color.RED);
                if (goal.frozen) {
                    g2D.setPaint(new Color(.149f, .929f, .851f));//light blue
                }
            } else if (enemy.score % 1.0 == .75) {
                g2D.setPaint(new Color(.811f, .631f, .098f));
            } else {
                g2D.setPaint(Color.LIGHT_GRAY);
            }
            g2D.draw(goal);
        }
        for (GoalHoop goalData : game.hiGoals) {
            GoalSprite goal = new GoalSprite(goalData, camX, camY, sconst);
            Team enemy;
            if (goal.team == TeamAffiliation.HOME) {
                enemy = game.away;
            } else { //(goal.team == TeamAffiliation.AWAY)
                enemy = game.home;
            }
            g2D.setPaint(Color.DARK_GRAY);
            if (enemy.score % 1.0 == .75) {
                g2D.setPaint(Color.GREEN);
            }
            g2D.draw(goal);
        }
        drawPainHealIndicator(g2D, game);
        ArrayList<RangeCircle> clientCircles = new ArrayList<>();
        for (RangeCircle ri : game.underControl.rangeIndicators) {
            clientCircles.add(ri);
        }
        g2D.setStroke(new BasicStroke(SHOT_WIDTH));
        if (game.underControl.possession == 1) {
            double pow = game.underControl.throwPower;
            double mx = controlsHeld.posX + camX;
            double my = controlsHeld.posY + camY;
            double ox = game.ball.X + game.ball.width/2 - camX;
            double oy = game.ball.Y + game.ball.height/2 - camY;
            ox = sconst.adjX(ox);
            oy = sconst.adjY((int)oy);
            double angle = Math.toRadians(Util.degreesFromCoords(mx - game.ball.X - game.ball.width/2,
                    my - game.ball.Y - game.ball.height/2));
            final double LOB_DIST = sconst.adjX(230);
            final double SHOT_DIST = sconst.adjX(316);
            final double BALL_HALF = sconst.adjX(15);
            Line2D lobBlock = new Line2D.Double(ox,
                    oy,
                    ox + ((.2 * LOB_DIST * pow +BALL_HALF) * Math.cos(angle)),
                    oy + ((.2 * LOB_DIST * pow +BALL_HALF) * Math.sin(angle)));
            g2D.setColor(Color.YELLOW);
            reflectDraw(lobBlock, mx, my, g2D);
            g2D.draw(lobBlock);

            Line2D lobFly = new Line2D.Double(ox + ((.2 * LOB_DIST * pow + BALL_HALF) * Math.cos(angle)),
                    oy + ((.2 * LOB_DIST * pow + BALL_HALF) * Math.sin(angle)),
                    ox + ((.75 * LOB_DIST * pow -BALL_HALF) * Math.cos(angle)),
                    oy + ((.75 * LOB_DIST * pow -BALL_HALF) * Math.sin(angle)));
            g2D.setColor(Color.BLUE);
            reflectDraw(lobFly, mx, my, g2D);
            g2D.draw(lobFly);

            Line2D lobCatch = new Line2D.Double(ox + ((.75 * LOB_DIST * pow -BALL_HALF) * Math.cos(angle)),
                    oy + ((.75 * LOB_DIST * pow -BALL_HALF) * Math.sin(angle)),
                    ox + (LOB_DIST * pow * Math.cos(angle)),
                    oy + (LOB_DIST * pow * Math.sin(angle)));
            g2D.setColor(Color.YELLOW);
            reflectDraw(lobCatch, mx, my, g2D);
            g2D.draw(lobCatch);
            if(this.controlsHeld.artisanShot == ClientPacket.ARTISAN_SHOT.SHOT
                    || ! game.underControl.getType().equals(TitanType.ARTISAN)){
                Line2D shot = new Line2D.Double(ox + (LOB_DIST * pow * Math.cos(angle)),
                        oy + (LOB_DIST * pow * Math.sin(angle)),
                        ox + (SHOT_DIST * pow * Math.cos(angle)),
                        oy + (SHOT_DIST * pow * Math.sin(angle)));
                g2D.setColor(new Color(.65f, 0f, 0f));
                reflectDraw(shot, mx, my, g2D);
                g2D.draw(shot);
            }
            double Q_CURVE_A = sconst.adjX(310);
            double Q_CURVE_B = sconst.adjX(186);
            if (game.underControl.possession == 1 && game.underControl.getType() == TitanType.ARTISAN) {
                if(this.controlsHeld.artisanShot == ClientPacket.ARTISAN_SHOT.LEFT) {
                    QuadCurve2D eL = new QuadCurve2D.Double(ox, oy,
                            ox + (Q_CURVE_A * pow * Math.cos(angle - .97)),
                            oy + (Q_CURVE_A * pow * Math.sin(angle - .97)),
                            ox + (Q_CURVE_B * pow * Math.cos(angle)),
                            oy + (Q_CURVE_B * pow * Math.sin(angle)));
                    g2D.setColor(Color.GREEN);
                    g2D.draw(eL);
                }
                if(this.controlsHeld.artisanShot == ClientPacket.ARTISAN_SHOT.RIGHT) {
                    QuadCurve2D eR = new QuadCurve2D.Double(ox, oy,
                            ox + (Q_CURVE_A * pow * Math.cos(angle + .97)),
                            oy + (Q_CURVE_A * pow * Math.sin(angle + .97)),
                            ox + (Q_CURVE_B * pow * Math.cos(angle)),
                            oy + (Q_CURVE_B * pow * Math.sin(angle)));
                    g2D.setColor(new Color(.45f, .0f, .85f));
                    g2D.draw(eR);
                }
            }
        } else {
            RangeCircle steal = new RangeCircle(
                    new Color(.25f, .75f, .75f), game.underControl.stealRad);
            clientCircles.add(steal);
        }

        drawEntities(g2D);
        if (game.ballVisible == true) {
            if (game.anyPoss()) {
                if (ballFrame == 0) {
                    sconst.drawImage(g2D, ballTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
                if (ballFrame == 1) {
                    sconst.drawImage(g2D, ballBTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
            } else {
                if (ballLobMode()) {
                    sconst.drawImage(g2D, ballLobTexture, ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                } else if (ballFrame == 0) {
                    sconst.drawImage(g2D, ballFTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                } else if (ballFrame == 1) {
                    sconst.drawImage(g2D, ballFBTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
            }
        }
        for (RangeCircle ri : clientCircles) { //draw these on top of enemies, but the shot underneath
            g2D.setStroke(new BasicStroke(RANGE_SIZE));
            g2D.setColor(ri.getColor());
            Titan t = game.underControl;
            if (ri.getRadius() > 0) {
                //don't show Artisan Suck with ball
                if (t.getType() != TitanType.ARTISAN || t.possession == 0 || ri.getColor().getGreen() < 250) {
                    int w = (int) (ri.getRadius() * 2 * t.rangeFactor);
                    int h = w;
                    int x = (int) t.X + (t.width / 2) - w / 2;
                    int y = (int) t.Y + (t.height / 2) - h / 2;
                    x = (int) sconst.adjX(x - camX);
                    y = sconst.adjY(y - camY);
                    h = sconst.adjY(h);
                    w = (int) sconst.adjX(w);
                    Ellipse2D.Double ell = new Ellipse2D.Double(x, y, w, h);
                    ShapePayload c = new ShapePayload(ell);
                    g2D.draw(c.from());
                }
            }
        }
        drawPortalRanges(g2D);
        if (game.goalVisible == true) {
            g2D.drawImage(goalScored.getImage(), sconst.GOAL_TXT_X, sconst.GOAL_TXT_Y, this);
        }
        if (camFollow) {
            camX = (int) (game.underControl.X + 35 - (this.xSize / 3 / 1.5 * scl));
            //if (camX > 820) camX = 820;
            if (camX < 0) camX = 0;
            camY = (int) (game.underControl.Y + 35 - (this.ySize / 3 / 1.5 * scl));
            //if (camY > 480) camY = 480;
            if (camY < 0) camY = 0;
        }
        if (game.colliders != null) {
            g2D.setStroke(new BasicStroke(6));
            for (ShapePayload c : game.colliders) {
                g2D.setColor(c.getColor());
                Shape b = c.fromWithCamera(camX, camY, sconst);
                g2D.draw(b);
            }
        }
    }

    private void drawPortalRanges(Graphics2D g2D) {
        for(Entity e : game.entityPool){
            RangeCircle ri = null;
            if(e instanceof BallPortal && game.underControl.id.equals(((BallPortal) e).getCreatedById())){
                ri = ((BallPortal) e).rangeCircle;
            }
            if(e instanceof Portal && game.underControl.id.equals(((Portal) e).getCreatedById())){
                ri = ((Portal) e).rangeCircle;
            }
            if(ri != null){
                int w = (int) (ri.getRadius() * 2);
                int h = w;
                int x = (int) e.X + (e.width / 2) - w / 2;
                int y = (int) e.Y + (e.height / 2) - h / 2;
                x = (int) sconst.adjX(x - camX);
                y = sconst.adjY(y - camY);
                h = sconst.adjY(h);
                w = (int) sconst.adjX(w);
                Ellipse2D.Double ell = new Ellipse2D.Double(x, y, w, h);
                ShapePayload c = new ShapePayload(ell);
                g2D.setColor(ri.getColor());
                g2D.draw(c.from());
            }
        }
    }

    private void drawPainHealIndicator(Graphics2D g2D, GameEngine game) {
        for (int sel = 1; sel <= game.players.length; sel++) {
            if (indexSelected() == sel) {
                Titan e = game.players[sel - 1];
                GoalHoop[] pains = game.getPainHoopsFromTeam(e.team);
                for (GoalHoop pain : pains) {
                    double delta = Util.calculatePain(e, pain);
                    g2D.setStroke(new BasicStroke(4.0f));
                    if (delta > 0) {
                        double percent = 75 - (7.5*delta);
                        if(percent < 0){
                            percent = 0.1;
                        }
                        setColorBasedOnPercent(g2D, percent, false);
                        g2D.drawLine(0,0,1920,0);
                    }
                    final double FPS = 1000 / game.GAMETICK_MS;
                    double time = (game.framesSinceStart / FPS);
                    time = (int)(time*10.0) / 10.0;
                    if (delta < -3 && time < game.PAIN_DISABLE_TIME) {
                        if(e.possession == 1){
                            g2D.setColor(Color.BLUE);
                        }else{
                            g2D.setColor(Color.GREEN);
                        }
                        g2D.drawLine(0,0,1920,0);
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

    private void reflectDraw(Line2D pass, double tx, double ty, Graphics2D g2D) {
        double dx = pass.getX2() - pass.getX1();
        double dy = pass.getX2() - pass.getX1();
        if (game.underControl.X - 35 + dx > game.c.MAX_X) {
            double remainder = game.underControl.X - 35 + dx - game.c.MAX_X;
            //TODO this is bullshit without reflection/intersection helpers. Proudly developed elsewhere
        }
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

    protected void drawEntities(Graphics2D g2D) {
        //The selection flag
        for (int sel = 1; sel <= game.players.length; sel++) {
            if (indexSelected() == sel) {
                Titan p = game.players[sel - 1];
                sconst.drawImage(g2D, selector.getImage(), ((int) p.getX() - camX + 27), ((int) p.getY() - camY - 22), this);
            }
        }
        drawNontitans(g2D, game.entityPool);
        //For the goalies
        for (int n = 0; n < 2; n++) {
            if (game.players[n].getX() + 35 <= game.ball.X) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.GOALIE, 17), ((int) game.players[n].getX() - camX), ((int) game.players[n].getY() - camY), this);
            }
            if (game.players[n].getX() + 35 > game.ball.X) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.GOALIE, 18), ((int) game.players[n].getX() - camX), ((int) game.players[n].getY() - camY), this);
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
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 1), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (facing == 2 && t.runningFrame == 0 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 9), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            // Frame 1 run right
            if (facing == 1 && t.runningFrame == 1 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 2), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            // Frame 2 run right
            if (facing == 1 && t.runningFrame == 2 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 3), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            // Frame 1 run left
            if (facing == 2 && t.runningFrame == 1 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 10), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            // Frame 2 run right
            if (facing == 2 && t.runningFrame == 2 && t.actionState == Titan.TitanState.IDLE) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 11), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            //Shoot
            if (facing == 1 && t.actionState == Titan.TitanState.SHOOT) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 8), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (facing == 2 && t.actionState == Titan.TitanState.SHOOT) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 16), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            //pass
            if (facing == 1 && t.actionState == Titan.TitanState.LOB) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 4), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (facing == 2 && t.actionState == Titan.TitanState.LOB) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 12), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            //attacks
            if (t.actionState == Titan.TitanState.A1 && facing == 2) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 5), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.A2 && facing == 2) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 6), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.STEAL && facing == 2) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 5), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.A1 && facing == 1) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 13), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.A2 && facing == 1) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 14), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.STEAL && facing == 1) {
                sconst.drawImage(g2D, SelectClassSkins.pullImage(t.getType(), 13), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
        }
        drawEffectIcons(g2D);
        if (game.allSolids != null) {
            List<Entity> drawHp = new ArrayList<>(Arrays.asList(game.allSolids));
            if(game.underControl.team == TeamAffiliation.HOME)
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
                    drawHealthBar(g2D, e);
                }
            }
        }
    }

    protected boolean invisible(Entity t) {
        boolean opp = this.game.underControl.team != t.team;
        return opp && game.effectPool.hasEffect(t, EffectId.STEALTHED) && !game.effectPool.hasEffect(t, EffectId.FLARE);
    }

    protected void drawNontitans(Graphics2D g2D, java.util.List<Entity> draw) {
        staticFrame += 1;
        staticFrame %= 2;
        for (Entity e : draw) {
            Image f1 = trap1.getImage();
            Image f2 = trap2.getImage();
            if (e instanceof Wall) {
                f1 = wall.getImage();
                f2 = wall.getImage();
            }
            if (e instanceof BallPortal) {
                BallPortal p = (BallPortal) e;
                if (p.isCooldown(game.now)) {
                    f1 = bportalcd.getImage();
                    f2 = bportalcd.getImage();
                } else {
                    f1 = bportal1.getImage();
                    f2 = bportal2.getImage();
                }
            }
            if (e instanceof Portal) {
                Portal p = (Portal) e;
                if (p.isCooldown(game.now)) {
                    f1 = portalcd.getImage();
                    f2 = portalcd.getImage();
                } else {
                    f1 = portal1.getImage();
                    f2 = portal2.getImage();
                }
            }
            if (e instanceof Fire) {
                f1 = fire1.getImage();
                f2 = fire2.getImage();
            }
            if (e instanceof Cage) {
                f1 = cage.getImage();
                f2 = f1;
            }
            if (e instanceof Wolf) {
                Wolf w = (Wolf) e;
                if (w.wolfPower == 1) {
                    if(w.facingRight){
                        f1 = wolf1R.getImage();
                    }else{
                        f1 = wolf1L.getImage();
                    }
                } else if (w.wolfPower == 2) {
                    if(w.facingRight){
                        f1 = wolf2R.getImage();
                    }else{
                        f1 = wolf2L.getImage();
                    }
                } else if (w.wolfPower > 2 && w.wolfPower < 5) {
                    if(w.facingRight){
                        f1 = wolf3R.getImage();
                    }else{
                        f1 = wolf3L.getImage();
                    }
                } else if (w.wolfPower >= 5) {
                    if(w.facingRight){
                        f1 = wolf5R.getImage();
                    }else{
                        f1 = wolf5L.getImage();
                    }
                }
                f2 = f1;
            }
            if(!invisible(e)){
                if (staticFrame % 2 == 1) {
                    sconst.drawImage(g2D, f1, (int) e.getX() - camX, (int) e.getY() - camY, this);
                } else {
                    sconst.drawImage(g2D, f2, (int) e.getX() - camX, (int) e.getY() - camY, this);
                }
            }
        }
    }

    protected void drawEffectIcons(Graphics2D g2D) {
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
                if(en instanceof Titan){
                    Titan t = (Titan) en;
                    if (offset.containsKey(t.id) && !invisible(t)) {
                        sconst.drawImage(g2D, e.getIconSmall(), (int) t.X + offset.get(t.id) - camX,
                                (int) t.Y - 29 - camY, this);
                        offset.put(t.id, offset.get(t.id) + 16);
                    }
                }
                else{
                    if (offset.containsKey(en.id) && !invisible(en)) {
                        sconst.drawImage(g2D, e.getIconSmall(), (int) en.X + offset.get(en.id) - camX,
                                (int) en.Y - 25 - camY, this);
                        offset.put(en.id, offset.get(en.id) + 16);
                    }
                }

            }
        }
    }

    protected void drawHealthBar(Graphics2D g2D, Entity e) {
        int xOffset = -25;
        if (e.team == TeamAffiliation.AWAY) {
            g2D.setColor(Color.WHITE);
            xOffset = -21;
        }
        if (e.team == TeamAffiliation.HOME) {
            g2D.setColor(Color.BLUE);
            xOffset = -21;
        }
        if (e instanceof Titan) {
            if(!invisible(e)){
                int x = (int) sconst.adjX((int) e.X + xOffset - camX);
                int y = sconst.adjY((int) e.Y - 13 - camY);
                Rectangle healthBar = new Rectangle(x, y,
                        (int) sconst.adjX(100), sconst.adjY(15));
                g2D.fill(healthBar);
                int hpPercentage = (int) (100 * e.health / e.maxHealth);
                y = sconst.adjY((int) e.Y - 10 - camY);
                Rectangle healthStat = new Rectangle(x,
                        y,
                        (int) sconst.adjX(hpPercentage), sconst.adjY(9));
                setColorBasedOnPercent(g2D, hpPercentage, false);
                g2D.fill(healthStat);
                Titan t = (Titan) e;
                displayBuust(g2D, t, x);
            }
        }else{
            if(!invisible(e)) {
                g2D.setColor(Color.DARK_GRAY);
                xOffset = -9;
                if (e.team == TeamAffiliation.AWAY) {
                    g2D.setColor(Color.WHITE);
                    xOffset = -5;
                }
                if (e.team == TeamAffiliation.HOME) {
                    g2D.setColor(Color.BLUE);
                    xOffset = -5;
                }
                int x = (int) sconst.adjX((int) e.X + xOffset - camX);
                int y = sconst.adjY((int) e.Y - 9 - camY);
                Rectangle healthBar = new Rectangle(x, y,
                        (int) sconst.adjX(66), sconst.adjY(8));
                g2D.fill(healthBar);
                int hpPercentage = (int) (100 * e.health / e.maxHealth);
                y = sconst.adjY((int) e.Y - 8 - camY);
                Rectangle healthStat = new Rectangle(x,
                        y,
                        (int) sconst.adjX(hpPercentage * 2 / 3), sconst.adjY(5));
                setColorBasedOnPercent(g2D, hpPercentage, false);
                g2D.fill(healthStat);
            }
        }
        if (e instanceof Portal) {
            Portal p = (Portal) e;
            if (p.isCooldown(game.now)) {
                double durSpent = p.cooldownPercentOver(game.now);
                setColorBasedOnPercent(g2D, durSpent, false);
                Rectangle durBar = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 1 - camY, (int) sconst.adjX(durSpent*2/3), sconst.adjY(2));
                g2D.fill(durBar);
            }
        }
        if (e instanceof BallPortal) {
            BallPortal p = (BallPortal) e;
            if (p.isCooldown(game.now)) {
                double durSpent = p.cooldownPercentOver(game.now);
                setColorBasedOnPercent(g2D, durSpent, false);
                Rectangle durBar = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 1 - camY, (int) sconst.adjX(durSpent*2/3), sconst.adjY(2));
                g2D.fill(durBar);
            }
        }
    }

    private void displayBuust(Graphics2D g2D, Titan t, int x) {
        int y = sconst.adjY((int) t.Y - 4 - camY);
        if (t.fuel > 25) {
            g2D.setColor(new Color(.5f, .5f, 1f));
        } else {
            g2D.setColor(new Color(.65f, 0f, 0f));
        }
        Rectangle buustStat = new Rectangle(x, y,
                (int) sconst.adjX((int) t.fuel),
                sconst.adjY(3));
        g2D.fill(buustStat);
        if(t.fuel > 25){
            g2D.setColor(new Color(.65f, 0f, 0f));
            Rectangle lowBuustWarning = new Rectangle(x+25 - 1, y,
                    (int) sconst.adjX( 4),
                    sconst.adjY(3));
            g2D.fill(lowBuustWarning);
        }
    }

    public void classFacts(Graphics2D g2D) {
        updateSelected();
        if(controlsHeld.classSelection != TitanType.GOALIE){
            double speed = Titan.normalOutOfTenFromStat(Titan.titanSpeed, controlsHeld.classSelection);
            double hp = Titan.normalOutOfTenFromStat(Titan.titanHealth, controlsHeld.classSelection);
            double shoot = Titan.normalOutOfTenFromStat(Titan.titanShoot, controlsHeld.classSelection);
            double steal = Titan.normalOutOfTenFromStat(Titan.titanStealRad, controlsHeld.classSelection);
            g2D.setColor(Color.BLUE);
            g2D.setFont(new Font("Verdana", Font.BOLD, sconst.STAT_CAT_FONT));
            g2D.fill(new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
            g2D.drawString("Speed", sconst.STAT_CAT_X, sconst.adjY(80));
            g2D.drawImage(new RatioEffect(0, null, EffectId.FAST, 0)
                    .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL, this);
            g2D.fill(new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 2, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
            g2D.drawString("Health", sconst.STAT_CAT_X, sconst.adjY(180));
            g2D.drawImage(new HealEffect(0, null)
                    .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL * 2, this);
            g2D.fill(new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 3, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
            g2D.drawString("Shot Power", sconst.STAT_CAT_X - 18, sconst.adjY(280));
            g2D.drawImage(new ShootEffect(0, null)
                    .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL * 3, this);
            g2D.fill(new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 4, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
            g2D.drawString("Steal Radius", sconst.STAT_CAT_X - 18, sconst.adjY(380));
            g2D.drawImage(new EmptyEffect(0, null, EffectId.STEAL)
                    .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL * 4, this);

            setColorBasedOnPercent(g2D, speed * 10.0, false);
            g2D.fill(new Rectangle(sconst.STAT_INT_X, sconst.STAT_Y_SCL + 2,
                    (int) ((int) (speed * 10.0) * sconst.STAT_INTERNAL_SC), sconst.STAT_INTERNAL_H));
            setColorBasedOnPercent(g2D, hp * 10.0, false);
            g2D.fill(new Rectangle(sconst.STAT_INT_X, sconst.STAT_Y_SCL * 2 + 2,
                    (int) ((int) (hp * 10.0) * sconst.STAT_INTERNAL_SC), sconst.STAT_INTERNAL_H));
            setColorBasedOnPercent(g2D, shoot * 10.0, false);
            g2D.fill(new Rectangle(sconst.STAT_INT_X, sconst.STAT_Y_SCL * 3 + 2,
                    (int) ((int) (shoot * 10.0) * sconst.STAT_INTERNAL_SC), sconst.STAT_INTERNAL_H));
            setColorBasedOnPercent(g2D, steal * 10.0, false);
            g2D.fill(new Rectangle(sconst.STAT_INT_X, sconst.STAT_Y_SCL * 4 + 2,
                    (int) ((int) (steal * 10.0) * sconst.STAT_INTERNAL_SC), sconst.STAT_INTERNAL_H));


            String e = Titan.titanEText.get(controlsHeld.classSelection);
            String r = Titan.titanRText.get(controlsHeld.classSelection);
            String text = Titan.titanText.get(controlsHeld.classSelection);
            g2D.setColor(Color.GRAY);
            g2D.setFont(new Font("Verdana", Font.BOLD, sconst.OVR_DESC_FONT));
            g2D.drawString(controlsHeld.classSelection.toString(), sconst.OVR_DESC_FONT - 2, sconst.OVR_DESC_Y - sconst.OVR_DESC_FONT - 4);
            g2D.setColor(Color.BLACK);
            darkTheme(g2D, false);
            g2D.drawString(text, sconst.OVR_DESC_FONT - 2, sconst.OVR_DESC_Y);
            g2D.setFont(new Font("Verdana", Font.BOLD, sconst.ABIL_DESC_FONT));

            g2D.drawImage(new CooldownE(0, null)
                    .getIcon(), sconst.ICON_ABIL_X, sconst.E_ABIL_Y, this);
            g2D.drawString(e, sconst.DESC_ABIL_X, sconst.E_DESC_Y);
            g2D.drawImage(new CooldownR(0, null)
                    .getIcon(), sconst.ICON_ABIL_X, sconst.R_ABIL_Y, this);
            g2D.drawString(r, sconst.DESC_ABIL_X, sconst.R_DESC_Y);
        }
    }

    @Override
    public void keyPressed(KeyEvent ke) {
        int key = ke.getKeyCode();
        if (game != null && game.ended) {//back to main after game
            if(key == KeyEvent.VK_BACK_SPACE || key == KeyEvent.VK_SPACE ||
                    key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_ENTER){
                try {
                    parentWindow.reset(true); //TODO tournament feature here
                } catch (IOException e) {
                    e.printStackTrace();
                }
                phase = GamePhase.SHOW_GAME_MODES;
            }
        }
        if(key == KeyEvent.VK_ESCAPE && phase == GamePhase.DRAW_CLASS_SCREEN){
            phase = GamePhase.SHOW_GAME_MODES;
        }
        if(phase == GamePhase.TOURNAMENT_CODE){
            if(key == KeyEvent.VK_ESCAPE){
                phase = GamePhase.SHOW_GAME_MODES;
            }
            if(key == KeyEvent.VK_BACK_SPACE || key == KeyEvent.VK_DELETE){
                if(tournamentCode.length() > 0){
                    tournamentCode = tournamentCode.substring(0, tournamentCode.length() - 1);
                }
            }
            if(key == KeyEvent.VK_SHIFT){
                codeHide = ! codeHide;
            }
            if(key >= 65 && key <= 90){
                tournamentCode+= (char) key;
            }
            if(key == KeyEvent.VK_DOWN){
                tourneyIndex++;
                if(tourneyIndex >= 8){
                    tourneyIndex -=8;
                }
            }
            if(key == KeyEvent.VK_UP){
                tourneyIndex--;
                if(tourneyIndex < 0){
                    tourneyIndex +=8;
                }
            }
            if(key == KeyEvent.VK_LEFT){
                tourneyOptions.advance(tourneyIndex, -1);
            }
            if(key == KeyEvent.VK_RIGHT){
                tourneyOptions.advance(tourneyIndex, 1);
            }
            if(key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER){
                tournamentCode+=tourneyOptions.toString();
                phase = GamePhase.DRAW_CLASS_SCREEN;
            }
            return;
        }
        if (debugCamera == 1) {
            if (key == KeyEvent.VK_RIGHT && phase == GamePhase.INGAME) {
                camX += 10;
            }
            if (key == KeyEvent.VK_LEFT && phase == GamePhase.INGAME) {
                camX -= 10;
            }
            if (key == KeyEvent.VK_UP && phase == GamePhase.INGAME) {
                camY -= 10;
            }
            if (key == KeyEvent.VK_DOWN && phase == GamePhase.INGAME) {
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
        if(key == KeyEvent.VK_ESCAPE && phase == GamePhase.WAIT_FOR_GAME){
            loginClient.leave();
            queued = false;
            phase = GamePhase.DRAW_CLASS_SCREEN;
        }
        if(key == KeyEvent.VK_ESCAPE && phase == GamePhase.SET_MASTERIES){
            phase = GamePhase.DRAW_CLASS_SCREEN;
        }
        if (key == KeyEvent.VK_SPACE && (phase == GamePhase.INGAME || phase == GamePhase.SCORE_FREEZE || phase == GamePhase.TUTORIAL)) {
            camFollow = !camFollow;
            controlsHeld.CAM = true;
            //TODO play restart ding/click
        }
        if ((phase == GamePhase.INGAME) || phase == GamePhase.TUTORIAL) {
            controlsConfig.mapKeyPress(this.game, controlsHeld, key, this.shotSound);
            if (controlsConfig.toggleInstructions("" + (char)key)) {
                instructionToggle = true;
            }
            if (controlsConfig.fullScreen("" + (char)key)) {
                fullScreen = !fullScreen;
                parentWindow.toggleFullscreen(fullScreen);
            }
        }
        classKeys(key);
    }

    @Override
    public void keyReleased(KeyEvent ke) {
        int key = ke.getKeyCode();
        if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL ) {
            controlsConfig.mapKeyRelease(controlsHeld, "" + key);
            //shotSound.rewindStart();
            if (controlsConfig.toggleInstructions("" + (char)key)) {
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


    private void classKeys(int key) {
        if ((key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A)) {
            if (phase == GamePhase.DRAW_CLASS_SCREEN) {
                cursor -= 1;
            }
            if (phase == GamePhase.SHOW_GAME_MODES) {
                cursor -= 1;
            }
        }
        if ((key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D)) {
            if (phase == GamePhase.DRAW_CLASS_SCREEN) {
                cursor += 1;
            }
            if (phase == GamePhase.SHOW_GAME_MODES) {
                cursor += 1;
            }
        }
        if ((key == KeyEvent.VK_UP || key == KeyEvent.VK_W)) {
            if (phase == GamePhase.DRAW_CLASS_SCREEN) {
                cursor -= 4;
            }
            if (phase == GamePhase.SHOW_GAME_MODES) {
                cursor -= 1;
            }
        }
        if ((key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S)) {
            if (phase == GamePhase.DRAW_CLASS_SCREEN) {
                cursor += 4;
            }
            if (phase == GamePhase.SHOW_GAME_MODES) {
                cursor += 1;
            }
        }
    }

    private boolean stateControls(int key) {
        if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER)
                && phase == GamePhase.CREDITS) {
            controlsHeld.CAM = true;
            phase = GamePhase.CONTROLS;
            return true;
        }
        if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER)
                && phase == GamePhase.CONTROLS) {
            phase = GamePhase.SHOW_GAME_MODES;
            controlsHeld.CAM = true;
            return true;
        }
        if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER)
                && phase == GamePhase.SHOW_GAME_MODES) {
            controlsHeld.CAM = true;
            switch(cursor){
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
                    phase = GamePhase.TOURNAMENT_CODE;
                    cursor = 1;
                    return true;
                case 3:
                    phase = GamePhase.TUTORIAL_START;
                    cursor = 1;
                    return true;
            }
            return true;
        }
        if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER)
                && phase == GamePhase.DRAW_CLASS_SCREEN) {
            controlsHeld.CAM = true;
            phase = GamePhase.SET_MASTERIES;
            return true;
        }
        if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER)
                && phase == GamePhase.SET_MASTERIES) {
            phase = GamePhase.TRANSITIONAL;
            controlsHeld.CAM = true;
            return true;
        }
        if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER)
                && phase == GamePhase.TRANSITIONAL) {
            phase = GamePhase.WAIT_FOR_GAME;
            controlsHeld.CAM = true;
            return true;
        }
        return false;
    }

    protected boolean masteriesKeys(int key) {
        if ((key == KeyEvent.VK_UP || key == KeyEvent.VK_W) && phase == GamePhase.SET_MASTERIES) {
            masteriesIndex--;
            if (masteriesIndex < 0) {
                masteriesIndex = 8;
            }
        }
        if ((key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) && phase == GamePhase.SET_MASTERIES) {
            masteriesIndex++;
            if (masteriesIndex > 8) {
                masteriesIndex = 0;
            }
        }
        if ((key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) && phase == GamePhase.SET_MASTERIES) {
            masteryDelta(masteriesIndex, 1);
        }
        if ((key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) && phase == GamePhase.SET_MASTERIES) {
            masteryDelta(masteriesIndex, -1);
        }
        return false;
    }

    protected void masteryDelta(int index, int delta) {
        Cloner cl = new Cloner();
        Masteries oldMasteries = cl.deepClone(masteries);
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
        }
        if (!masteries.validate()) {
            System.out.println("detected invalid mastery settings");
            masteries = oldMasteries;
        }
    }

    @Override
    public void keyTyped(KeyEvent ke) {
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
        if (cursor == 10) controlsHeld.classSelection= TitanType.BUILDER;
        if (cursor == 11) controlsHeld.classSelection= TitanType.ARTISAN;
        if (cursor == 12) controlsHeld.classSelection= TitanType.GRENADIER;
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
            loginClient.check();
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
        if(addListeners){
            addKeyListener(this);
        }

        setBackground(Color.WHITE);
        setFocusable(true); // recognizes obtaining focus from the keyboard
        timer.start();
    }

    public void starting(Graphics2D g2D) {
        darkTheme(g2D, true);
        if (gamestart == null) {
            gamestart = Instant.now().plus(new Duration(5100));
        }
        sconst.drawImage(g2D, lobby.getImage(), 1, 1, this);
        Font font = new Font("Verdana", Font.BOLD, 24);
        g2D.setColor(Color.YELLOW);
        sconst.setFont(g2D, font);
        double milUntil = (new Duration(Instant.now(), gamestart)).getMillis();
        System.out.println(milUntil);
        sconst.drawString(g2D,String.format("Starting in %1.1f seconds", milUntil / 1000.0), 345, 220);
        font = new Font("Verdana", Font.BOLD, 48);
        g2D.setColor(Color.RED);
        sconst.setFont(g2D, font);
        sconst.drawString(g2D,"STARTING", 418, 480);
        if (game == null &&
                Instant.now().isAfter(gamestart.plus(new Duration(500)))) {
            //TODO this messes us up bad somehow for everyone but the last client to connect
            sconst.setFont(g2D, new Font("Verdana", Font.BOLD, 12));
            sconst.drawString(g2D,"(Client may be disconnected, retrying)", 80, 520);
            if (game == null &&
                    Instant.now().isAfter(gamestart.plus(new Duration(2500)))) {
                try {
                    parentWindow.reset(false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void lobby(Graphics2D g2D) {
        darkTheme(g2D, true);
        sconst.drawImage(g2D, lobby.getImage(), 1, 1, this);
        Font font = new Font("Verdana", Font.BOLD, 24);
        g2D.setColor(Color.YELLOW);
        sconst.setFont(g2D, font);
        sconst.drawString(g2D,"Waiting for players...", 342, 220);

        font = new Font("Verdana", Font.BOLD, 24);
        g2D.setColor(Color.GREEN);
        sconst.setFont(g2D, font);
        sconst.drawString(g2D,"Press ESC to cancel...", 342, 480);
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

    public void displayScore(Graphics2D g2D) {
        if (phase == GamePhase.INGAME || phase == GamePhase.TUTORIAL) {
            Font font = new Font("Verdana", Font.BOLD, 45);
            sconst.setFont(g2D, font);
            String goalsHome = Integer.toString((int) game.home.score);
            String minorGoalsHome = Integer.toString((int) ((game.home.score - (int) game.home.score) * 4));
            String goalsAway = Integer.toString((int) game.away.score);
            String minorGoalsAway = Integer.toString((int) ((game.away.score - (int) game.away.score) * 4));

            g2D.setColor(new Color(0f, 0f, 1f, .5f));
            sconst.drawString(g2D,goalsHome, 180, 704);
            g2D.setColor(new Color(1f, 1f, 1f, .5f));
            sconst.drawString(g2D,goalsAway, 799, 704);

            //draw minor goals
            font = new Font("Verdana", Font.BOLD, 32);
            sconst.setFont(g2D, font);
            setColorFromCharge(g2D, minorGoalsHome);
            sconst.drawString(g2D,minorGoalsHome + "/4", 230, 701);
            setColorFromCharge(g2D, minorGoalsAway);
            sconst.drawString(g2D,minorGoalsAway + "/4", 848, 701);

            g2D.setColor(Color.RED);
            int x = xSize / 4;
            //addBoostIcons();
            for (int i = 0; i < game.effectPool.getEffects().size(); i++) {
                Effect e = game.effectPool.getEffects().get(i);
                Entity on = game.effectPool.getOn().get(i);
                if (game.underControl.id.equals(on.id) && !e.toString().contains("ATTACKED")) {
                    sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 72));
                    if (e.effect == EffectId.ROOT) {
                        g2D.setColor(new Color(.36f, .51f, .28f, .4f));
                        sconst.drawString(g2D,"Rooted!", 450, 300);
                    }
                    if (e.effect == EffectId.SLOW) {
                        g2D.setColor(new Color(.45f, .9f, .75f, .4f));
                        sconst.drawString(g2D,"Slowed!", 450, 300);
                    }
                    if (e.effect == EffectId.STUN) {
                        g2D.setColor(new Color(1f, .74f, 0f, .4f));
                        sconst.drawString(g2D,"Stunned!", 450, 300);
                    }
                    if (e.effect == EffectId.STEAL) {
                        g2D.setColor(new Color(0f, 0f, 0f, .4f));
                        sconst.drawString(g2D,"Stolen!", 450, 300);
                    }
                    if (e.getIcon() != null) {
                        Composite originalComposite = g2D.getComposite();
                        g2D.setComposite(makeComposite(.5f));
                        sconst.drawImage(g2D, e.getIcon(), x, 689, this);
                        g2D.setComposite(originalComposite);
                        g2D.setColor(new Color(1f, 1f, 1f, .5f));
                        double xt = sconst.adjX(x);
                        double wt = sconst.adjX(32);
                        double yt = sconst.adjY(657);
                        double ht = sconst.adjY(32);
                        Arc2D.Double background = new Arc2D.Double(xt, yt, wt, ht, 90, -360, Arc2D.PIE);
                        g2D.fill(background);
                        double percentBar = 100.0 - e.getPercentLeft();
                        if (percentBar > 100) {
                            percentBar = 99.99999;
                        }
                        if (percentBar < 0) {
                            percentBar = 0.000001;
                        }
                        setColorBasedOnPercent(g2D, percentBar, true);
                        double coverage = e.getPercentLeft() / 100.0 * 360.0;
                        xt = sconst.adjX(x+2);
                        wt = sconst.adjX(28);
                        yt = sconst.adjY(659);
                        ht = sconst.adjY(28);
                        Arc2D.Double percent = new Arc2D.Double(xt, yt, wt, ht, 90, coverage, Arc2D.PIE);
                        g2D.fill(percent);
                        x += 32;
                    }
                }
            }
            drawTimerWarnings(g2D);
        }
    }

    private void drawTimerWarnings(Graphics2D g2D) {
        g2D.setColor(new Color(0f, 1f, 0f, .4f));
        Font font = new Font("Verdana", Font.BOLD, 32);
        sconst.setFont(g2D, font);
        final double FPS = 1000 / game.GAMETICK_MS;
        String timeStr = "";
        double time = (game.framesSinceStart / FPS);
        time = (int)(time*10.0) / 10.0;
        timeStr += time;
        sconst.drawString(g2D,timeStr, 614, 711);
        g2D.setColor(new Color(0f, 0f, 0f, .4f));
        setBottomText(g2D, (int) time);
        sconst.drawString(g2D,bottomText, 480, 664);
    }

    private void setBottomText(Graphics2D g2D, int timer) {
        Font font = new Font("Verdana", Font.BOLD, 16);
        sconst.setFont(g2D, font);
        bottomText = "";
        final int WARN=30, FWARN=10, CHWARN = 4;
        if(timer >= game.GOALIE_DISABLE_TIME - WARN && timer < game.GOALIE_DISABLE_TIME - FWARN){
            g2D.setColor(new Color(.9f, .9f, 0f, .4f));
            bottomText = "GOALIES VANISHING WARNING";
        }
        else if(timer >= game.GOALIE_DISABLE_TIME - FWARN && timer < game.GOALIE_DISABLE_TIME){
            g2D.setColor(new Color(1f,0f, 0f, .4f));
            bottomText = "GOALIES VANISHING WARNING";
        }
        else if(timer >= game.GOALIE_DISABLE_TIME && timer < game.GOALIE_DISABLE_TIME - CHWARN){
            g2D.setColor(new Color(1f,0f, 0f, .4f));
            bottomText = "GOALIES VANISHED";
        }
        else if(timer >= (game.options.suddenDeathIndex*60) - WARN && timer < (game.options.suddenDeathIndex*60) - FWARN){
            g2D.setColor(new Color(.9f, .9f, 0f, .6f));
            bottomText = "SUDDEN DEATH WARNING";
        }
        else if(timer >= (game.options.suddenDeathIndex*60) - FWARN && timer < (game.options.suddenDeathIndex*60)){
            g2D.setColor(new Color(1f,0f, 0f, .6f));
            bottomText = "SUDDEN DEATH WARNING";
        }
        else if(timer >= (game.options.suddenDeathIndex*60) && timer < (game.options.suddenDeathIndex*60) - CHWARN){
            g2D.setColor(new Color(1f,0f, 0f, 1f));
            bottomText = "SUDDEN DEATH ENABLED";
        }
        else if(timer >= (game.options.tieIndex*60) - WARN && timer < (game.options.tieIndex*60) - FWARN){
            g2D.setColor(new Color(.9f, .9f, 0f, 1f));
            bottomText = "TIE GAME WARNING";
        }
        else if(timer >= (game.options.tieIndex*60) - FWARN && timer < (game.options.tieIndex*60)){
            g2D.setColor(new Color(1f,0f, 0f, 1f));
            bottomText = "TIE GAME WARNING";
        }
        else if(timer >= (game.options.tieIndex*60) && timer < (game.options.tieIndex*60) - CHWARN){
            g2D.setColor(new Color(1f,0f, 0f, 1f));
            bottomText = "TIE GAME";
        }
    }

    private void setColorFromCharge(Graphics2D g2D, String str) {
        switch (str) {
            case "1":
                g2D.setColor(new Color(.4f, 1f, 0f, .8f));
                break;
            case "2":
                g2D.setColor(new Color(.94f, .90f, .33f, .8f));
                break;
            case "3":
                g2D.setColor(new Color(1f, .15f, .15f, .8f));
                break;
            default:
                g2D.setColor(new Color(.6f, .6f, .6f, .8f));
        }
    }

    protected AlphaComposite makeComposite(float alpha) {
        int type = AlphaComposite.SRC_OVER;
        return (AlphaComposite.getInstance(type, alpha));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // empty, only redraw when we have a new UDP state
    }


    public void tutorial(Graphics2D g2D) {
        darkTheme(g2D, true);
        sconst.drawImage(g2D, tutorial.getImage(), 1, 1, this);
        Font font = new Font("Verdana", Font.PLAIN, 65);
        sconst.setFont(g2D, font);
        sconst.drawString(g2D,"Space to proceed", 5, 705);
    }

    protected void setColorBasedOnPercent(Graphics2D g2D, double inputPercent, boolean translucent) {
        g2D.setColor(new Color(redColorFromPercent(inputPercent),
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

    private void drawTournamentScreen(Graphics2D g2D) {
        darkTheme(g2D, true);
        try{
            tournamentCode=tournamentCode.split("/")[0];
        } catch (Exception ex){ }
        g2D.setColor(new Color(1f, 0f, 0f));
        sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 32));
        sconst.drawString(g2D,"Type alphabetical game password. Space to proceed, shift to hide letters", 60, 100);
        g2D.setColor(new Color(0f, 0f, 1f));
        if(codeHide){
            String s = "";
            for(int i=0; i< tournamentCode.length(); i++){
                s += "*";
            }
            sconst.drawString(g2D,s, 100, 200);
        }else{
            sconst.drawString(g2D,tournamentCode, 100, 200);
        }
        sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 16));
        sconst.drawString(g2D,"This feature is for avoiding random matchmaking. Set the same game password and rules as your friends to join a private lobby", 50, 400);
        sconst.drawString(g2D,"Leave the password blank for the default/public lobby", 120, 500);

        g2D.setColor(Color.BLACK);
        darkTheme(g2D, false);
        sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 32));
        sconst.drawString(g2D,"Setting Tournament Options", 200, 50);
        sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 16));
        sconst.drawString(g2D,"ARROW keys to set options", 200, 420);
        int y = 420;
        for (int i = 0; i < 8; i++) {
            int x = 650;
            g2D.setColor(Color.BLACK);
            darkTheme(g2D, false);
            sconst.drawString(g2D,tourneyOptions.disp(i), x, y);
            if (tourneyIndex == i) {
                g2D.setColor(Color.RED);
                int[] xpts = new int[3], ypts = new int[3];
                xpts[0] = (int) sconst.adjX(x-22); ypts[0] = sconst.adjY(y-14);
                xpts[1] = (int) sconst.adjX(x-30); ypts[1] = sconst.adjY(y-6);
                xpts[2] = (int) sconst.adjX(x-22); ypts[2] = sconst.adjY(y+2);
                g2D.fill(new Polygon(xpts, ypts, 3));
                xpts[0] = (int) sconst.adjX(x+200); ypts[0] = sconst.adjY(y-14);
                xpts[1] = (int) sconst.adjX(x+208); ypts[1] = sconst.adjY(y-6);
                xpts[2] = (int) sconst.adjX(x+200); ypts[2] = sconst.adjY(y+2);
                g2D.fill(new Polygon(xpts, ypts, 3));
            }
            y += 25;
        }
    }

    private void showGameModes(Graphics2D g2D) {
        if(hasFocus()) {
            darkTheme(g2D, true);
            tut = null;
            game = null;
            tut4.rewindStop();
            if (cursor > 3) {
                cursor = 0;
            }
            if (cursor < 0) {
                cursor = 3;
            }

            classCursor = new StaticImage(sconst.loadImage(classCursor, "res/Court/draft/cursorflag.png", this, 1.8, 1.0));
            if (cursor == 0) sconst.drawImage(g2D, classCursor.getImage(), 250, 245, this);
            if (cursor == 1) sconst.drawImage(g2D, classCursor.getImage(), 250, 365, this);
            if (cursor == 2) sconst.drawImage(g2D, classCursor.getImage(), 250, 485, this);
            if (cursor == 3) sconst.drawImage(g2D, classCursor.getImage(), 250, 605, this);

            sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 24));
            g2D.setColor(new Color(1f, 0f, 0f));
            sconst.drawString(g2D, "Navigate Options with W (up) S (down), confirm with SPACE", 350, 155);
            sconst.drawString(g2D, "RANKED 3v3", 260, 300);
            g2D.setColor(new Color(1f, .5f, 0f));
            sconst.drawString(g2D, "RANKED 1v1", 260, 420);
            g2D.setColor(new Color(0f, 0f, 1f));
            sconst.drawString(g2D, "TUTORIAL", 260, 660);
            sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 20));
            g2D.setColor(new Color(0f, 1f, 0f));
            sconst.drawString(g2D, "CUSTOM GAME", 260, 540);//drawn after to save a refont

            drawTrophies(g2D);

        } else {
            g2D.setColor(new Color(1f, 0f, 0f));
            sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 32));
            sconst.drawString(g2D,"APP LOST FOCUS (AFTER GAME USUALLY)", 10, 132);
            sconst.drawString(g2D,"ALT TAB OUT AND THEN BACK IN TO RESTORE KEY LISTENERS", 10, 280);
        }
    }

    private void drawTrophies(Graphics2D g2D) {
        g2D.setColor(Color.BLACK);
        darkTheme(g2D, false);
        String rankname = "res/Court/rnk/" +
                loginClient.getRank3v3().toString().toLowerCase()  + ".png";
        String rank1v1name = "res/Court/rnk/" +
                loginClient.getRank1v1().toString().toLowerCase()  + ".png";
        StaticImage trophy = new StaticImage();
        trophy = new StaticImage(sconst.loadImage(trophy, rankname, this, 2.0, 2.0));
        StaticImage trophy1v1 = new StaticImage();
        trophy1v1 = new StaticImage(sconst.loadImage(trophy1v1, rank1v1name, this, 2.0, 2.0));
        sconst.drawImage(g2D, trophy.getImage(), 458, 235, this);
        sconst.drawImage(g2D, trophy1v1.getImage(), 458, 355, this);
        g2D.setColor(Color.YELLOW);
        if(loginClient.getTopten() <= 10){
            sconst.drawString(g2D, ""+ loginClient.getTopten(), 432, 290);
        }
        if(loginClient.getTopten1v1() <= 10){
            sconst.drawString(g2D,""+ loginClient.getTopten1v1(), 432, 410);
        }
    }

    protected void drawClassScreen(Graphics2D g2D) {
        if(hasFocus()) {
            tut = null;
            game = null;
            tut4.rewindStop();
            darkTheme(g2D, true);
            sconst.drawImage(g2D, select.getImage(), 0, 0, this);
            sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 24));
            g2D.setColor(new Color(1f, 0f, 0f));
            sconst.drawString(g2D, "Navigate characters with WASD, confirm with SPACE", 350, 155);
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
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.WARRIOR, staticFrame), 160, 200, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.RANGER, staticFrame), 360, 200, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.MAGE, staticFrame), 560, 200, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.HOUNDMASTER, staticFrame), 760, 200, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.MARKSMAN, staticFrame), 160, 320, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.DASHER, staticFrame), 360, 320, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.GOLEM, staticFrame), 560, 320, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.STEALTH, staticFrame), 760, 320, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.SUPPORT, staticFrame), 160, 440, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.BUILDER, staticFrame), 360, 440, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.ARTISAN, staticFrame), 560, 440, this);
            sconst.drawImage(g2D, SelectClassSkins.pullImage(TitanType.GRENADIER, staticFrame), 760, 440, this);

            classFacts(g2D);
            if (cursor > 12) {
                cursor %= 12;
            }
            if (cursor <= 0) {
                cursor = -1 * (cursor);
                if (cursor == 0) {
                    cursor = 1;
                }
            }
            classCursor = new StaticImage(sconst.loadImage(classCursor, "res/Court/draft/cursorflag.png", this));
            if (cursor == 1) sconst.drawImage(g2D, classCursor.getImage(), 150, 195, this);
            if (cursor == 2) sconst.drawImage(g2D, classCursor.getImage(), 350, 195, this);
            if (cursor == 3) sconst.drawImage(g2D, classCursor.getImage(), 550, 195, this);
            if (cursor == 4) sconst.drawImage(g2D, classCursor.getImage(), 750, 195, this);
            if (cursor == 5) sconst.drawImage(g2D, classCursor.getImage(), 150, 315, this);
            if (cursor == 6) sconst.drawImage(g2D, classCursor.getImage(), 350, 315, this);
            if (cursor == 7) sconst.drawImage(g2D, classCursor.getImage(), 550, 315, this);
            if (cursor == 8) sconst.drawImage(g2D, classCursor.getImage(), 750, 315, this);
            if (cursor == 9) sconst.drawImage(g2D, classCursor.getImage(), 150, 435, this);
            if (cursor == 10) sconst.drawImage(g2D, classCursor.getImage(), 350, 435, this);
            if (cursor == 11) sconst.drawImage(g2D, classCursor.getImage(), 550, 435, this);
            if (cursor == 12) sconst.drawImage(g2D, classCursor.getImage(), 750, 435, this);
            if (cursor == 13) sconst.drawImage(g2D, classCursor.getImage(), 150, 515, this);
            if (cursor == 14) sconst.drawImage(g2D, classCursor.getImage(), 350, 515, this);
            if (cursor == 15) sconst.drawImage(g2D, classCursor.getImage(), 550, 515, this);
            if (cursor == 16) sconst.drawImage(g2D, classCursor.getImage(), 750, 515, this);
        } else {
            g2D.setColor(new Color(1f, 0f, 0f));
            sconst.setFont(g2D, new Font("Verdana", Font.PLAIN, 32));
            sconst.drawString(g2D,"APP LOST FOCUS (AFTER GAME USUALLY)", 10, 132);
            sconst.drawString(g2D,"ALT TAB OUT AND THEN BACK IN TO RESTORE KEY LISTENERS", 10, 280);
        }
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
