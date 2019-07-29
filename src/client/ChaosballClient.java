package client;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.rits.cloning.Cloner;
import gameserver.GameEngine;
import gameserver.TutorialOverrides;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownE;
import gameserver.effects.cooldowns.CooldownR;
import gameserver.effects.effects.*;
import gameserver.engine.GoalHoop;
import gameserver.engine.Masteries;
import gameserver.engine.Team;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.RangeCircle;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.BallPortal;
import gameserver.entity.minions.Portal;
import gameserver.entity.minions.Wall;
import gameserver.models.Game;
import gameserver.targeting.ShapePayload;
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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static gameserver.models.Game.GAMETICK_MS;

public class ChaosballClient extends JPanel implements ActionListener, KeyListener {
    protected final ScreenConst sconst;
    protected final BufferedImage ballLobTexture;
    public ClientPacket controlsHeld = new ClientPacket();
    public Timer timer = new Timer(25, this);
    public Instant gamestart = null;
    public Random rand;
    public Sound shotSound;
    protected Client gameserverConn = new Client(8192 * 8, 32768 * 8);
    protected String gameID;
    protected GameEngine game;
    protected int phase;
    protected Kryo kryo = gameserverConn.getKryo();
    protected boolean camFollow = true;
    protected boolean keysEnabled = true;
    protected String token, refresh;
    protected HttpClient loginClient;
    protected boolean instructionToggle = false;
    protected int staticFrame = 0, staticFrameCounter = 0;
    protected Masteries masteries;
    protected String bottomText = "";
    protected int masteriesIndex = 0;
    int debugCamera = 0;
    int cursor = 1; // For deciding classes
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
    StaticImage wall = new StaticImage();
    private boolean diskLoadMasteries = false;
    private Map<String, Map<String, Integer>> masteriesMap;
    private TutorialOverrides tut;

    public ChaosballClient(int xSize, int ySize, HttpClient loginClient) {
        this.xSize = xSize;
        this.ySize = ySize;
        sconst = new ScreenConst(xSize, ySize);
        this.loginClient = loginClient;
        intro.loadImage("res/Court/logo2.png");
        ballTexture.loadImage("res/Court/ballA.png");
        ballBTexture.loadImage("res/Court/ballB.png");
        BufferedImage tmp = Images.getBufferedFrom(ballTexture.image, 30, 30);
        ballLobTexture = Images.resize(tmp, 45, 45);
        ballFTexture.loadImage("res/Court/ballFA.png");
        ballFBTexture.loadImage("res/Court/ballFB.png");
        ballPtr.loadImage("res/Court/ballptr.png");
        ballFPtr.loadImage("res/Court/ballfptr.png");

        select.loadImage("res/Court/selectClass.png");
        classCursor.loadImage("res/Court/cursorflag.png");
        lobby.loadImage("res/Court/lobby.png");
        field.loadImage("res/Court/field.png");
        selector.loadImage("res/Court/select.png");
        goalScored.loadImage("res/Court/goal.png");
        victory.loadImage("res/Court/victory.png");
        tie.loadImage("res/Court/tie.png");
        defeat.loadImage("res/Court/defeat.png");
        tutorial.loadImage("res/Court/tutorial.png");

        wall.loadImage("res/Court/wall.png");
        portal1.loadImage("res/Court/portal.png");
        portal2.loadImage("res/Court/portal2.png");
        portalcd.loadImage("res/Court/portalcd.png");

        bportal1.loadImage("res/Court/ballp.png");
        bportal2.loadImage("res/Court/ballp2.png");
        bportalcd.loadImage("res/Court/ballpcd.png");

        trap1.loadImage("res/Court/trap.png");
        trap2.loadImage("res/Court/trap2.png");
        initSurface();

        JButton button = new JButton("Click me!");

        setLayout(new BorderLayout());
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent aE) {
                keysEnabled = true;
            }

