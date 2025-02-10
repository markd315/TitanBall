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

