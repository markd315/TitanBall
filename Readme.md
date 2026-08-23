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

Adam Bolt (Angbad) for 16x16 effect sprites

mage sprites from https://opengameart.org/content/sorlo-ultimate-smash-friends

builder from https://www.deviantart.com/agentmidnight/art/Engy-Man-Sprites-and-Hats-190830428

marksman, support, grenadier from AgentMidnight on DeviantArt

Warrior by FireMinstrel on NewGrounds

Ranger and Goalie by Warren Clark on https://lionheart963.itch.io/archer-character-sprite
https://lionheart963.itch.io/flying-eye-creature

Post/tank from https://opengameart.org/content/lpc-golem

Slasher, Artisan, Houndmaster sprites generated with http://gaurav.munjal.us/Universal-LPC-Spritesheet-Character-Generator

Stephen "Redstrike" Challener and William Thomsonj for the wolf sprite https://opengameart.org/content/lpc-wolf-animation

Molotov sprite from cgman at http://spritefx.blogspot.com/2013/04/fire-sprites.html

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

Authenticate with Amazon ECR, build the server Docker image, tag it, and push it to ECR:

```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 720291373173.dkr.ecr.us-east-1.amazonaws.com
docker build -t titanball .
docker tag titanball 720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball
docker push 720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball
```

### 3. CloudFormation Pilot Light Stack (Optional / Infrastructure)

To deploy or update the server infrastructure stack:

```bash
aws cloudformation deploy \
  --template-file operations/pilot-light-stack.yaml \
  --stack-name titanball-pilot-light \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    DeploymentType=ECS \
    CloudFrontDistributionId=E250EEB1SQKL1Z \
    ECRImageUri=720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball:latest \
    DatabaseRootPassword=yoursecurepassword
```

For more detailed pilot-light architecture information, see [PILOT_LIGHT_GUIDE.md](file:///operations/PILOT_LIGHT_GUIDE.md).


