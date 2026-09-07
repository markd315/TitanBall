package gameserver.gamemanager;

import authserver.users.PersistenceManager;
import gameserver.engine.GameEngine;
import gameserver.engine.GameOptions;
import gameserver.engine.Masteries;
import gameserver.entity.Titan;
import networking.PlayerDivider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HeadlessGameManager implements ApplicationRunner {

    @Autowired
    private PersistenceManager persistenceManager;

    private final AtomicInteger completedGames = new AtomicInteger(0);
    private final AtomicInteger activeGames = new AtomicInteger(0);
    private final Set<String> runningGameIds = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;
    private int targetConcurrent = 100;
    private int tickIntervalMs = 8;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean actuated = false;
        int count = 100;

        gameserver.Const cfg = new gameserver.Const("res/game.cfg");
        if (cfg.HEADLESS_ENABLED) {
            actuated = true;
            count = cfg.HEADLESS_CONCURRENCY;
        }
        this.tickIntervalMs = cfg.HEADLESS_GAMETICK_MS > 0 ? cfg.HEADLESS_GAMETICK_MS : Math.max(1, cfg.GAMETICK_MS / 3);

        // 1. Check CLI options: --headless-games or --headless-games=N
        if (args.containsOption("headless-games")) {
            actuated = true;
            List<String> values = args.getOptionValues("headless-games");
            if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).isEmpty()) {
                try {
                    count = Integer.parseInt(values.get(0));
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. Check non-option args e.g. --headless-games 15
        List<String> nonOption = args.getNonOptionArgs();
        for (int i = 0; i < nonOption.size(); i++) {
            String arg = nonOption.get(i);
            if ("--headless-games".equals(arg)) {
                actuated = true;
                if (i + 1 < nonOption.size()) {
                    try {
                        count = Integer.parseInt(nonOption.get(i + 1));
                    } catch (NumberFormatException ignored) {}
                }
            } else if (arg.startsWith("--headless-games=")) {
                actuated = true;
                try {
                    count = Integer.parseInt(arg.substring("--headless-games=".length()));
                } catch (NumberFormatException ignored) {}
            }
        }

        // 3. Check environment variable HEADLESS_GAMES
        String envCount = System.getenv("HEADLESS_GAMES");
        if (envCount != null && !envCount.trim().isEmpty()) {
            try {
                count = Integer.parseInt(envCount.trim());
                actuated = true;
            } catch (NumberFormatException ignored) {}
        }

        // 4. Check system property -Dheadless-games=N
        String sysProp = System.getProperty("headless-games");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            actuated = true;
            try {
                count = Integer.parseInt(sysProp.trim());
            } catch (NumberFormatException ignored) {}
        }

        if (!actuated) {
            return;
        }

        this.targetConcurrent = Math.max(1, count);
        this.running = true;

        System.out.println("================================================================================");
        System.out.println(" [HeadlessGameManager] Starting " + this.targetConcurrent + " concurrent 4v4 balance test games");
        System.out.println(" [HeadlessGameManager] Tick Interval: " + this.tickIntervalMs + "ms (~" + (1000 / Math.max(1, this.tickIntervalMs)) + " TPS)");
        System.out.println(" [HeadlessGameManager] AI Reaction Speed: HARD (200-700ms), Roster: Configured AI Titans (Excludes Grenadier & Mage)");
        System.out.println(" [HeadlessGameManager] Results will be persisted continuously to `classstat` table.");
        System.out.println("================================================================================");

        for (int i = 0; i < this.targetConcurrent; i++) {
            spawnHeadlessGame();
        }
    }

    private synchronized void spawnHeadlessGame() {
        if (!running) return;

        String gameId = "headless-" + UUID.randomUUID();
        runningGameIds.add(gameId);
        activeGames.incrementAndGet();

        // 4v4 match (playerIndex = 1), Goalies on (goalieIndex = 0), Best of 1 (bestOfIndex = 1),
        // First to 10 points (playToIndex = 10), Win by 2 (winByIndex = 2), Hard win off (hardWinIndex = 9999),
        // Sudden death 10 min (suddenDeathIndex = 10), Draw at 20 min (tieIndex = 20),
        // Hard AI reaction speed 200-700ms (aiDifficultyIndex = 2), Hybrid slot reservation (isHybrid = 1)
        GameOptions op = new GameOptions("/1/0/1/10/2/9999/10/20/2/1");

        // 4v4 slots: 1 (Home Goalie), 2 (Away Goalie), 3 (Home Field 1), 4 (Home Field 2), 5 (Home Field 3),
        //            11 (Away Field 1), 12 (Away Field 2), 13 (Away Field 3)
        int[] initialSlots = new int[]{1, 2, 3, 4, 5, 11, 12, 13};
        List<PlayerDivider> clients = new ArrayList<>();
        for (int slot : initialSlots) {
            PlayerDivider pd = new PlayerDivider(Collections.singletonList(slot));
            pd.email = "bot-slot-" + slot + "@headless";
            pd.isAi = true;
            clients.add(pd);
        }

        GameEngine engine = new GameEngine(gameId, clients, op);
        engine.onGameEnded = this::handleGameCompleted;

        engine.initializeServer();

        // Initiate 10 titan masteries:
        // - Goalies: speed=3, boost=3, health=3, +1 random in another stat
        // - Field titans: 10 random masteries allocated in a 3/3/3/1 split across the stats
        Random rng = new Random();
        for (int i = 0; i < engine.players.length; i++) {
            Titan t = engine.players[i];
            if (t == null) continue;
            Masteries m;
            if (i == 0 || i == 1 || t.getType() == gameserver.entity.TitanType.GOALIE) {
                m = Masteries.createGoalieMasteries(rng);
            } else {
                m = Masteries.createRandom3331(rng);
            }
            m.applyMasteries(t);
        }

        engine.secondsToStart = 0;
        engine.phase = GamePhase.INGAME;
        engine.customTickIntervalMs = this.tickIntervalMs;
        engine.kickoff();
    }

    private void handleGameCompleted(GameEngine endedGame) {
        if (endedGame == null) return;

        String gId = endedGame.gameId;
        if (gId != null && runningGameIds.remove(gId)) {
            activeGames.decrementAndGet();
        }

        // Record stats for every titan in the match
        try {
            for (PlayerDivider player : endedGame.clients) {
                Titan t = endedGame.titanSelected(player);
                if (t != null && t.getType() != null) {
                    String className = t.getType().toString();
                    persistenceManager.recordClassStats(endedGame.stats, player.email, className, player.wasVictorious);
                }
            }
        } catch (Exception e) {
            System.err.println("[HeadlessGameManager] Error recording game stats: " + e.getMessage());
        }

        // Record masteries stats for +3 mastery groups (ignoring +1 stat)
        try {
            for (PlayerDivider player : endedGame.clients) {
                Titan t = endedGame.titanSelected(player);
                if (t != null && t.getType() != null && t.masteries != null) {
                    String className = t.getType().toString();
                    List<String> plusThrees = t.masteries.getPlusThreeMasteryNames();
                    for (String masteryName : plusThrees) {
                        String pairName = className + "_" + masteryName;
                        persistenceManager.recordMasteryStats(endedGame.stats, player.email, pairName, player.wasVictorious);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[HeadlessGameManager] Error recording mastery stats: " + e.getMessage());
        }

        // Record upgrade class stats for goalie purchases
        try {
            PlayerDivider homeGoaliePlayer = null;
            PlayerDivider awayGoaliePlayer = null;
            for (PlayerDivider player : endedGame.clients) {
                if (player.selection == 1) {
                    homeGoaliePlayer = player;
                } else if (player.selection == 2) {
                    awayGoaliePlayer = player;
                }
            }

            if (homeGoaliePlayer != null && endedGame.homeGoalieAllPurchasedUpgrades != null) {
                for (String upgradeKey : endedGame.homeGoalieAllPurchasedUpgrades) {
                    persistenceManager.recordUpgradeStats(endedGame.stats, homeGoaliePlayer.email, upgradeKey, homeGoaliePlayer.wasVictorious);
                }
            }
            if (awayGoaliePlayer != null && endedGame.awayGoalieAllPurchasedUpgrades != null) {
                for (String upgradeKey : endedGame.awayGoalieAllPurchasedUpgrades) {
                    persistenceManager.recordUpgradeStats(endedGame.stats, awayGoaliePlayer.email, upgradeKey, awayGoaliePlayer.wasVictorious);
                }
            }
        } catch (Exception e) {
            System.err.println("[HeadlessGameManager] Error recording upgrade stats: " + e.getMessage());
        }

        // Record build order stats for headless txt build orders
        try {
            PlayerDivider homeGoaliePlayer = null;
            PlayerDivider awayGoaliePlayer = null;
            for (PlayerDivider player : endedGame.clients) {
                if (player.selection == 1) {
                    homeGoaliePlayer = player;
                } else if (player.selection == 2) {
                    awayGoaliePlayer = player;
                }
            }

            if (homeGoaliePlayer != null && endedGame.players.length > 0) {
                Titan homeGoalie = endedGame.players[0];
                if (homeGoalie != null && homeGoalie.aiGoalieBuildName != null) {
                    persistenceManager.recordBuildOrderStats(endedGame.stats, homeGoaliePlayer.email, homeGoalie.aiGoalieBuildName, homeGoaliePlayer.wasVictorious);
                }
            }
            if (awayGoaliePlayer != null && endedGame.players.length > 1) {
                Titan awayGoalie = endedGame.players[1];
                if (awayGoalie != null && awayGoalie.aiGoalieBuildName != null) {
                    persistenceManager.recordBuildOrderStats(endedGame.stats, awayGoaliePlayer.email, awayGoalie.aiGoalieBuildName, awayGoaliePlayer.wasVictorious);
                }
            }
        } catch (Exception e) {
            System.err.println("[HeadlessGameManager] Error recording build order stats: " + e.getMessage());
        }

        int totalDone = completedGames.incrementAndGet();
        double homeScore = endedGame.home != null ? endedGame.home.score : 0.0;
        double awayScore = endedGame.away != null ? endedGame.away.score : 0.0;
        String outcome = homeScore > awayScore ? "HOME WIN" : (awayScore > homeScore ? "AWAY WIN" : "DRAW");

        System.out.printf("[HeadlessGameManager] Match #%d finished [%s %.1f - %.1f]. Active matches: %d. Class stats updated.%n",
                totalDone, outcome, homeScore, awayScore, activeGames.get());

        // Maintain constant pool of concurrent games
        if (running && activeGames.get() < targetConcurrent) {
            spawnHeadlessGame();
        }
    }

    public void stop() {
        this.running = false;
    }

    public int getCompletedGamesCount() {
        return completedGames.get();
    }

    public int getActiveGamesCount() {
        return activeGames.get();
    }
}
