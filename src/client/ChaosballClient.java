package client;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.mashape.unirest.http.exceptions.UnirestException;
import gameserver.Game;
import gameserver.effects.EffectId;
import gameserver.effects.cooldowns.CooldownE;
import gameserver.effects.cooldowns.CooldownR;
import gameserver.effects.effects.Effect;
import gameserver.effects.effects.FastEffect;
import gameserver.effects.effects.HealEffect;
import gameserver.effects.effects.ShootEffect;
import gameserver.engine.GoalHoop;
import gameserver.engine.Team;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Entity;
import gameserver.entity.RangeCircle;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.BallPortal;
import gameserver.entity.minions.Portal;
import gameserver.entity.minions.Wall;
import gameserver.targeting.ShapePayload;
import networking.KryoRegistry;
import org.json.JSONObject;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChaosballClient extends JPanel implements ActionListener, KeyListener {
    private Client client = new Client(8192*2, 32768*2);
    private String gameID;
    private Game game;
    ClientPacket controlsHeld = new ClientPacket();
    int debugCamera = 0;
    int cursor = 1; // For deciding classes
    public Timer timer = new Timer(23, this);
    public Random rand;
    int camX = 0;
    int camY = 0;
    int round = 1;
    int ballFrame = 0;
    int ballFrameCounter = 0;
    int crowdFrame = 1;//not used yet
    int crowdFrameCount = 0;
    public Sound shotSound;
    File shotSoundFile = new File("res/Sound/shotsound.wav");
    StaticImage intro = new StaticImage();
    Images ballTexture = new Images();
    Images ballBTexture = new Images();
    Images ballFTexture = new Images();
    Images ballFBTexture = new Images();
    Images ballPtr = new Images();
    Images ballFPtr = new Images();
    private int phase;
    private Kryo kryo = client.getKryo();
    private int staticFrame = 0;
    private boolean camFollow = false;
    int xSize, ySize;
    private boolean keysEnabled = true;
    private String token;


    public int indexSelected(){
        for(int i=0; i<game.players.length; i++){
            if(game.players[i].id.equals(game.underControl.id)){
                return i +1;
            }
        }
        return -1;
    }

    public void paintComponent(Graphics g) {
        Graphics2D g2D = (Graphics2D) g;
        super.paintComponent(g);
        if(game != null && game.ended){
            Team team = teamFromUnderControl();
            Team enemy = enemyFromUnderControl();
            if(team.score > enemy.score){
                g2D.drawImage(victory.getImage(), 300, 200, this);
            }else{
                g2D.drawImage(defeat.getImage(), 300, 200, this);
            }
            gameEndStatsAndRanks(g2D, game.underControl);
            return;
        }
        if (phase == 0) creditPanel(g);
        if (phase == 50) tutorial(g);
        if (phase == 2) selectClasses(g);
        if (phase == 3) consumeCursorSelectClasses();
        if (phase == 4)
            randomTeam();
        if (phase == 5) {
            phase++;
        }
        if (phase == 6){
            initTeamFromCursor();
            try {
                clientInitialize(g);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (UnirestException e) {
                e.printStackTrace();
            }
        }
        if(phase == 7){
            lobby(g);
        }
        if (phase == 8 || phase == 9 || phase == 10) {
            updateFrameBall();
            doDrawing(g); // do drawing of screen
            displayBallArrow(g);
            displayScore(g); // call the method to display the game score
        }

        Toolkit.getDefaultToolkit().sync();
    }

    private void gameEndStatsAndRanks(Graphics2D g2D, Titan underControl) {
        JSONObject stats = game.stats.statsOf(game.clientFromTitan(underControl));
        JSONObject ranks = game.stats.ranksOf(game.clientFromTitan(underControl));
        int y = 425;
        Font font = new Font("Verdana", Font.PLAIN, 18);
        g2D.setFont(font);
        for(String stat : stats.keySet()){
            g2D.setColor(Color.BLACK);
            g2D.drawString(stat + ": " + stats.get(stat) ,310, y);
            if(ranks.has(stat) && ((int) ranks.get(stat) >= 1)){
                int rank = (int) ranks.get(stat);
                if(rank == 1){
                    g2D.setColor(new Color(1f, .843f, 0f));//gold silver bronze
                }
                if(rank == 2){
                    g2D.setColor(new Color(.753f, .753f, .753f));//gold silver bronze
                }
                if(rank == 3){
                    g2D.setColor(new Color(.804f, .498f, .196f));//gold silver bronze
                }
                g2D.drawString(""+rank ,295, y);
            }
            y+= 23;
        }
    }

    public void clientInitialize(Graphics g) throws IOException, InterruptedException, UnirestException {
        crowdFrame = 1;
        crowdFrameCount = 0;
        ballFrame = 0;
        ballFrameCounter = 0;
        camX = 500;
        camY = 300;
        client.start();
        //client.setHardy(true);
        gameID = waitForGame(g);
        client.connect(5000, System.getenv("host"), 54555);
        //client.connect(5000, "127.0.0.1", 54555);
        client.addListener(new Listener() {
            public synchronized void received(Connection connection, Object object) {
                if (object instanceof Game) {
                    game = (Game) object;
                    game.began = true;
                    phase = game.phase;
                    controlsHeld.gameID = gameID;
                    controlsHeld.token = token;
                    try {
                        client.sendTCP(controlsHeld); //Automatically respond to the gameserver with tutorial when we get a new state
                    }
                    catch(KryoException e){
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
        Runnable updateClients = () -> {
            controlsHeld.gameID = gameID;
            controlsHeld.token = token;
            client.sendTCP(controlsHeld);
        };
        exec.scheduleAtFixedRate(updateClients, 1, 20, TimeUnit.MILLISECONDS);
    }

    private String waitForGame(Graphics g) throws UnirestException, InterruptedException {
        HttpClient.authenticate();
        token = HttpClient.token;
        HttpClient.join();
        while(HttpClient.gameId == null
        || HttpClient.gameId.equals("NOT QUEUED")
        || HttpClient.gameId.equals("WAITING")){
            lobby(g);
            if(HttpClient.gameId.equals("NOT QUEUED")){
                throw new UnirestException("Server not accepting connections");
            }
            HttpClient.check();
            System.out.println(HttpClient.gameId);
            Thread.sleep(100);
        }
        return HttpClient.gameId;
    }

    public void rotTranslateArrow(Graphics2D g2D, int xt, int yt, double rot){
        // create the transform, note that the transformations happen
        // in reversed order (so check them backwards)
        AffineTransform at = new AffineTransform();
        // 4. translate it to the center of the component
        at.translate(xt, yt);
        // 3. do the actual rotation
        at.rotate(Math.toRadians(rot));
        // 1. translate the object so that you rotate it around the center (easier :))
        at.translate(-20, -9);
        if(game.anyPoss()){
            g2D.drawImage(ballPtr.getImage(), at, this);
        }
        else{
            g2D.drawImage(ballFPtr.getImage(), at, this);
        }
    }

    public void displayBallArrow(Graphics g){
        if (game == null) {
            //return;
        }
        Graphics2D g2D = (Graphics2D) g;
        int x = (int) (game.ball.X + 7 - camX);
        int y = (int) (game.ball.Y + 7 - camY);
        final int XCORR = xSize/3;
        final int YCORR = ySize/3;
        double rot = 0;
        if(x< 0){
            rot = 180;
            int yt = y;
            if(y < 0){//diag
                rot = 225;
                yt = 20;
            }
            else if(y > ySize -YCORR){//diag plus magic number 383 because ahhh idk
                rot = 135;
                yt = ySize - YCORR - 20;
            }
            rotTranslateArrow(g2D, 20, yt, rot);
        }
        else if(x> xSize - XCORR){
            int yt = y;
            if(y < 0){//diag
                rot = 315;
                yt = 20;
            }
            else if(y > ySize -YCORR){//diag
                rot = 45;
                yt = ySize -YCORR - 20;
            }
            rotTranslateArrow(g2D, xSize- XCORR - 20, yt, rot);
        }
        else if(y < 0){
            rot = 270;
            rotTranslateArrow(g2D, x, 20, rot);
        }
        else if(y > ySize -YCORR){
            rot = 90;
            rotTranslateArrow(g2D, x, ySize- YCORR - 20, rot);
        }
    }

    public void postgoalReset() {
        crowdFrame = 1;
        crowdFrameCount = 0;
        ballFrame = 0;
        ballFrameCounter = 0;
        camX = 500;
        camY = 300;
        game.serverGoalScored();
    }

    private void doDrawing(Graphics g) {
        Graphics2D g2D = (Graphics2D) g;
        if (game == null) {
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
            g2D.drawImage(goalScored.getImage(), (220), (250), this);
        }
        if (game.ballVisible == true) {
            if(game.anyPoss()) {
                if (ballFrame == 0) {
                    g2D.drawImage(ballTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
                if (ballFrame == 1) {
                    g2D.drawImage(ballBTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
            }else{
                if (ballFrame == 0) {
                    g2D.drawImage(ballFTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
                if (ballFrame == 1) {
                    g2D.drawImage(ballFBTexture.getImage(), ((int) game.ball.X - camX), ((int) game.ball.Y - camY), this);
                }
            }
        }
        if(camFollow){
            camX = (int)game.underControl.X+35 - (this.xSize/3);
            //if (camX > 820) camX = 820;
            if (camX < 0) camX = 0;
            camY = (int)game.underControl.Y+35 - (this.ySize/3);
            //if (camY > 480) camY = 480;
            if (camY < 0) camY = 0;
        }
        if (debugCamera == 0) {
            if ((game.ball.X - camX) > 630) {
                camX += 5;
                //if (camX > 820) camX = 820;
            }
            if ((game.ball.X - camX) < 630) {
                camX -= 5;
                if (camX < 0) camX = 0;
            }
            if ((game.ball.Y - camY) > 350) {
                camY += 5;
                //if (camY > 480) camY = 480;
            }
            if ((game.ball.Y - camY) < 350) {
                camY -= 5;
                if (camY < 0) camY = 0;
            }
        }

        ArrayList<RangeCircle> clientCircles = new ArrayList<>();
        for(RangeCircle ri : game.underControl.rangeIndicators){
            clientCircles.add(ri);
        }

        if(game.underControl.possession == 1){
            double pow = game.underControl.throwPower;
            RangeCircle shot = new RangeCircle(new Color(.65f,0f,0f), (int) (320 * pow));
            RangeCircle pass = new RangeCircle(Color.BLUE, (int) (186 * pow));
            clientCircles.add(shot);
            clientCircles.add(pass);
        }
        else{
            RangeCircle steal = new RangeCircle(new Color(.25f,.75f,.75f), 80);
            clientCircles.add(steal);
        }

        for(RangeCircle ri : clientCircles){
            g2D.setStroke(new BasicStroke(1));
            g2D.setColor(ri.getColor());
            Titan t = game.underControl;
            if(ri.getRadius() > 0){
                int w = ri.getRadius() * 2;
                int h = ri.getRadius() * 2;
                int x = (int)t.X + (t.width / 2) - w / 2;
                int y = (int)t.Y + (t.height / 2) - w / 2;
                Ellipse2D.Double ell = new Ellipse2D.Double(x, y, w, h);
                ShapePayload c = new ShapePayload(ell);
                Shape b = c.fromWithCamera(camX, camY);
                g2D.draw(b);
            }
        }

        if (game.colliders != null) {
            g2D.setStroke(new BasicStroke(6));
            g2D.setColor(Color.YELLOW);
            for (ShapePayload c : game.colliders) {
                Shape b = c.fromWithCamera(camX, camY);
                g2D.draw(b);
            }
        }
    }

    private Team teamFromUnderControl() {
        TeamAffiliation affil = game.underControl.team;
        if(affil.equals(game.home.which)){
            return game.home;
        }
        return game.away;
    }

    private Team enemyFromUnderControl() {
        TeamAffiliation affil = game.underControl.team;
        if(affil.equals(game.home.which)){
            return game.away;
        }
        return game.home;
    }

    private void drawEntities(Graphics2D g2D) {
        drawNontitans(g2D, game.entityPool);
        drawEffectIcons(g2D);
        if (game.allSolids != null) {
            for (Entity e : game.allSolids) {
                if (e.health > 0.0) {
                    if (e instanceof Titan) {
                        Titan t = (Titan) e;
                        if (game.underControl.team != t.team &&
                                invisible(t)){
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
                g2D.drawImage(SelectClassSkins.pullImage(TitanType.GUARDIAN, 17), ((int)game.players[n].getX() - camX), ((int)game.players[n].getY() - camY), this);
            }
            if (game.players[n].getX() + 35 > game.ball.X) {
                g2D.drawImage(SelectClassSkins.pullImage(TitanType.GUARDIAN, 18), ((int)game.players[n].getX() - camX), ((int)game.players[n].getY() - camY), this);
            }
        }
        //Skip the goalies for the next display loop
        for (int i = 2; i < game.players.length; i++) {
            Titan t = game.players[i];
            if (game.underControl.team != t.team &&
                    invisible(game.players[i])) {
                continue;
            }//Display NOTHING if stealthed
            int facing = 0;
            if(t.facing >= 90 && t.facing < 270){
                facing = 2;
            }else{
                facing = 1;
            }
            //Stills
            if (facing == 1 && t.runningFrame == 0  && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 1), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            if (facing == 2 && t.runningFrame == 0 && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 9), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            // Frame 1 run right
            if (facing == 1 && t.runningFrame == 1 && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 2), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            // Frame 2 run right
            if (facing == 1 && t.runningFrame == 2 && t.actionState == Titan.TitanState.IDLE) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 3), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            // Frame 1 run left
            if (facing == 2 && t.runningFrame == 1 && t.actionState == Titan.TitanState.IDLE ) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 10), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            // Frame 2 run right
            if (facing == 2 && t.runningFrame == 2 && t.actionState == Titan.TitanState.IDLE ) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 11), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            //Shoot
            if (facing == 1 && t.actionState == Titan.TitanState.SHOOT) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 8), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            if (facing == 2 && t.actionState == Titan.TitanState.SHOOT) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 16), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            //pass
            if (facing == 1 && t.actionState == Titan.TitanState.PASS) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 4), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            if (facing == 2 && t.actionState == Titan.TitanState.PASS) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 12), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            //attacks
            if  (t.actionState == Titan.TitanState.A1 && facing == 2) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 5), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            if  (t.actionState == Titan.TitanState.A2 && facing == 2) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 6), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            if  (t.actionState == Titan.TitanState.STEAL && facing == 2) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 5), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            if  (t.actionState == Titan.TitanState.A1 && facing == 1) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 13), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            if  (t.actionState == Titan.TitanState.A2 && facing == 1) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 14), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
            if  (t.actionState == Titan.TitanState.STEAL && facing == 1) {
                g2D.drawImage(SelectClassSkins.pullImage(t.getType(), 13), ((int)t.X - camX), ((int)t.Y - camY), this);
            }
        }
        //The selection flag
        for (int sel = 1; sel <= game.players.length; sel++) {
            if (indexSelected() == sel) {
                Titan p = game.players[sel - 1];
                g2D.drawImage(selector.getImage(), ((int)p.getX() - camX + 27), ((int)p.getY() - camY - 22), this);
            }
        }
    }

    private boolean invisible(Titan t) {
        return game.effectPool.hasEffect(t, EffectId.STEALTHED) && !game.effectPool.hasEffect(t, EffectId.FLARE);
    }


    private void drawNontitans(Graphics2D g2D, java.util.List<Entity> draw) {
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
                g2D.drawImage(f1, (int)e.getX() - camX, (int)e.getY() - camY, this);
            } else {
                g2D.drawImage(f2, (int)e.getX() - camX, (int)e.getY() - camY, this);
            }

        }
    }

    private void drawEffectIcons(Graphics2D g2D) {
        Map<UUID, Integer> offset = new HashMap<>();
        for(Titan t: game.players){
            offset.put(t.id, 4);
        }
        for(int i=0; i< game.effectPool.getEffects().size(); i++){
            Effect e = game.effectPool.getEffects().get(i);
            Entity en = game.effectPool.getOn().get(i);
            if(en instanceof Titan && !e.toString().contains("COOLDOWN")){
                Titan t = (Titan) en;
                if(!invisible(t)) {
                    g2D.drawImage(e.getIconSmall(), (int) t.X + offset.get(t.id) - camX, (int) t.Y - 22 - camY, this);
                    offset.put(t.id, offset.get(t.id) + 16);
                }
            }
        }

    }

    private void drawHealthBar(Graphics2D g2D, Entity e) {
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
        Rectangle healthBar = new Rectangle((int)e.X + xOffset - camX, (int)e.Y - 6 - camY, 50, 7);
        g2D.fill(healthBar);
        int hpPercentage = (int) (100 * e.health / e.maxHealth);
        //System.out.println(hpPercentage);
        Rectangle healthStat = new Rectangle((int)e.X + xOffset - camX, (int)e.Y - 4 - camY, hpPercentage / 2, 3);
        setColorBasedOnPercent(g2D, hpPercentage, false);
        g2D.fill(healthStat);
    }

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
            System.out.println("camX = " + camX);
            System.out.println("camY = " + camY);
            System.out.println("-------------");
        }
        if (key == KeyEvent.VK_SPACE && phase == 5 && keysEnabled) {
            controlsHeld.SPACE = true;
            phase = 6;
        }
        if (key == KeyEvent.VK_SPACE && phase == 2 && keysEnabled) {
            controlsHeld.SPACE = true;
            phase = 3;
        }
        if (key == KeyEvent.VK_SPACE && phase == 50 && keysEnabled) {
            phase = 2;
            controlsHeld.SPACE = true;
        }
        if (key == KeyEvent.VK_SPACE && phase == 0 && keysEnabled) { // show the logo
            controlsHeld.SPACE = true;
            phase = 50;
        }
        if (key == KeyEvent.VK_SPACE && (phase == 8 || phase == 9 && keysEnabled)) {
            camFollow = !camFollow;
            controlsHeld.SPACE = true;
            //TODO play restart ding/click
        }
        if (key == KeyEvent.VK_SPACE && phase == 9 && keysEnabled) {
            postgoalReset();
            controlsHeld.SPACE = true;
            phase = 8;
        }
        if (key == KeyEvent.VK_R && phase == 8 && keysEnabled) {
            controlsHeld.R = true;
            //shotSound.rewindStart();
        }
        if (key == KeyEvent.VK_E && phase == 8  && keysEnabled) {
            //shotSound.rewindStart();
            controlsHeld.E = true;
        }
        if (key == KeyEvent.VK_Q && phase == 8  && keysEnabled) {
            controlsHeld.Q = true;
        }
        // Selection switch
        if (key == KeyEvent.VK_Z && phase == 8 && keysEnabled) {
            controlsHeld.Z = true;
        }
        if (key == KeyEvent.VK_D && phase == 8 && keysEnabled ) {
            controlsHeld.D = true;
        }
        if (key == KeyEvent.VK_A && phase == 8 && keysEnabled) {
            controlsHeld.A = true;
        }
        if (key == KeyEvent.VK_W && phase == 8 && keysEnabled) {
            controlsHeld.W = true;
        }
        if (key == KeyEvent.VK_S && phase == 8 && keysEnabled) {
            controlsHeld.S = true;
        }
        if ((key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) && keysEnabled) {
            if (phase == 2 && cursor > 1){
                cursor -= 1;
            }
        }
        if ((key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) && keysEnabled) {
            if (phase == 2 && cursor < 12) {
                cursor += 1;
            }
        }
        if ((key == KeyEvent.VK_UP || key == KeyEvent.VK_W) && keysEnabled) {
            if (phase == 2 && cursor < 12) {
                cursor -= 4;
            }
        }
        if ((key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) && keysEnabled) {
            if (phase == 2 && cursor < 12){
                cursor += 4;
            }
        }
    }

    public void classFacts(Graphics2D g2D){
        updateSelected();
        double speed = Titan.normalOutOfTenFromStat(Titan.titanSpeed, controlsHeld.classSelecton);
        double hp = Titan.normalOutOfTenFromStat(Titan.titanHealth, controlsHeld.classSelecton);
        double shoot = Titan.normalOutOfTenFromStat(Titan.titanShoot, controlsHeld.classSelecton);
        String e = Titan.titanEText.get(controlsHeld.classSelecton);
        String r = Titan.titanRText.get(controlsHeld.classSelecton);
        String text = Titan.titanText.get(controlsHeld.classSelecton);
        g2D.setColor(Color.BLUE);
        g2D.setFont(new Font("Verdana", Font.BOLD, 20));
        g2D.fill(new Rectangle(1100, 100, 106, 9));
        g2D.drawString("Speed", 1045, 80);
        g2D.drawImage(new FastEffect(0,null)
                .getIcon(), 1060, 100, this);
        g2D.fill(new Rectangle(1100, 200, 106, 9));
        g2D.drawString("Health", 1045, 180);
        g2D.drawImage(new HealEffect(0,null)
                .getIcon(), 1060, 200, this);
        g2D.fill(new Rectangle(1100, 300, 106, 9));
        g2D.drawString("Shot Power", 1027, 280);
        g2D.drawImage(new ShootEffect(0,null)
                .getIcon(), 1060, 300, this);

        g2D.setColor(Color.BLACK);
        g2D.setFont(new Font("Verdana", Font.BOLD, 22));
        g2D.drawString(text, 20, 690);
        g2D.setFont(new Font("Verdana", Font.BOLD, 14));

        g2D.drawImage(new CooldownE(0,null)
                .getIcon(), 712, 445, this);
        g2D.drawString(e, 750, 460);
        g2D.drawImage(new CooldownR(0,null)
                .getIcon(), 712, 505, this);
        g2D.drawString(r, 750, 520);

        setColorBasedOnPercent(g2D, speed*10.0, false);
        g2D.fill(new Rectangle(1103, 102, (int)(speed*10.0), 5));
        setColorBasedOnPercent(g2D, hp*10.0, false);
        g2D.fill(new Rectangle(1103, 202, (int)(hp*10.0), 5));
        setColorBasedOnPercent(g2D, shoot*10.0, false);
        g2D.fill(new Rectangle(1103, 302, (int)(shoot*10.0), 5));
    }

    public void keyReleased(KeyEvent ke) {
        int key = ke.getKeyCode();
        if (key == KeyEvent.VK_SPACE) {
            controlsHeld.SPACE = false;
        }
        if (key == KeyEvent.VK_D && phase == 8) {
            controlsHeld.D = false;
            for (Titan p : game.players) {
                p.runningFrame = 0;
                p.diagonalRunDir = 0;
            }
        }
        if (key == KeyEvent.VK_A && phase == 8) {
            controlsHeld.A = false;
            for (Titan p : game.players) {
                p.runningFrame = 0;
                p.diagonalRunDir = 0;
            }
        }
        if (key == KeyEvent.VK_W && phase == 8) {
            controlsHeld.W = false;
            for (Titan p : game.players) {
                p.runningFrame = 0;
                p.dirToBall = 0;
            }
        }
        if (key == KeyEvent.VK_S && phase == 8) {
            controlsHeld.S = false;
            for (Titan p : game.players) {
                p.runningFrame = 0;
                p.dirToBall = 0;
            }
        }
        if(key == KeyEvent.VK_SPACE && (this.phase == 8 || this.phase == 9)){
            //camFollow = false;
            controlsHeld.SPACE = false;
        }
        if (key == KeyEvent.VK_Z) {
            controlsHeld.Z = false;
        }
        if (key == KeyEvent.VK_Q) {
            controlsHeld.Q = false;
        }
        if (key == KeyEvent.VK_E) {
            controlsHeld.E = false;
        }
        if (key == KeyEvent.VK_R) {
            controlsHeld.R = false;
        }
    }

    public void keyTyped(KeyEvent ke) {
    }

    public void updateSelected(){
        // Pass the value of the cursor to the class that uses it to choose the team of the player to load
        if (cursor == 1) controlsHeld.classSelecton = TitanType.WARRIOR;
        if (cursor == 2) controlsHeld.classSelecton = TitanType.RANGER;
        if (cursor == 3) controlsHeld.classSelecton = TitanType.MAGE;
        if (cursor == 4) controlsHeld.classSelecton = TitanType.SLASHER;
        if (cursor == 5) controlsHeld.classSelecton = TitanType.MARKSMAN;
        if (cursor == 6) controlsHeld.classSelecton = TitanType.ARTISAN;
        if (cursor == 7) controlsHeld.classSelecton = TitanType.SUPPORT;
        if (cursor == 8) controlsHeld.classSelecton = TitanType.STEALTH;
        if (cursor == 9) controlsHeld.classSelecton = TitanType.POST;
        if (cursor == 10) controlsHeld.classSelecton = TitanType.BUILDER;
        if (cursor == 11) controlsHeld.classSelecton = TitanType.WARRIOR;
        if (cursor == 12) controlsHeld.classSelecton = TitanType.WARRIOR;
    }

    public void consumeCursorSelectClasses() {
        updateSelected();
        phase = 4;
    }

    public void initTeamFromCursor() {
        // Pass the value of the cursor to the class that uses it to choose the opposing team to load
        phase = 7;
    }

    public void randomTeam() {
        rand = new Random();
        cursor = rand.nextInt(8) + 1;
        phase = 5;
    }

    public ChaosballClient(int xSize, int ySize) {
        this.xSize = xSize;
        this.ySize = ySize;
        intro.loadImage("res/Court/LogoIndi.png");
        ballTexture.loadImage("res/Court/ballA.png");
        ballBTexture.loadImage("res/Court/ballB.png");
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

        MouseListener mouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if(keysEnabled) {
                    shotSound.rewindStart();
                    controlsHeld.posX = event.getPoint().x;
                    controlsHeld.posY = event.getPoint().y;
                    controlsHeld.btn = event.getButton();
                    controlsHeld.camX = camX;
                    controlsHeld.camY = camY;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if(keysEnabled) {
                    controlsHeld.btn = 0;
                }
            }
        };
        addMouseListener(mouseListener);
        addMouseMotionListener(mouseMvtListener);
        button.addMouseListener(mouseListener);
    }


    private void initSurface() {
        // Instantiation of sound classes
        shotSound = new Sound();
        // Load the sounds
        shotSound.loadSound(shotSoundFile);
        addKeyListener(this);
        setBackground(Color.WHITE);
        setFocusable(true); // recognizes obtaining focus from the keyboard
        timer.start();
    }

    StaticImage select = new StaticImage();
    StaticImage lobby = new StaticImage();
    StaticImage classCursor = new StaticImage();
    StaticImage field = new StaticImage();
    StaticImage selector = new StaticImage();
    StaticImage goalScored = new StaticImage();
    StaticImage victory = new StaticImage();
    StaticImage defeat = new StaticImage();
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


    public void lobby(Graphics g) {
        Graphics2D g2D = (Graphics2D) g;
        g2D.drawImage(lobby.getImage(), 1, 1, this);
        Font font = new Font("Verdana", Font.BOLD, 24);
        g2D.setColor(Color.YELLOW);
        g2D.setFont(font);
        g2D.drawString("Waiting for players...", 335, 220);
        font = new Font("Verdana", Font.BOLD, 48);
        g2D.setColor(Color.GREEN);
        g2D.setFont(font);
        g2D.drawString("READY", 435, 480);
    }



    public void updateFrameBall() {
        if(game.anyPoss() || game.anyBallMoveState()) {
            ballFrameCounter += 1;

            if (ballFrameCounter == 5) ballFrame = 1;
            if (ballFrameCounter == 10) {
                ballFrame = 0;
                ballFrameCounter = 0;
            }
        }
    }

    public void displayScore(Graphics g) {
        if (phase == 8) {
            Graphics2D g2d = (Graphics2D) g;
            Font font = new Font("Verdana", Font.BOLD, 45);
            g2d.setFont(font);
            String goalsHome = Double.toString(game.home.score);
            String goalsAway = Double.toString(game.away.score);
            g2d.setColor(new Color(0f, 0f, 1f, .5f));
            g2d.drawString(goalsHome, (int)(.094*xSize), (int)(.652*ySize));
            g2d.setColor(new Color(1f, 1f, 1f, .5f));
            g2d.drawString(goalsAway, (int)(.416*xSize), (int)(.652*ySize));
            g2d.setColor(Color.RED);
            int x = xSize/4;
            for (int i = 0; i < game.effectPool.getEffects().size(); i++) {
                Effect e = game.effectPool.getEffects().get(i);
                Entity on = game.effectPool.getOn().get(i);
                if (game.underControl.id.equals(on.id)) {
                    if (e.getIcon() != null) {
                        Composite originalComposite = g2d.getComposite();
                        g2d.setComposite(makeComposite(.5f));
                        g2d.drawImage(e.getIcon(), x, 689, this);
                        g2d.setComposite(originalComposite);
                        g2d.setColor(new Color(1f, 1f, 1f, .5f));
                        Arc2D.Double background = new Arc2D.Double(x, 657, 32, 32, 90, -360, Arc2D.PIE);
                        g2d.fill(background);
                        double percentBar = 100.0 - e.getPercentLeft();
                        if(percentBar > 100){
                            percentBar = 99.99999;
                        }
                        if(percentBar < 0){
                            percentBar = 0.000001;
                        }
                        setColorBasedOnPercent(g2d, percentBar, true);
                        double coverage = e.getPercentLeft() / 100.0 * 360.0;
                        Arc2D.Double percent = new Arc2D.Double(x + 2, 659, 28, 28, 90, coverage, Arc2D.PIE);
                        g2d.fill(percent);
                        x += 32;
                    }
                }
            }
        }
    }

    private AlphaComposite makeComposite(float alpha) {
        int type = AlphaComposite.SRC_OVER;
        return(AlphaComposite.getInstance(type, alpha));
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

    public void tutorial(Graphics g) {
        Graphics2D g2D = (Graphics2D) g;
        g2D.drawImage(tutorial.getImage(), 1, 1, this);
        Font font = new Font("Verdana", Font.PLAIN, 65);
        g2D.setFont(font);
        g2D.drawString("Space to proceed", 5, 705);
    }

    private void creditPanel(Graphics g) {
        Graphics2D g2D = (Graphics2D) g;
        g2D.drawImage(intro.getImage(), 0, 0, this);
        Font font = new Font("Verdana", Font.PLAIN, 65);
        g2D.setFont(font);
        g2D.drawString("Space to proceed", 370, 640);
    }

    private void setColorBasedOnPercent(Graphics2D g2d, double inputPercent, boolean translucent) {
        g2d.setColor(new Color(redColorFromPercent(inputPercent),
                greenColorFromPercent(inputPercent), .54f, translucent?.5f:1f));
    }

    private float redColorFromPercent(double inputPercent) {
        float rColorValue = (float) (.025f * (100f - inputPercent));
        if (rColorValue < 0f) {
            return 0f;
        }
        if (rColorValue > 1f) {
            return 1f;
        }
        return rColorValue;
    }

    private float greenColorFromPercent(double inputPercent) {
        if (inputPercent < 0) {
            inputPercent = 0.0;
        }
        return (float) (.01f * (inputPercent));
    }

    private void selectClasses(Graphics g) {
        Graphics2D g2D = (Graphics2D) g;
        g2D.drawImage(select.getImage(), 1, 1, this);
        if (staticFrame == 2) {
            staticFrame = 3;
        } else {
            staticFrame = 2;
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
        if (cursor > 10) {
            cursor%= 10;
        }
        if (cursor <= 0) {
            cursor= -1*(cursor);
            if(cursor == 0){
                cursor =1;
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
        /*if (cursor == 11) g2D.drawImage(classCursor.getImage(), 555, 435, this);
        if (cursor == 12) g2D.drawImage(classCursor.getImage(), 755, 435, this);
        */
    }
}
