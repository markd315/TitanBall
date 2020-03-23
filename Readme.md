To run with the launcher

```
java -jar Titanball-Launcher/getdown.jar Titanball-Launcher
```

IMPORTANT:
It is LIKELY that on Mac, your keylisteners will get stuck down occasionally. Symptoms of this include working mouse keybinds, but no working keyboard keybinds.
Run `defaults write -g ApplePressAndHoldEnabled -bool false` in a command-line to fix it permanently.


It is POSSIBLE that you will not be able to play the game without modifying your firewall.
Open an ADMINISTRATOR command prompt, and run these commands. You should only need to do it once.
Be sure to replace the last part of the command with the path to your own Java file.

If you want to test whether you will be affected by this issue, go into tournament settings and run a single player game.
If the game start counter goes deep into negative numbers with no change, your firewall configuration is blocking my server.

Also turn on network discovery this way!
Control Panel\Network and Internet\Network and Sharing Center

INET6 / WindowsSelectorProvider
localport
/10.0.0.96:50116

remote port
zanzalaz.com/18.189.118.238:54556

```
Netsh.exe advfirewall firewall add rule name="Titanball packets from server 1" protocol=udp dir=in enable=yes action=allow program="C:\Users\markd\Desktop\getdown\Titanball-Launcher\Titanball.jar"
Netsh.exe advfirewall firewall add rule name="Titanball packets from server 2" protocol=udp dir=in enable=yes action=allow localport=49152-65535 program="java.exe"
Netsh.exe advfirewall firewall add rule name="Titanball packets from server 3" protocol=udp dir=in enable=yes action=allow localport=49152-65535
```

we may need udp OUT as well as in!

(probably not this)
It seems that behind the scenes, my PC is using the Bonjour service for network discovery to allow UDP packets through the firewall.
May want to have clients try this.
https://download.info.apple.com/Mac_OS_X/061-8098.20100603.gthyu/BonjourPSSetup.exe
localport=5353 (not according to wireshark though!)

The client itself executes with the following code
```
defaults write -g ApplePressAndHoldEnabled -bool false
java -cp Titanball.jar client.TitanballWindow
```

Or use the bash script
`./start.sh` (While in the folder with the jar and res folder)

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