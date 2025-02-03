To run with the launcher

```
java -jar Titanball-Launcher/getdown.jar Titanball-Launcher
```

IMPORTANT:
It is LIKELY that on Mac, your keylisteners will get stuck down occasionally. Symptoms of this include working mouse keybinds, but no working keyboard keybinds.
Run `defaults write -g ApplePressAndHoldEnabled -bool false` in a command-line to fix it permanently.

The client itself executes with the following code
```
defaults write -g ApplePressAndHoldEnabled -bool false
java -cp Titanball.jar client.TitanballWindow
```

Or use the bash script
`./start.sh` (While in the folder with the jar and res folder)

Server info: https://zanzalaz.com


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
