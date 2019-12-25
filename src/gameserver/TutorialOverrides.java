package gameserver;

import gameserver.engine.GameOptions;
import gameserver.engine.StatEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.*;
import networking.ClientPacket;
import networking.PlayerDivider;

import java.util.*;

public class TutorialOverrides extends GameEngine{

     Titan hGol = new Titan(HOME_HI_X, HOME_HI_Y, TeamAffiliation.HOME, TitanType.GOALIE);
     Titan awGol = new Titan(AWAY_HI_X, AWAY_HI_Y, TeamAffiliation.AWAY, TitanType.GOALIE);

     public Titan[] playersRebound = {
            hGol,
            awGol,
            new Titan(MID_HOME, MID_WING_HOME, TeamAffiliation.HOME, TitanType.RANGER)
    };

     public Titan[] playersKill = {
            hGol,
            awGol,
            new Titan(MID_HOME, MID_WING_HOME, TeamAffiliation.HOME, TitanType.RANGER),
            new Titan(FIELD_LENGTH - MID_HOME, MID_WING_HOME, TeamAffiliation.AWAY, TitanType.GOLEM)
    };

     public Titan[] playersKick = {
            hGol,
            awGol,
            new Titan(MID_HOME, MID_WING_HOME, TeamAffiliation.HOME, TitanType.RANGER),
            new Titan(FIELD_LENGTH - MID_HOME, MID_WING_HOME, TeamAffiliation.AWAY, TitanType.GOLEM),
    };

     public Titan[] playersSteal = {
            hGol,
            awGol,
            new Titan(MID_HOME, MID_WING_HOME, TeamAffiliation.HOME, TitanType.RANGER),
            new Titan(FIELD_LENGTH - MID_HOME, MID_WING_HOME, TeamAffiliation.AWAY, TitanType.GOLEM),
    };

     public Titan[] playersComboGoal = {
            hGol,
            awGol,
            new Titan(FIELD_LENGTH - DEFENDER_HOME, MID_WING_HOME, TeamAffiliation.HOME, TitanType.MARKSMAN),
    };

     Box ballDefault = new Box((int)BALL_X, (int)BALL_Y, 15, 15);
     Box ballSteal = new Box(FIELD_LENGTH - MID_HOME, MID_WING_HOME, 15, 15);
     Box ballScore = new Box(FIELD_LENGTH - DEFENDER_HOME - 50, MID_WING_HOME, 15, 15);

     public Map<String, Titan[]> tutMap = new HashMap<>();
     public Map<String, Box> ballMap = new HashMap<>();

    public PlayerDivider client;
    private PlayerDivider cpuClient;

    public int tutorialPhase = 1;
    public int narrationPhase = 0;

    public TutorialOverrides(){
        super(UUID.randomUUID().toString(), Collections.emptyList(), new GameOptions(new GameOptions().toString()));
        tutMap.put("rebound", playersRebound);
        tutMap.put("steal", playersSteal);
        tutMap.put("abilities", playersKick);
        tutMap.put("kill", playersKill);
        tutMap.put("score", playersComboGoal);
        ballMap.put("rebound", ballDefault);
        ballMap.put("steal", ballSteal);
        ballMap.put("score", ballScore);
        players = tutMap.get("rebound");
        client = clientFromTitan(players[2]);
        players[2].setVarsBasedOnType();
    }

    public void tutReset() {
        tutorialPhase++;
        this.underControl = players[2];
        this.underControl.possession = 0;
        this.underControl.setVarsBasedOnType();
        this.underControl.setHealth(40);
        if(players.length > 3){
            cpuClient = clientFromTitan(players[3]);
            effectPool.cullAllOn(this, players[3]);
            players[3].possession = 0;
        }

        this.lastControlPacket[0] = new ClientPacket();

        stats.reset();
        client = clientFromTitan(this.underControl);
    }

