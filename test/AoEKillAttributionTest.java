import gameserver.effects.EffectId;
import gameserver.effects.effects.FlareEffect;
import gameserver.engine.GameEngine;
import gameserver.engine.StatEngine;
import gameserver.engine.TeamAffiliation;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.Bomb;
import gameserver.entity.minions.Fire;
import networking.PlayerDivider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AoEKillAttributionTest {

    private GameEngine engine;
    private Titan homeGrenadier;
    private Titan homeCaptain;
    private Titan awayTarget;
    private Titan homeWarrior;
    private PlayerDivider grenadierClient;
    private PlayerDivider captainClient;
    private PlayerDivider warriorClient;
    private PlayerDivider targetClient;

    @Before
    public void setup() {
        engine = new GameEngine();
        List<PlayerDivider> clients = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            PlayerDivider pd = new PlayerDivider(Arrays.asList(i));
            pd.id = i;
            pd.email = "player" + i + "@titan.test";
            clients.add(pd);
        }
        engine.clients = clients;
        engine.initializeServer();

        // players[2] is HOME field Titan
        homeGrenadier = engine.players[2];
        homeGrenadier.team = TeamAffiliation.HOME;
        homeGrenadier.setType(TitanType.GRENADIER);
        homeGrenadier.setVarsBasedOnType();
        grenadierClient = engine.clientFromTitan(homeGrenadier);
        grenadierClient.email = "grenadier@titan.test";

        // players[3] is HOME field Titan
        homeCaptain = engine.players[3];
        homeCaptain.team = TeamAffiliation.HOME;
        homeCaptain.setType(TitanType.CAPTAIN);
        homeCaptain.setVarsBasedOnType();
        captainClient = engine.clientFromTitan(homeCaptain);
        captainClient.email = "captain@titan.test";

        // players[4] is HOME field Titan
        homeWarrior = engine.players[4];
        homeWarrior.team = TeamAffiliation.HOME;
        homeWarrior.setType(TitanType.WARRIOR);
        homeWarrior.setVarsBasedOnType();
        warriorClient = engine.clientFromTitan(homeWarrior);
        warriorClient.email = "warrior@titan.test";

        // players[5] configured as AWAY field Titan
        awayTarget = engine.players[5];
        awayTarget.team = TeamAffiliation.AWAY;
        awayTarget.setType(TitanType.RANGER);
        awayTarget.setVarsBasedOnType();
        targetClient = engine.clientFromTitan(awayTarget);
        targetClient.email = "target@titan.test";

        // Ensure fresh stats
        engine.stats.reset();
    }

    @Test
    public void testGrenadierMolotovDirectKill() {
        awayTarget.setHealth(1);

        // Place Fire spawned by Grenadier
        Fire fire = new Fire(homeGrenadier, (int) awayTarget.X, (int) awayTarget.Y);
        engine.entityPool.add(fire);

        // Trigger collision with fire
        fire.triggerCollide(engine, awayTarget);

        // Tick effects: FlareEffect activates and deals damage, triggering die(), then DeadEffect activates
        engine.effectPool.tickAll(engine);
        engine.effectPool.tickAll(engine);

        // Target should be dead
        Assert.assertTrue("Target should have died from Fire", awayTarget.getHealth() <= 0 || engine.effectPool.hasEffect(awayTarget, EffectId.DEAD));

        // Direct kills column for Grenadier must be incremented
        double kills = engine.stats.getGamestats().get(StatEngine.StatEnum.KILLS.ordinal()).getOrDefault(grenadierClient.email, 0.0);
        Assert.assertEquals("Grenadier must receive direct kill credit for Molotov kill", 1.0, kills, 0.001);

        // Target must receive death credit
        double deaths = engine.stats.getGamestats().get(StatEngine.StatEnum.DEATHS.ordinal()).getOrDefault(targetClient.email, 0.0);
        Assert.assertEquals("Target must receive death credit", 1.0, deaths, 0.001);
    }

    @Test
    public void testFlareEffectLethalBurnTickKill() {
        awayTarget.setHealth(2);

        // Add FlareEffect directly attributed to Grenadier
        FlareEffect flare = new FlareEffect(3000, awayTarget, 0.0, 5.0, homeGrenadier);
        engine.effectPool.addUniqueEffect(homeGrenadier, flare, engine);

        // Tick effects: FlareEffect ticks recurring damage and kills target, next tick activates DeadEffect
        engine.effectPool.tickAll(engine);
        engine.effectPool.tickAll(engine);

        double kills = engine.stats.getGamestats().get(StatEngine.StatEnum.KILLS.ordinal()).getOrDefault(grenadierClient.email, 0.0);
        Assert.assertEquals("Grenadier must receive direct kill credit when FlareEffect ticks lethal damage", 1.0, kills, 0.001);
    }

    @Test
    public void testCaptainBombLethalExplosionKill() {
        // Position awayTarget at (500, 500)
        awayTarget.X = 500;
        awayTarget.Y = 500;
        awayTarget.setHealth(30); // 30 HP < 50 Bomb dmg -> lethal 1-shot

        // Captain drops bomb at same location
        Bomb bomb = new Bomb(homeCaptain, 500, 500, engine);
        engine.entityPool.add(bomb);

        // Trigger explosion
        bomb.ticksRemaining = 1;
        bomb.tick(engine); // triggers explosion, deals 50 dmg, triggers die()
        engine.effectPool.tickAll(engine); // activates DeadEffect -> grantKillAssists

        Assert.assertTrue("Target should have died from Bomb explosion", awayTarget.getHealth() <= 0 || engine.effectPool.hasEffect(awayTarget, EffectId.DEAD));

        double kills = engine.stats.getGamestats().get(StatEngine.StatEnum.KILLS.ordinal()).getOrDefault(captainClient.email, 0.0);
        Assert.assertEquals("Captain must receive kill credit for lethal Bomb explosion", 1.0, kills, 0.001);
    }

    @Test
    public void testKillAndAssistAttribution() throws InterruptedException {
        // Attacker 1 (Warrior) damages target
        awayTarget.damage(engine, 20.0, homeWarrior);

        // Sleep briefly to ensure time difference in effect percent
        Thread.sleep(20);

        // Attacker 2 (Grenadier) damages target and kills them
        awayTarget.damage(engine, 100.0, homeGrenadier);
        engine.effectPool.tickAll(engine); // activates DeadEffect -> grantKillAssists

        System.out.println("DEBUG kills map: " + engine.stats.getGamestats().get(StatEngine.StatEnum.KILLS.ordinal()));
        System.out.println("DEBUG assists map: " + engine.stats.getGamestats().get(StatEngine.StatEnum.KILLASSISTS.ordinal()));
        System.out.println("DEBUG grenadier email: " + grenadierClient.email);
        System.out.println("DEBUG warrior email: " + warriorClient.email);

        // Killer should be Grenadier
        double grenadierKills = engine.stats.getGamestats().get(StatEngine.StatEnum.KILLS.ordinal()).getOrDefault(grenadierClient.email, 0.0);
        Assert.assertEquals("Grenadier should receive the kill", 1.0, grenadierKills, 0.001);

        // Assister should be Warrior
        double warriorAssists = engine.stats.getGamestats().get(StatEngine.StatEnum.KILLASSISTS.ordinal()).getOrDefault(warriorClient.email, 0.0);
        Assert.assertEquals("Warrior should receive the kill assist", 1.0, warriorAssists, 0.001);

        // Grenadier should NOT receive an assist for their own kill
        double grenadierAssists = engine.stats.getGamestats().get(StatEngine.StatEnum.KILLASSISTS.ordinal()).getOrDefault(grenadierClient.email, 0.0);
        Assert.assertEquals("Grenadier should not receive their own assist", 0.0, grenadierAssists, 0.001);
    }

    @Test
    public void testEnvironmentalDeathWithinCombatWindow() {
        // Grenadier damages target with fire, leaving target low but alive
        awayTarget.damage(engine, 50.0, homeGrenadier);
        Assert.assertTrue("Target should be alive", awayTarget.getHealth() > 0);

        // Target then dies to environmental redzone / pain (no attacker passed)
        awayTarget.damage(engine, 100.0, null);
        engine.effectPool.tickAll(engine); // activates DeadEffect -> grantKillAssists

        // Target died within 5 seconds of Grenadier's attack: Grenadier should be credited with kill
        double kills = engine.stats.getGamestats().get(StatEngine.StatEnum.KILLS.ordinal()).getOrDefault(grenadierClient.email, 0.0);
        Assert.assertEquals("Grenadier should receive kill credit for environmental death within combat window", 1.0, kills, 0.001);
    }

    @Test
    public void testFriendlyFireDoesNotAwardKill() {
        // HOME Warrior damages HOME Grenadier
        homeGrenadier.damage(engine, 200.0, homeWarrior);
        engine.effectPool.tickAll(engine);

        double warriorKills = engine.stats.getGamestats().get(StatEngine.StatEnum.KILLS.ordinal()).getOrDefault(warriorClient.email, 0.0);
        Assert.assertEquals("Friendly fire should not award kill to teammate", 0.0, warriorKills, 0.001);
    }
}
