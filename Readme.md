To run with the launcher

```
java -jar Titanball-Launcher/getdown.jar Titanball-Launcher
```

IMPORTANT:
On windows, it is LIKELY that you will not be able to play the game without modifying your firewall.
Open an ADMINISTRATOR command prompt, and run this command. You should only need to do it once.
Be sure to replace the last part of the command with the path to your own Java file.

If you want to test whether you will be affected by this issue, go into tournament settings and run a single player game.
If the game start counter goes deep into negative numbers with no change, your firewall configuration is blocking my server.

```
Netsh.exe advfirewall firewall add rule name="Titanball packets from server" protocol=udp dir=in enable=yes action=allow program="C:\Users\markd\Desktop\getdown\Titanball-Launcher\Titanball.jar"
```

The client itself executes with the following code
```
java -cp Titanball.jar client.TitanballWindow
```

Or use the bash script
```
./start.sh (While in the folder with the jar and res folder)
```

Made with love by Mark Davis (contact: markd315@gmail.com), anything not specified below was developed in-house.

Big thanks to:

Adam Bolt (Angbad) for 16x16 effect sprites

mage sprites from https://opengameart.org/content/sorlo-ultimate-smash-friends

builder from https://www.deviantart.com/agentmidnight/art/Engy-Man-Sprites-and-Hats-190830428

marksman, support, demoman (not used) from AgentMidnight on DeviantArt

Warrior by FireMinstrel on NewGrounds

Ranger and Goalie by Warren Clark on https://lionheart963.itch.io/archer-character-sprite
https://lionheart963.itch.io/flying-eye-creature

Post/tank from https://opengameart.org/content/lpc-golem

Slasher and Artisan sprites generated with http://gaurav.munjal.us/Universal-LPC-Spritesheet-Character-Generator

Ball downsized from https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Basketball_Clipart.svg/1035px-Basketball_Clipart.svg.png