    public void detectAndUpdateState() {
        this.began = true;
        client = new PlayerDivider(Collections.singletonList(3));
        client.setEmail("localhost");
        switch(tutorialPhase){
            case 1:
                if(stats.statConditionalMet(client, StatEngine.StatEnum.REBOUND, 1)){
                    players[2].possession = 0;
                    players = tutMap.get("steal");
                    ball.setX(ballMap.get("steal").X);
                    ball.setY(ballMap.get("steal").Y);
                    tutReset();
                }
                break;
            case 2:
                if(stats.statConditionalMet(client, StatEngine.StatEnum.STEALS, 1)){
                    tutorialPhase++;
                    players[2].X = MID_HOME;
                    players[2].possession = 0;
                    players[3].possession = 1;
                    players[3].setVarsBasedOnType();
                    players[3].setHealth(13);
                }
                break;
            case 3:
                if(stats.statConditionalMet(client, StatEngine.StatEnum.KILLS, 1)){
                    this.underControl.possession = 0;
                    players[2].possession = 0;
                    this.players[2].setType(TitanType.MARKSMAN);
                    this.players[2].setVarsBasedOnType();
                    players = tutMap.get("score");
                    ball.setX(ballMap.get("score").X);
                    ball.setY(ballMap.get("score").Y);
                    tutReset();
                    //spawn a wall here to prevent rebound, then kick them free
                }
                break;
            case 4:
                this.players[2].setType(TitanType.MARKSMAN);
                this.players[2].setVarsBasedOnType();
                this.underControl.setType(TitanType.MARKSMAN);
                this.underControl.setVarsBasedOnType();
                if(stats.statConditionalMet(client, StatEngine.StatEnum.SIDEGOALS, 1)){
                    players[2].setX(FIELD_LENGTH - DEFENDER_HOME);
                    players[2].setY(MID_WING_HOME);
                    this.players[0].possession = 0;
                    this.players[1].possession = 0;
                    this.players[2].possession = 0;
                    this.players[2].actionFrame = 0;
                    this.players[2].actionState = Titan.TitanState.IDLE;
                    this.lastPossessed = null;
                    this.underControl.possession = 0;
                    ball.setX(ballMap.get("score").X);
                    ball.setY(ballMap.get("score").Y);
                    tutReset();
                }
                break;
            case 5:
                this.players[2].setType(TitanType.MARKSMAN);
                this.players[2].setVarsBasedOnType();
                this.underControl.setType(TitanType.MARKSMAN);
                this.underControl.setVarsBasedOnType();
                if(stats.statConditionalMet(client, StatEngine.StatEnum.GOALS, 1)){
                    phase = 2;
                    /*players = tutMap.get("score");
                    ball.setX(ballMap.get("score").X);
                    ball.setY(ballMap.get("score").Y);
                    */
                    //TODO
                }
                break;
            default:
        }
    }

    @Override
    public void gameTick() throws Exception {
        //System.out.println("tock " + began + ended);
        lock();
        if (began && !ended) {
            try {
                detectAndUpdateState();
                List<Entity> tempSolids = new ArrayList<>();
                tempSolids.addAll(Arrays.asList(this.players));
                trimEntities(this.entityPool);
                tempSolids.addAll(this.entityPool);
                allSolids = tempSolids.toArray(new Entity[tempSolids.size()]);
                updateBallIfPossessed();
                effectPool.tickAll(this);
                //TODO consider this off for tutorial?
                //doHealthModification();
                updateSelectedDirection();
                cullOldColliders();
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (Titan t : players) {
                if (t.isBoosting) {
                    t.fuel -= .5;
                    if (t.fuel < 0) {
                        t.fuel = 0;
                    }
                } else {
                    if (t.fuel > 25.0) {
                        t.fuel += .35;//regen bonus
                    } else {
                        t.fuel += .25;
                    }
                    if (t.fuel > 100.0) {
                        t.fuel = 100.0;
                    }
                }
                if (t.runRight == 1) runRightCtrl(t);
                if (t.runLeft == 1) runLeftCtrl(t);
                if (t.runUp == 1) runUpCtrl(t);
                if (t.runDown == 1) runDownCtrl(t);
                if (t.actionState == Titan.TitanState.SHOOT) shootingBall(t);
                else if (t.actionState == Titan.TitanState.PASS) lobbingBall(t);
                else if (t.actionState == Titan.TitanState.CURVE_LEFT) curve(t, 1);
                else if (t.actionState == Titan.TitanState.CURVE_RIGHT) curve(t, -1);
                if (t.actionState == Titan.TitanState.A1) attack1(t);
                if (t.actionState == Titan.TitanState.A2) attack2(t);
                if (t.actionState == Titan.TitanState.STEAL) steal(t);
            }
            yourPlayerTactics();
        }
        if (ballVisible) {
            intersectAll();
            detectGoals();
        }
        if (ball.X < GameEngine.MIN_X) ball.X = GameEngine.MIN_X;
        if (ball.X > GameEngine.MAX_X) ball.X = GameEngine.MAX_X;
        if (ball.Y < GameEngine.MIN_Y) ball.Y = GameEngine.MIN_Y;
        if (ball.Y > GameEngine.MAX_Y) ball.Y = GameEngine.MAX_Y;
        unlock();
    }
}