            @Override
            public void focusLost(FocusEvent aE) {
                keysEnabled = false;
            }
        });
        MouseMotionListener mouseMvtListener = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                controlsHeld.posX = e.getPoint().x;
                controlsHeld.posY = e.getPoint().y;
                controlsHeld.camX = camX;
                controlsHeld.camY = camY;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                controlsHeld.posX = e.getPoint().x;
                controlsHeld.posY = e.getPoint().y;
                controlsHeld.camX = camX;
                controlsHeld.camY = camY;
            }
        };

        ChaosballClient clientSelf = this;
        MouseListener mouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (keysEnabled && (phase == 8 || phase == 101) ) {
                    controlsHeld.posX = event.getPoint().x;
                    controlsHeld.posY = event.getPoint().y;
                    if (event.getButton() == 1) {
                        controlsConfig.mapKeyPress(clientSelf, controlsHeld, "LMB");
                    }
                    if (event.getButton() == 3) {
                        controlsConfig.mapKeyPress(clientSelf, controlsHeld, "RMB");
                    }
                    controlsHeld.camX = camX;
                    controlsHeld.camY = camY;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (keysEnabled) {
                    controlsConfig.mapKeyRelease(controlsHeld, "LMB");
                    controlsConfig.mapKeyRelease(controlsHeld, "RMB");
                }
            }
        };
        addMouseListener(mouseListener);
        addMouseMotionListener(mouseMvtListener);
        button.addMouseListener(mouseListener);
        fireReconnect();
    }

    public void setAuth(String token, String refresh) {
        this.token = token;
        this.refresh = refresh;
        loginClient = new HttpClient();
        loginClient.token = token;
        loginClient.refreshToken = refresh;
    }

    public int indexSelected() {
        for (int i = 0; i < game.players.length; i++) {
            if (game.players[i].id.equals(game.underControl.id)) {
                return i + 1;
            }
        }
        return -1;
    }

    public void paintComponent(Graphics g) {
        Graphics2D g2D = (Graphics2D) g;
        super.paintComponent(g);
        if (game != null && game.ended) {
            Team team = teamFromUnderControl();
            Team enemy = enemyFromUnderControl();
            if (team.score > enemy.score) {
                g2D.drawImage(victory.getImage(), sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y, this);
            } else if(team.score == enemy.score){
                g2D.drawImage(tie.getImage(), sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y, this);
            } else{
                g2D.drawImage(defeat.getImage(), sconst.RESULT_IMG_X, sconst.RESULT_IMG_Y, this);
            }
            gameEndStatsAndRanks(g2D, game.underControl);
            return;
        }
        if (phase == 0) creditPanel(g2D);
        if (phase == 1) tutorial(g2D);
        if (phase == 2) drawClassScreen(g2D);
        if (phase == 3) {
            if (cursor == 13) {
                phase = 100;
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
                    File file = new File("masteries.json");
                    masteriesMap = mapper.readValue(file, Map.class);
                    Map<String, Integer> json = masteriesMap.get(controlsHeld.classSelection.toString());
                    masteries = new Masteries(json);
                } catch (Exception ex1) {
                    ex1.printStackTrace();
                    this.masteries = new Masteries();
                }
            }
            diskLoadMasteries = true;
            drawSetMasteries(g2D);
        }
        if (phase == 4) consumeCursorSelectClasses();

        //TODO rn skips straight to 6 -> 5 -> 7 due to refactors
        if (phase == 6) {
            try {
                gameID = requestOrQueueGame();
                clientInitialize();
                phase = 5;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (phase == 5) {
            try {
                gameID = requestOrQueueGame(); //sets to 7 when complete
                lobby(g2D);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (phase == 7) {
            starting(g2D);
        }
        if (phase == 8 || phase == 9 || phase == 10) {
            updateFrameBall();
            doDrawing(g2D); // do drawing of screen
            displayBallArrow(g2D);
            displayScore(g2D); // call the method to display the game score
        }
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
        if (phase == 100) {
            tut = new TutorialOverrides();
            try {
                tut.detectAndUpdateState();
                tut.clients = Collections.singletonList(tut.client);
                tut.initializeServer();
                tut.gameTick();
                tut.processClientPacket(tut.client, controlsHeld);
                tut.inGame = true;
                tut.underControl = tut.players[2];
                TerminableExecutor terminableExecutor = new TerminableExecutor(tut, exec);
                exec.scheduleAtFixedRate(terminableExecutor, 0, GAMETICK_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.game = tut;
            phase = 101;
        }
        if (phase == 101) {
            this.game = tut;
            updateFrameBall();
            doDrawing(g2D); // do drawing of screen
            displayBallArrow(g2D);
            displayScore(g2D); // call the method to display the game score
            updateNarration(tut);
            if(this.game.phase == 2){ //finish tutorial
                exec.shutdown();
                phase = this.game.phase;
                this.game = null;
                Thread.yield();
            }

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
        if(game.tutorialPhase == 1 && game.narrationPhase == 0){
            tut0.rewindStart();
            game.narrationPhase++;
        }
        if(game.tutorialPhase == 2 && game.narrationPhase == 1){
            tut0.rewindStop();
            tut1.rewindStart();
            game.narrationPhase++;
        }
        if(game.tutorialPhase == 3 && game.narrationPhase == 2){
            tut1.rewindStop();
            tut2.rewindStart();
            game.narrationPhase++;
        }
        if(game.tutorialPhase == 4 && game.narrationPhase == 3){
            tut2.rewindStop();
            tut3.rewindStart();
            game.narrationPhase++;
        }
        if(game.tutorialPhase == 5 && game.narrationPhase == 4){
            tut3.rewindStop();
            tut4.rewindStart();
            game.narrationPhase++;
        }
    }

    protected void drawSetMasteries(Graphics2D g2D) {
        g2D.setColor(Color.BLACK);
        g2D.setFont(new Font("Verdana", Font.PLAIN, 32));
        g2D.drawString("Setting Masteries", 200, 50);
        g2D.setFont(new Font("Verdana", Font.PLAIN, 16));
        g2D.drawString("Remaining Points", 200, 90);
        g2D.drawString("WASD or arrows to spend points", 200, 420);
        g2D.drawString("Space to enter (And queue for game)", 200, 450);
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
            g2D.drawString(Masteries.masteryFromIndex(i), x, y);
            if (masteriesIndex == i) {
                g2D.setColor(Color.RED);
                g2D.fill(new Rectangle(x - 30, y - 14, 16, 16));
            }
            x = 430;
            for (int ballNum = 3; ballNum > 0; ballNum--) {
                setColorFromRank(g2D, ballNum);
                if (3 - ballNum < arr[i]) {
                    g2D.fill(new Ellipse2D.Double(x, y - 14, 16, 16));
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
        int y = sconst.STATS_Y;
        Font font = new Font("Verdana", Font.PLAIN, sconst.STATS_FONT);
        g2D.setFont(font);
        for (String stat : stats.keySet()) {
            g2D.setColor(Color.BLACK);
            g2D.drawString(stat + ": " + stats.get(stat), sconst.STATS_X, y);
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
        crowdFrame = 1;
        crowdFrameCount = 0;
        ballFrame = 0;
        ballFrameCounter = 0;
        camX = 500;
        camY = 300;
        gameserverConn.start();
        //gameserverConn.setHardy(true);
        gameserverConn.connect(999999999, System.getenv("host"), 54555, 54556);
        //gameserverConn.connect(5000, "127.0.0.1", 54555);
        gameserverConn.addListener(new Listener() {
            public synchronized void received(Connection connection, Object object) {
                if (object instanceof GameEngine) {
                    game = (GameEngine) object;
                    game.began = true;
                    phase = game.phase;
                    controlsHeld.gameID = gameID;
                    controlsHeld.token = token;
                    controlsHeld.masteries = masteries;
                    try {
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
        exec.scheduleAtFixedRate(updateServer, 1, 20, TimeUnit.MILLISECONDS);
    }

    protected String requestOrQueueGame() throws UnirestException {
        if (phase != 5) { //request game
            token = loginClient.token;
            loginClient.join();
        } else { //already waiting
            loginClient.check();
            System.out.println(loginClient.gameId);
        }
        if (loginClient.gameId == null
                || loginClient.gameId.equals("WAITING")) {
            phase = 5;
        } else {
            gameID = loginClient.gameId;
            phase = 7;
        }
        if (loginClient.gameId.equals("NOT QUEUED")) {
            throw new UnirestException("Server not accepting connections");
        }
        return loginClient.gameId;
    }

    protected void creditPanel(Graphics2D g2D) {
        g2D.drawImage(intro.getImage(), 0, 0, this);
        Font font = new Font("Verdana", Font.PLAIN, 65);
        g2D.setFont(font);
        g2D.drawString("Space to proceed", 370, 640);
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
            g2D.drawImage(ballPtr.getImage(), at, this);
        } else {
            g2D.drawImage(ballFPtr.getImage(), at, this);
        }
    }

    public void displayBallArrow(Graphics2D g2D) {
        if (game == null) {
            //return;
        }
        int x = (int) (game.ball.X + game.ball.centerDist - camX);
        int y = (int) (game.ball.Y + game.ball.centerDist - camY);
        final int XCORR = xSize / 3;
        final int YCORR = ySize / 3;
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
        g2D.drawImage(field.getImage(), (1 - camX), (1 - camY), this);
        if (crowdFrame == 2) {
            //TODO animation
        }
        //Draw hoops
        //55x70 for majors
        //23/97 for minors
        g2D.setStroke(new BasicStroke(6f));
        for (GoalHoop goalData : game.lowGoals) {
            GoalSprite goal = new GoalSprite(goalData, camX, camY);
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
            GoalSprite goal = new GoalSprite(goalData, camX, camY);
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
        drawEntities(g2D);
        if (game.goalVisible == true) {
            g2D.drawImage(goalScored.getImage(), sconst.GOAL_TXT_X, sconst.GOAL_TXT_Y, this);
        }
        if (game.ballVisible == true) {
            if (game.anyPoss()) {
                if (ballFrame == 0) {
                    g2D.drawImage(ballTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
                if (ballFrame == 1) {
                    g2D.drawImage(ballBTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
            } else {
                if (ballLobMode()) {
                    g2D.drawImage(ballLobTexture, ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                } else if (ballFrame == 0) {
                    g2D.drawImage(ballFTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                } else if (ballFrame == 1) {
                    g2D.drawImage(ballFBTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
            }
        }
        if (camFollow) {
            camX = (int) game.underControl.X + 35 - (this.xSize / 3);
            //if (camX > 820) camX = 820;
            if (camX < 0) camX = 0;
            camY = (int) game.underControl.Y + 35 - (this.ySize / 3);
            //if (camY > 480) camY = 480;
            if (camY < 0) camY = 0;
        }
        if (debugCamera == 0) {
            if ((game.ball.X - camX) > sconst.CAM_MAX_X) {
                camX += 5;
                //if (camX > 820) camX = 820;
            }
            if ((game.ball.X - camX) < sconst.CAM_MAX_X) {
                camX -= 5;
                if (camX < 0) camX = 0;
            }
            if ((game.ball.Y - camY) > sconst.CAM_MAX_Y) {
                camY += 5;
                //if (camY > 480) camY = 480;
            }
            if ((game.ball.Y - camY) < sconst.CAM_MAX_Y) {
                camY -= 5;
                if (camY < 0) camY = 0;
            }
        }

        ArrayList<RangeCircle> clientCircles = new ArrayList<>();
        for (RangeCircle ri : game.underControl.rangeIndicators) {
            clientCircles.add(ri);
        }

        if (game.underControl.possession == 1) {
            g2D.setStroke(new BasicStroke(1));
            double pow = game.underControl.throwPower;
            double mx = controlsHeld.posX + camX;
            double my = controlsHeld.posY + camY;
            double ox = game.underControl.X - camX + 35;
            double oy = game.underControl.Y - camY + 35;
            double angle = Math.toRadians(Util.degreesFromCoords(mx - game.underControl.X - 35,
                    my - game.underControl.Y - 35));
            final double LOB_DIST = 212;
            final double SHOT_DIST = 324;
            Line2D lobBlock = new Line2D.Double(ox,
                    oy,
                    ox + ((.2 * LOB_DIST * pow +14) * Math.cos(angle)),
                    oy + ((.2 * LOB_DIST * pow +14) * Math.sin(angle)));
            g2D.setColor(Color.YELLOW);
            reflectDraw(lobBlock, mx, my, g2D);
            g2D.draw(lobBlock);

            Line2D lobFly = new Line2D.Double(ox + ((.2 * LOB_DIST * pow +14) * Math.cos(angle)),
                    oy + ((.2 * LOB_DIST * pow +14) * Math.sin(angle)),
                    ox + ((.75 * LOB_DIST * pow -14) * Math.cos(angle)),
                    oy + ((.75 * LOB_DIST * pow -14) * Math.sin(angle)));
            g2D.setColor(Color.BLUE);
            reflectDraw(lobFly, mx, my, g2D);
            g2D.draw(lobFly);

            Line2D lobCatch = new Line2D.Double(ox + ((.75 * LOB_DIST * pow -14) * Math.cos(angle)),
                    oy + ((.75 * LOB_DIST * pow -14) * Math.sin(angle)),
                    ox + (LOB_DIST * pow * Math.cos(angle)),
                    oy + (LOB_DIST * pow * Math.sin(angle)));
            g2D.setColor(Color.YELLOW);
            reflectDraw(lobCatch, mx, my, g2D);
            g2D.draw(lobCatch);

            Line2D shot = new Line2D.Double(ox + (LOB_DIST * pow * Math.cos(angle)),
                    oy + (LOB_DIST * pow * Math.sin(angle)),
                    ox + (SHOT_DIST * pow * Math.cos(angle)),
                    oy + (SHOT_DIST * pow * Math.sin(angle)));
            g2D.setColor(new Color(.65f, 0f, 0f));
            reflectDraw(shot, mx, my, g2D);
            g2D.draw(shot);
            if (game.underControl.possession == 1 && game.underControl.getType() == TitanType.ARTISAN) {
                QuadCurve2D eL = new QuadCurve2D.Double(ox, oy,
                        ox + (310 * pow * Math.cos(angle - .97)),
                        oy + (310 * pow * Math.sin(angle - .97)),
                        ox + (186 * pow * Math.cos(angle)),
                        oy + (186 * pow * Math.sin(angle)));
                QuadCurve2D eR = new QuadCurve2D.Double(ox, oy,
                        ox + (310 * pow * Math.cos(angle + .97)),
                        oy + (310 * pow * Math.sin(angle + .97)),
                        ox + (186 * pow * Math.cos(angle)),
                        oy + (186 * pow * Math.sin(angle)));
                g2D.setColor(Color.GREEN);
                g2D.draw(eL);
                g2D.setColor(new Color(.45f, .0f, .85f));
                g2D.draw(eR);
            }
        } else {
            RangeCircle steal = new RangeCircle(new Color(.25f, .75f, .75f), game.underControl.stealRad);
            clientCircles.add(steal);
        }

        for (RangeCircle ri : clientCircles) {
            g2D.setStroke(new BasicStroke(1));
            g2D.setColor(ri.getColor());
            Titan t = game.underControl;
            if (ri.getRadius() > 0) {
                if (t.getType() != TitanType.ARTISAN || t.possession == 0) {
                    int w = (int) (ri.getRadius() * 2 * t.rangeFactor);
                    int h = w;
                    int x = (int) t.X + (t.width / 2) - w / 2;
                    int y = (int) t.Y + (t.height / 2) - h / 2;
                    Ellipse2D.Double ell = new Ellipse2D.Double(x, y, w, h);
                    ShapePayload c = new ShapePayload(ell);
                    Shape b = c.fromWithCamera(camX, camY);
                    g2D.draw(b);
                }
            }
        }

        if (game.colliders != null) {
            g2D.setStroke(new BasicStroke(6));
            for (ShapePayload c : game.colliders) {
                g2D.setColor(c.getColor());
                Shape b = c.fromWithCamera(camX, camY);
                g2D.draw(b);
            }
        }
    }

    private boolean ballLobMode() {
        for (int n = game.players.length - 1; n >= 0; n--) {
            if (game.players[n].actionState == Titan.TitanState.PASS
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
        if (game.underControl.X - 35 + dx > Game.MAX_X) {
            double remainder = game.underControl.X - 35 + dx - Game.MAX_X;
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
        drawNontitans(g2D, game.entityPool);
        drawEffectIcons(g2D);
        if (game.allSolids != null) {
            for (Entity e : game.allSolids) {
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
        //For the goalies
        for (int n = 0; n < 2; n++) {
            if (game.players[n].getX() + 35 <= game.ball.X) {
                g2D.drawImage(SelectClassSkins.pullImage(TitanType.GUARDIAN, 17), ((int) game.players[n].getX() - camX), ((int) game.players[n].getY() - camY), this);
            }
            if (game.players[n].getX() + 35 > game.ball.X) {
                g2D.drawImage(SelectClassSkins.pullImage(TitanType.GUARDIAN, 18), ((int) game.players[n].getX() - camX), ((int) game.players[n].getY() - camY), this);
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
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 1), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (facing == 2 && t.runningFrame == 0 && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 9), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            // Frame 1 run right
            if (facing == 1 && t.runningFrame == 1 && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 2), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            // Frame 2 run right
            if (facing == 1 && t.runningFrame == 2 && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 3), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            // Frame 1 run left
            if (facing == 2 && t.runningFrame == 1 && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 10), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            // Frame 2 run right
            if (facing == 2 && t.runningFrame == 2 && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 11), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            //Shoot
            if (facing == 1 && t.actionState == Titan.TitanState.SHOOT) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 8), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (facing == 2 && t.actionState == Titan.TitanState.SHOOT) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 16), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            //pass
            if (facing == 1 && t.actionState == Titan.TitanState.PASS) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 4), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (facing == 2 && t.actionState == Titan.TitanState.PASS) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 12), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            //attacks
            if (t.actionState == Titan.TitanState.A1 && facing == 2) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 5), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.A2 && facing == 2) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 6), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.STEAL && facing == 2) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 5), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.A1 && facing == 1) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 13), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.A2 && facing == 1) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 14), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
            if (t.actionState == Titan.TitanState.STEAL && facing == 1) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 13), ((int) t.X - camX), ((int) t.Y - camY), this);
            }
        }
        //The selection flag
        for (int sel = 1; sel <= game.players.length; sel++) {
            if (indexSelected() == sel) {
                Titan p = game.players[sel - 1];
                g2D.drawImage(selector.getImage(), ((int) p.getX() - camX + 27), ((int) p.getY() - camY - 22), this);
            }
        }
    }

    protected boolean invisible(Titan t) {
        return game.effectPool.hasEffect(t, EffectId.STEALTHED) && !game.effectPool.hasEffect(t, EffectId.FLARE);
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
                if (p.isCooldown()) {
                    f1 = bportalcd.getImage();
                    f2 = bportalcd.getImage();
                } else {
                    f1 = bportal1.getImage();
                    f2 = bportal2.getImage();
                }
            }
            if (e instanceof Portal) {
                Portal p = (Portal) e;
                if (p.isCooldown()) {
                    f1 = portalcd.getImage();
                    f2 = portalcd.getImage();
                } else {
                    f1 = portal1.getImage();
                    f2 = portal2.getImage();
                }
            }
            if (staticFrame % 2 == 1) {
                g2D.drawImage(f1, (int) e.getX() - camX, (int) e.getY() - camY, this);
            } else {
                g2D.drawImage(f2, (int) e.getX() - camX, (int) e.getY() - camY, this);
            }

        }
    }

    protected void drawEffectIcons(Graphics2D g2D) {
        Map<UUID, Integer> offset = new HashMap<>();
        for (Titan t : game.players) {
            offset.put(t.id, 4);
        }
        for (int i = 0; i < game.effectPool.getEffects().size(); i++) {
            Effect e = game.effectPool.getEffects().get(i);
            Entity en = game.effectPool.getOn().get(i);
            if (en instanceof Titan && !e.toString().contains("COOLDOWN") && !e.toString().contains("ATTACKED")) {
                Titan t = (Titan) en;
                if (offset.containsKey(t.id) && !invisible(t)) {
                    g2D.drawImage(e.getIconSmall(), (int) t.X + offset.get(t.id) - camX, (int) t.Y - 22 - camY, this);
                    offset.put(t.id, offset.get(t.id) + 16);
                }
            }
        }

    }

    protected void drawHealthBar(Graphics2D g2D, Entity e) {
        g2D.setColor(Color.DARK_GRAY);
        int xOffset = 0;
        if (e.team == TeamAffiliation.AWAY) {
            g2D.setColor(Color.WHITE);
            xOffset = 4;
        }
        if (e.team == TeamAffiliation.HOME) {
            g2D.setColor(Color.BLUE);
            xOffset = 4;
        }
        Rectangle healthBar = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 8 - camY, 50, 7);
        g2D.fill(healthBar);
        int hpPercentage = (int) (100 * e.health / e.maxHealth);
        //System.out.println(hpPercentage);
        Rectangle healthStat = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 6 - camY, hpPercentage / 2, 3);
        setColorBasedOnPercent(g2D, hpPercentage, false);
        g2D.fill(healthStat);
        if (e instanceof Titan) {
            Titan t = (Titan) e;
            if (t.fuel > 25) {
                g2D.setColor(new Color(1f, .50f, .1f));
            } else {
                g2D.setColor(new Color(0.7f, 0f, 0f));
            }
            Rectangle buustStat = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 1 - camY, (int) t.fuel / 2, 2);
            g2D.fill(buustStat);
        }
        if (e instanceof Portal) {
            Portal p = (Portal) e;
            if (p.isCooldown()) {
                System.out.println();
                double durSpent = p.cooldownPercentOver();
                setColorBasedOnPercent(g2D, durSpent, false);
                Rectangle durBar = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 1 - camY, (int) durSpent / 2, 2);
                g2D.fill(durBar);
            }
        }
        if (e instanceof BallPortal) {
            BallPortal p = (BallPortal) e;
            if (p.isCooldown()) {
                double durSpent = p.cooldownPercentOver();
                setColorBasedOnPercent(g2D, durSpent, false);
                Rectangle durBar = new Rectangle((int) e.X + xOffset - camX, (int) e.Y - 1 - camY, (int) durSpent / 2, 2);
                g2D.fill(durBar);
            }
        }
    }

    public void classFacts(Graphics2D g2D) {
        updateSelected();
        double speed = Titan.normalOutOfTenFromStat(Titan.titanSpeed, controlsHeld.classSelection);
        double hp = Titan.normalOutOfTenFromStat(Titan.titanHealth, controlsHeld.classSelection);
        double shoot = Titan.normalOutOfTenFromStat(Titan.titanShoot, controlsHeld.classSelection);
        double steal = Titan.normalOutOfTenFromStat(Titan.titanStealRad, controlsHeld.classSelection);
        g2D.setColor(Color.BLUE);
        g2D.setFont(new Font("Verdana", Font.BOLD, sconst.STAT_CAT_FONT));
        g2D.fill(new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
        g2D.drawString("Speed", sconst.STAT_CAT_X, 80);
        g2D.drawImage(new FastEffect(0, null)
                .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL, this);
        g2D.fill(new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 2, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
        g2D.drawString("Health", sconst.STAT_CAT_X, 180);
        g2D.drawImage(new HealEffect(0, null)
                .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL * 2, this);
        g2D.fill(new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 3, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
        g2D.drawString("Shot Power", sconst.STAT_CAT_X - 18, 280);
        g2D.drawImage(new ShootEffect(0, null)
                .getIcon(), sconst.STAT_FX_X, sconst.STAT_Y_SCL * 3, this);
        g2D.fill(new Rectangle(sconst.STAT_BAR_X, sconst.STAT_Y_SCL * 4, sconst.STAT_EXTERNAL_W, sconst.STAT_EXTERNAL_H));
        g2D.drawString("Steal Radius", sconst.STAT_CAT_X - 18, 380);
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
        g2D.drawString(text, sconst.OVR_DESC_FONT - 2, sconst.OVR_DESC_Y);
        g2D.setFont(new Font("Verdana", Font.BOLD, sconst.ABIL_DESC_FONT));

        g2D.drawImage(new CooldownE(0, null)
                .getIcon(), sconst.ICON_ABIL_X, sconst.E_ABIL_Y, this);
        g2D.drawString(e, sconst.DESC_ABIL_X, sconst.E_DESC_Y);
        g2D.drawImage(new CooldownR(0, null)
                .getIcon(), sconst.ICON_ABIL_X, sconst.R_ABIL_Y, this);
        g2D.drawString(r, sconst.DESC_ABIL_X, sconst.R_DESC_Y);
    }

    @Override
    public void keyPressed(KeyEvent ke) {
        int key = ke.getKeyCode();
        // ONLY IF SET ON debugCamera =1
        if (debugCamera == 1) {
            if (key == KeyEvent.VK_RIGHT && phase == 8 && keysEnabled) {
                camX += 10;
            }
            if (key == KeyEvent.VK_LEFT && phase == 8 && keysEnabled) {
                camX -= 10;
            }
            if (key == KeyEvent.VK_UP && phase == 8 && keysEnabled) {
                camY -= 10;
            }
            if (key == KeyEvent.VK_DOWN && phase == 8 && keysEnabled) {
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
        if (key == KeyEvent.VK_SPACE && (phase == 8 || phase == 9 || phase == 101) && keysEnabled) {
            camFollow = !camFollow;
            controlsHeld.CAM = true;
            //TODO play restart ding/click
        }
        if ((phase == 8 && keysEnabled) || phase == 101) {
            controlsConfig.mapKeyPress(this, controlsHeld, key);
            //shotSound.rewindStart();
            if (controlsConfig.toggleInstructions("" + key)) {
                instructionToggle = true;
            }
        }
        classKeys(key);
    }

    private void classKeys(int key) {
        if ((key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) && keysEnabled) {
            if (phase == 2) {
                cursor -= 1;
            }
        }
        if ((key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) && keysEnabled) {
            if (phase == 2) {
                cursor += 1;
            }
        }
        if ((key == KeyEvent.VK_UP || key == KeyEvent.VK_W) && keysEnabled) {
            if (phase == 2) {
                cursor -= 4;
            }
        }
        if ((key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) && keysEnabled) {
            if (phase == 2) {
                cursor += 4;
            }
        }
    }

    private boolean stateControls(int key) {
        if (key == KeyEvent.VK_SPACE && phase == 0 && keysEnabled) {
            controlsHeld.CAM = true;
            phase = 1;
            return true;
        }
        if (key == KeyEvent.VK_SPACE && phase == 1 && keysEnabled) {
            phase = 2;
            controlsHeld.CAM = true;
            return true;
        }
        if (key == KeyEvent.VK_SPACE && phase == 2 && keysEnabled) {
            controlsHeld.CAM = true;
            phase = 3;
            return true;
        }
        if (key == KeyEvent.VK_SPACE && phase == 3 && keysEnabled) {
            phase = 4;
            controlsHeld.CAM = true;
            return true;
        }
        if (key == KeyEvent.VK_SPACE && phase == 4 && keysEnabled) {
            phase = 6;
            controlsHeld.CAM = true;
            return true;
        }
        return false;
    }

    protected boolean masteriesKeys(int key) {
        if ((key == KeyEvent.VK_UP || key == KeyEvent.VK_W) && phase == 3 && keysEnabled) {
            masteriesIndex--;
            if (masteriesIndex < 0) {
                masteriesIndex = 8;
            }
        }
        if ((key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) && phase == 3 && keysEnabled) {
            masteriesIndex++;
            if (masteriesIndex > 8) {
                masteriesIndex = 0;
            }
        }
        if ((key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) && phase == 3 && keysEnabled) {
            masteryDelta(masteriesIndex, 1);
        }
        if ((key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) && phase == 3 && keysEnabled) {
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
    public void keyReleased(KeyEvent ke) {
        int key = ke.getKeyCode();
        if (phase == 8 || phase == 101 && keysEnabled) {
            controlsConfig.mapKeyRelease(controlsHeld, "" + key);
            //shotSound.rewindStart();
            if (controlsConfig.toggleInstructions("" + key)) {
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
    public void keyTyped(KeyEvent ke) {
    }

    public void updateSelected() {
        // Pass the value of the cursor to the class that uses it to choose the team of the player to load
        if (cursor == 1) controlsHeld.classSelection = TitanType.WARRIOR;
        if (cursor == 2) controlsHeld.classSelection = TitanType.RANGER;
        if (cursor == 3) controlsHeld.classSelection = TitanType.MAGE;
        if (cursor == 4) controlsHeld.classSelection = TitanType.SLASHER;
        if (cursor == 5) controlsHeld.classSelection = TitanType.MARKSMAN;
        if (cursor == 6) controlsHeld.classSelection = TitanType.ARTISAN;
        if (cursor == 7) controlsHeld.classSelection = TitanType.SUPPORT;
        if (cursor == 8) controlsHeld.classSelection = TitanType.STEALTH;
        if (cursor == 9) controlsHeld.classSelection = TitanType.POST;
        if (cursor == 10) controlsHeld.classSelection = TitanType.BUILDER;
        if (cursor > 10) controlsHeld.classSelection = TitanType.WARRIOR;
        if (masteries != controlsHeld.masteries) {
            controlsHeld.masteries = masteries;
        }
    }

    public void consumeCursorSelectClasses() {
        updateSelected();
        ObjectMapper mapper = new ObjectMapper();
        masteriesMap.put(controlsHeld.classSelection.toString(), masteries.asMap());
        try {
            File file = new File("masteries.json");
            file.delete();
            file.createNewFile();
            PrintStream ps = new PrintStream(file);
            ps.println(mapper.writeValueAsString(masteriesMap));
            ps.close();
        } catch (Exception ex1) {
            ex1.printStackTrace();
        }
        phase = 6;
    }

    public void randomTeam() {
    }

    protected void fireReconnect() {
        try {//rejoin game logic
            loginClient.check();
            if (!loginClient.gameId.equals("NOT QUEUED")) {
                phase = 6;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void initSurface() {
        // Instantiation of sound classes
        shotSound = new Sound();
        // Load the sounds
        shotSound.loadSound(shotSoundFile);
        addKeyListener(this);
        setBackground(Color.WHITE);
        setFocusable(true); // recognizes obtaining focus from the keyboard
        timer.start();
    }

    public void starting(Graphics2D g2D) {
        if (gamestart == null) {
            gamestart = Instant.now().plus(new Duration(5100));
        }
        g2D.drawImage(lobby.getImage(), 1, 1, this);
        Font font = new Font("Verdana", Font.BOLD, 24);
        g2D.setColor(Color.YELLOW);
        g2D.setFont(font);
        double milUntil = (new Duration(Instant.now(), gamestart)).getMillis();
        g2D.drawString(String.format("Starting in %1.1f seconds", milUntil / 1000.0), 345, 220);
        font = new Font("Verdana", Font.BOLD, 48);
        g2D.setColor(Color.RED);
        g2D.setFont(font);
        g2D.drawString("STARTING", 418, 480);
        if ((game == null || game.phase < 8) && Instant.now().isAfter(gamestart.plus(new Duration(500)))) {
            //TODO this messes us up bad somehow for everyone but the last client to connect
            g2D.setFont(new Font("Verdana", Font.BOLD, 12));
            g2D.drawString("(Client may be disconnected, try alt-F4 and restarting client)", 80, 520);
        }
    }

    public void lobby(Graphics2D g2D) {
        g2D.drawImage(lobby.getImage(), 1, 1, this);
        Font font = new Font("Verdana", Font.BOLD, 24);
        g2D.setColor(Color.YELLOW);
        g2D.setFont(font);
        g2D.drawString("Waiting for players...", 342, 220);
        font = new Font("Verdana", Font.BOLD, 48);
        g2D.setColor(Color.GREEN);
        g2D.setFont(font);
        g2D.drawString("READY", 430, 480);
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
        if (phase == 8 || phase == 101) {
            Font font = new Font("Verdana", Font.BOLD, 45);
            g2D.setFont(font);
            String goalsHome = Integer.toString((int) game.home.score);
            String minorGoalsHome = Integer.toString((int) ((game.home.score - (int) game.home.score) * 4));
            String goalsAway = Integer.toString((int) game.away.score);
            String minorGoalsAway = Integer.toString((int) ((game.away.score - (int) game.away.score) * 4));

            g2D.setColor(new Color(0f, 0f, 1f, .5f));
            g2D.drawString(goalsHome, (int) (.094 * xSize), (int) (.652 * ySize));
            g2D.setColor(new Color(1f, 1f, 1f, .5f));
            g2D.drawString(goalsAway, (int) (.416 * xSize), (int) (.652 * ySize));

            //draw minor goals
            font = new Font("Verdana", Font.BOLD, 32);
            g2D.setFont(font);
            setColorFromCharge(g2D, minorGoalsHome);
            g2D.drawString(minorGoalsHome + "/4", (int) (.120 * xSize), (int) (.649 * ySize));
            setColorFromCharge(g2D, minorGoalsAway);
            g2D.drawString(minorGoalsAway + "/4", (int) (.442 * xSize), (int) (.649 * ySize));

            g2D.setColor(Color.RED);
            int x = xSize / 4;
            //addBoostIcons();
            for (int i = 0; i < game.effectPool.getEffects().size(); i++) {
                Effect e = game.effectPool.getEffects().get(i);
                Entity on = game.effectPool.getOn().get(i);
                if (game.underControl.id.equals(on.id) && !e.toString().contains("ATTACKED")) {
                    g2D.setFont(new Font("Verdana", Font.PLAIN, 72));
                    if (e.effect == EffectId.ROOT) {
                        g2D.setColor(new Color(.36f, .51f, .28f, .4f));
                        g2D.drawString("Rooted!", 450, 300);
                    }
                    if (e.effect == EffectId.SLOW) {
                        g2D.setColor(new Color(.45f, .9f, .75f, .4f));
                        g2D.drawString("Slowed!", 450, 300);
                    }
                    if (e.effect == EffectId.STUN) {
                        g2D.setColor(new Color(1f, .74f, 0f, .4f));
                        g2D.drawString("Stunned!", 450, 300);
                    }
                    if (e.effect == EffectId.STEAL) {
                        g2D.setColor(new Color(0f, 0f, 0f, .4f));
                        g2D.drawString("Stolen!", 450, 300);
                    }
                    if (e.getIcon() != null) {
                        Composite originalComposite = g2D.getComposite();
                        g2D.setComposite(makeComposite(.5f));
                        g2D.drawImage(e.getIcon(), x, 689, this);
                        g2D.setComposite(originalComposite);
                        g2D.setColor(new Color(1f, 1f, 1f, .5f));
                        Arc2D.Double background = new Arc2D.Double(x, 657, 32, 32, 90, -360, Arc2D.PIE);
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
                        Arc2D.Double percent = new Arc2D.Double(x + 2, 659, 28, 28, 90, coverage, Arc2D.PIE);
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
        g2D.setFont(font);
        final double FPS = 1000 / GAMETICK_MS;
        String timeStr = "";
        double time = (game.framesSinceStart / FPS);
        time = (int)(time*10.0) / 10.0;
        timeStr += time;
        g2D.drawString(timeStr, (int)(.260*xSize), (int)(.652*ySize));
        g2D.setColor(new Color(0f, 0f, 0f, .4f));
        setBottomText(g2D, (int) time);
        g2D.drawString(bottomText, (int)(.240*xSize), (int)(.615*ySize));
    }

    private void setBottomText(Graphics2D g2D, int timer) {
        Font font = new Font("Verdana", Font.BOLD, 16);
        g2D.setFont(font);
        bottomText = "";
        final int WARN=10, FWARN=5, CHWARN = 2;
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
        else if(timer >= game.PAIN_DISABLE_TIME - WARN && timer < game.PAIN_DISABLE_TIME - FWARN){
            g2D.setColor(new Color(.9f, .9f, 0f, .6f));
            bottomText = "HOOP PROXIMITY DMG DISABLING WARNING";
        }
        else if(timer >= game.PAIN_DISABLE_TIME - FWARN && timer < game.PAIN_DISABLE_TIME){
            g2D.setColor(new Color(1f,0f, 0f, .6f));
            bottomText = "HOOP PROXIMITY DMG DISABLING WARNING";
        }
        else if(timer >= game.PAIN_DISABLE_TIME && timer < game.PAIN_DISABLE_TIME - CHWARN){
            g2D.setColor(new Color(1f,0f, 0f, .6f));
            bottomText = "HOOP PROXIMITY DMG DISABLED";
        }
        else if(timer >= game.SUDDEN_DEATH_TIME - WARN && timer < game.SUDDEN_DEATH_TIME - FWARN){
            g2D.setColor(new Color(.9f, .9f, 0f, .6f));
            bottomText = "SUDDEN DEATH WARNING";
        }
        else if(timer >= game.SUDDEN_DEATH_TIME - FWARN && timer < game.SUDDEN_DEATH_TIME){
            g2D.setColor(new Color(1f,0f, 0f, .6f));
            bottomText = "SUDDEN DEATH WARNING";
        }
        else if(timer >= game.SUDDEN_DEATH_TIME && timer < game.SUDDEN_DEATH_TIME - CHWARN){
            g2D.setColor(new Color(1f,0f, 0f, 1f));
            bottomText = "SUDDEN DEATH ENABLED";
        }
        else if(timer >= game.TIE_TIME - WARN && timer < game.TIE_TIME - FWARN){
            g2D.setColor(new Color(.9f, .9f, 0f, 1f));
            bottomText = "TIE GAME WARNING";
        }
        else if(timer >= game.TIE_TIME - FWARN && timer < game.TIE_TIME){
            g2D.setColor(new Color(1f,0f, 0f, 1f));
            bottomText = "TIE GAME WARNING";
        }
        else if(timer >= game.TIE_TIME && timer < game.TIE_TIME - CHWARN){
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
        thepublic();
        repaint();
    }

    public void thepublic() {
        crowdFrameCount += 1;
        if (crowdFrameCount == 50) crowdFrame = 2;
        if (crowdFrameCount == 100) {
            crowdFrame = 1;
            crowdFrameCount = 1;
        }
    }

    public void tutorial(Graphics2D g2D) {
        g2D.drawImage(tutorial.getImage(), 1, 1, this);
        Font font = new Font("Verdana", Font.PLAIN, 65);
        g2D.setFont(font);
        g2D.drawString("Space to proceed", 5, 705);
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

    protected void drawClassScreen(Graphics2D g2D) {
        g2D.drawImage(select.getImage(), 1, 1, this);
        g2D.setFont(new Font("Verdana", Font.PLAIN, 16));
        g2D.setColor(new Color(1f, 0f, 0f));
        g2D.drawString("Navigate characters with WASD, confirm with SPACE", 350, 170);
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
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.WARRIOR, staticFrame), 160, 200, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.RANGER, staticFrame), 360, 200, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.MAGE, staticFrame), 560, 200, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.SLASHER, staticFrame), 760, 200, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.MARKSMAN, staticFrame), 160, 320, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.ARTISAN, staticFrame), 360, 320, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.SUPPORT, staticFrame), 560, 320, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.STEALTH, staticFrame), 760, 320, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.POST, staticFrame), 160, 440, this);
        g2D.drawImage(SelectClassSkins.pullImage(TitanType.BUILDER, staticFrame), 360, 440, this);

        classFacts(g2D);
        //g2D.drawImage(SelectClassSkins.pullImage(TitanType.POST, staticFrame), 560, 440, this);
        //g2D.drawImage(SelectClassSkins.pullImage(TitanType.POST, staticFrame), 760, 440, this);
        if (cursor > 16) {
            cursor %= 16;
        }
        if (cursor <= 0) {
            cursor = -1 * (cursor);
            if (cursor == 0) {
                cursor = 1;
            }
        }
        if (cursor == 1) g2D.drawImage(classCursor.getImage(), 155, 195, this);
        if (cursor == 2) g2D.drawImage(classCursor.getImage(), 355, 195, this);
        if (cursor == 3) g2D.drawImage(classCursor.getImage(), 555, 195, this);
        if (cursor == 4) g2D.drawImage(classCursor.getImage(), 755, 195, this);
        if (cursor == 5) g2D.drawImage(classCursor.getImage(), 155, 315, this);
        if (cursor == 6) g2D.drawImage(classCursor.getImage(), 355, 315, this);
        if (cursor == 7) g2D.drawImage(classCursor.getImage(), 555, 315, this);
        if (cursor == 8) g2D.drawImage(classCursor.getImage(), 755, 315, this);
        if (cursor == 9) g2D.drawImage(classCursor.getImage(), 155, 435, this);
        if (cursor == 10) g2D.drawImage(classCursor.getImage(), 355, 435, this);
        if (cursor == 11) g2D.drawImage(classCursor.getImage(), 555, 435, this);
        if (cursor == 12) g2D.drawImage(classCursor.getImage(), 755, 435, this);
        if (cursor == 13) g2D.drawImage(classCursor.getImage(), 155, 515, this);
        if (cursor == 14) g2D.drawImage(classCursor.getImage(), 355, 515, this);
        if (cursor == 15) g2D.drawImage(classCursor.getImage(), 555, 515, this);
        if (cursor == 16) g2D.drawImage(classCursor.getImage(), 755, 515, this);
        g2D.setColor(new Color(0f, 0f, 1f));
        g2D.drawString("TUTORIAL", 169, 555);
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
                System.out.println("suspending game thread");
                exec.shutdown();
            } else {
                try {
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
