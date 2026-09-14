To run with the launcher


Requires Java 17 or higher to run the client. JavaFX is bundled with the client shaded jar.

[OpenJDK 17](https://www.openlogic.com/openjdk-downloads?field_java_parent_version_target_id=807&field_operating_system_target_id=All&field_architecture_target_id=All&field_java_package_target_id=401) is recommended.

```
java -jar Titanball-Launcher/getdown.jar Titanball-Launcher
```

The client itself executes with the following code
```
java -jar Titanball.jar
```

Or use the bash script
`./start.sh` (While in the folder with the jar and res folder)

Server info: https://zanzalaz.com

Proudly made with JavaFX

Made with love by Mark Davis (contact: markd315@gmail.com), anything not specified below was developed in-house.

Big thanks to:
Ball downsized from https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Basketball_Clipart.svg/1035px-Basketball_Clipart.svg.png

Ranked medals from http://pixeljoint.com/pixelart/26524.htm

I made the cage for the wolves myself, apparently it was too specific.

DEBUGGING:
It is POSSIBLE that on Mac, your keylisteners will still get stuck down occasionally. Symptoms of this include working mouse keybinds, but no working keyboard keybinds.
Run `defaults write -g ApplePressAndHoldEnabled -bool false` in a command-line to fix it permanently.

## Local Development (Web Client & Server)

From the repo root, start the backend stack:

```bash
docker-compose up --build -d
```

In a separate terminal, start the Vite dev server:

```bash
cd client-web
npm run build   # catches compile-time issues
npm run dev
```

Open http://localhost:5173. The dev server proxies `/api` and `/game` (WebSocket) to the Docker server on port 3030.

Server logs (`docker-compose logs -f server`) and browser console (`[DIAG]` prefix) include titan type / class-selection diagnostics.

---

## Balance Tables & Simulation Metrics (Docker Database)

Headless balance simulations periodically record winrates and gameplay metrics directly into MySQL running inside Docker (`titanball-db-1`).

### Quick Docker Query Commands

Run these commands directly in your terminal or via agent commands:

#### 1. Class Stat Balance & Win Rates (`classstat`)
```bash
docker exec titanball-db-1 mysql -uroot -pdevpassword titanball -e "
SELECT role, wins, losses, ties, (wins + losses + ties) AS total_games,
       ROUND(wins / (wins + losses) * 100, 2) AS winrate_pct,
       ROUND(points / (wins + losses), 2) AS ppg,
       ROUND(goals / (wins + losses), 2) AS gpg,
       ROUND(kills / (wins + losses), 2) AS kpg,
       ROUND(deaths / (wins + losses), 2) AS dpg
FROM classstat
WHERE (wins + losses) > 0
ORDER BY winrate_pct DESC;"
```

#### 2. Mastery Pairs Balance & Win Rates (`masteriesstat`)
```bash
docker exec titanball-db-1 mysql -uroot -pdevpassword titanball -e "
SELECT role, wins, losses, (wins + losses) AS total_games,
       ROUND(wins / (wins + losses) * 100, 2) AS winrate_pct,
       ROUND(points / (wins + losses), 2) AS ppg,
       ROUND(goals / (wins + losses), 2) AS gpg,
       ROUND(kills / (wins + losses), 2) AS kpg,
       ROUND(deaths / (wins + losses), 2) AS dpg
FROM masteriesstat
WHERE (wins + losses) > 0
ORDER BY winrate_pct DESC;"
```

#### 3. Goalie Upgrade Tree Balance & Win Rates (`upgradeclassstat`)
```bash
docker exec titanball-db-1 mysql -uroot -pdevpassword titanball -e "
SELECT upgrade, wins, losses, (wins + losses) AS total_games,
       ROUND(wins / (wins + losses) * 100, 2) AS winrate_pct,
       ROUND(saves / (wins + losses), 2) AS saves_pg,
       ROUND(goalsconceded / (wins + losses), 2) AS conc_pg,
       ROUND(upgradesgold / (wins + losses), 2) AS gold_pg
FROM upgradeclassstat
WHERE (wins + losses) > 0
ORDER BY winrate_pct DESC;"
```

#### 4. Headless Build Order Win Rates (`buildorderstat`)
```bash
docker exec titanball-db-1 mysql -uroot -pdevpassword titanball -e "
SELECT buildname, wins, losses, (wins + losses) AS total_games,
       ROUND(wins / (wins + losses) * 100, 2) AS winrate_pct
FROM buildorderstat
WHERE (wins + losses) > 0
ORDER BY winrate_pct DESC;"
```

### How to Respond to Balance Queries (For AI Assistants & Developers)
When prompted with queries like *"check classstat, masteries, and upgrade balance tables"*:
1. **Query Live Container**: Run `docker exec` against `titanball-db-1` on the `titanball` database (`-uroot -pdevpassword`) for `classstat`, `masteriesstat`, and `upgradeclassstat`.
2. **Present Clean Tables**:
   - **Class Stats Table**: Ranked by `winrate_pct` with matches played (`total_games`), points per game (`ppg`), goals per game (`gpg`), and K/D ratios. Highlight any classes with sample size > 1,000 above 52% (overpowered/dominant) or below 48% (underpowered).
   - **Masteries**: Provide the top 10 highest winrate traits and bottom 10 lowest winrate traits, pointing out stat synergies (e.g. `GOLEM_BOOST`, `STEALTH_SHOT`) vs traps (e.g. `CAPTAIN_COOLDOWNS`, `SPIDER_DAMAGE`).
   - **Goalie Upgrades**: Group or sort by winrate and tree branch (`cultivation`, `fortress`, `siege`, `empowerment`), highlighting meta-defining upgrades (e.g. `siege.t6.forwardmedics`, `fortress.t5.icebarrage`) vs underperforming investments (e.g. `empowerment.t6.apexform`).
3. **Synthesis & Balance Takeaways**: Provide a bulleted summary of key balance insights, outlier trends, and potential tuning levers in `res/game.cfg`.

---

## Deployment

### 1. Frontend Deployment (S3 & CloudFront)

Build the web client, copy `home.html`, sync to S3 (`s3://public-720291373173-prod/pages/titanball/`), and invalidate the CloudFront distribution cache:

**Linux / macOS / Git Bash:**
```bash
./operations/deploy-frontend.sh
```

**PowerShell (Windows):**
```powershell
./operations/deploy-frontend.ps1
```

Once deployed, the frontend is live at `https://blockforger.net/pages/titanball/home.html`.

### 2. Backend Server Deployment (Docker & Amazon ECR)

Ensure headless games are turned off in `res/game.cfg`, authenticate with Amazon ECR, build the server Docker image, tag it, and push it to ECR:

```bash
./operations/deploy-backend.sh
```

Or execute manually:

```bash
sed -i.bak 's/^headless\.enabled=.*/headless.enabled=false/' res/game.cfg && rm -f res/game.cfg.bak
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 720291373173.dkr.ecr.us-east-1.amazonaws.com
docker build -t titanball .
docker tag titanball 720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball
docker push 720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball
```

### 3. CloudFormation Pilot Light Stack (Optional / Infrastructure)

To deploy or update the server infrastructure stack (the stack automatically resolves and uses the latest ECR image available):

```bash
aws cloudformation deploy \
  --template-file operations/pilot-light-stack.yaml \
  --stack-name titanball-pilot-light \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    DeploymentNonce=$(date +%s) \
    CloudFrontDistributionId=E250EEB1SQKL1Z \
    DatabaseRootPassword=yoursecurepassword
```

For more detailed pilot-light architecture information, see [PILOT_LIGHT_GUIDE.md](file:///operations/PILOT_LIGHT_GUIDE.md).

---

## Tournament Strings & Match Options

TitanBall matches are configured using slash-delimited tournament codes (e.g. `"/1/0/1/10/2/9999/10/20/3/1"`), parsed into `GameOptions`:

| Token Index | Field | Type | Description |
| :--- | :--- | :--- | :--- |
| `split[1]` | `playerIndex` | Array Index | Team sizing (`0` = 3v3, `1` = 4v4, `4` = 1v1). |
| `split[2]` | `goalieIndex` | Array Index | Goalie configuration (`0` = Goalies on, `1` = Goalies off, `2` = Permanent goalies). |
| `split[3]` | `bestOfIndex` | Array Index | Match series (`1` = Best of 1). |
| `split[4]` | `playToIndex` | **Literal Value** | Soft target score needed to win (e.g. `10` = first to 10). Must not be 0. |
| `split[5]` | `winByIndex` | **Literal Value** | Win margin required (e.g. `2` = win by 2). |
| `split[6]` | `hardWinIndex` | **Literal Value** | Blowout / mercy score cutoff (`9999` = disabled). |
| `split[7]` | `suddenDeathIndex` | **Literal Minutes** | Minutes elapsed until sudden death triggers (e.g. `10` = 10 minutes). Must not be 0. |
| `split[8]` | `tieIndex` | **Literal Minutes** | Minutes elapsed until draw is declared (e.g. `20` = 20 minutes). |
| `split[9]` | `aiDifficultyIndex` | Array Index | Optional. AI reaction time (`0` = Beginner 2000–5000ms [unrated], `1` = Easy 1200–1700ms [900 ELO], `2` = Medium 500–1200ms [1000 ELO], `3` = Hard 200–700ms [1100 ELO], `4` = Expert 0–200ms [1200 ELO], `5` = Perfect 0ms [unrated]). |
| `split[10]` | `isHybrid` | Boolean (`0`/`1`) | Optional. Enables bot slot reservation and AI tactics. |

**Key Gotcha**: While `playerIndex`, `goalieIndex`, and `bestOfIndex` index into option arrays, `playToIndex`, `winByIndex`, `hardWinIndex`, `suddenDeathIndex`, and `tieIndex` are parsed directly as **literal numbers**. Passing zeroes for these thresholds triggers immediate win conditions and premature game termination.
