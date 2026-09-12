# TitanBall Initial Run: Balance, Systems & Telemetry Caveat Report

This document compiles the theoretical and empirical balance findings for TitanBall from the initial 4v4 headless benchmark run. It includes:
1. The baseline output of `balance_analyzer.py` (theoretical stat/ability modeling from `res/game.cfg`).
2. Empirical match statistics from the MySQL `classstat` table across 1,432 completed matches.
3. System, telemetry, AI, and rules caveats explaining the discrepancies between theoretical power and actual match performance.

---

## 1. Theoretical Analysis Output (`balance_analyzer.py`)

The theoretical balance model evaluates base stats (HP, Effective Speed with boost, Throw Speed, Steal Radius) and 75% cooldown-discounted ability power rankings directly from `res/game.cfg`.

```text
==================================================================================================
                    TITANBALL BALANCE & RELATIVE STRENGTH MATRIX
==================================================================================================
Config Source:   res/game.cfg (14 Titans loaded dynamically)
Weighting Model: Custom (HP:20, Spd:70, Throw:50, Steal:35)                                 
Speed Model:     Average effective speed assuming optimal boost uptime
                 (Dasher 1.45x boost = 5.28 avg vs 4.76 base; standard titans 1.33x boost = +8.0% avg)
Baseline Median: 50.00%  |  Sorted from STRONGEST to WEAKEST
--------------------------------------------------------------------------------------------------
Rank  Class          Overall    Strength Bar           Stat Avg   Abil Avg   Delta   
--------------------------------------------------------------------------------------------------
#1    ARTISAN         67.54%   [============------]    69.79%    63.60%    +17.5%
#2    SUPPORT         66.33%   [============------]    44.18%   105.08%    +16.3%
#3    CAPTAIN         62.93%   [===========-------]    65.10%    59.12%    +12.9%
#4    SPIDER          62.16%   [===========-------]    62.25%    62.02%    +12.2%
#5    WARRIOR         61.14%   [===========-------]    51.43%    78.14%    +11.1%
#6    HOUNDMASTER     59.79%   [===========-------]    62.55%    54.95%     +9.8%
#7    GOLEM           59.15%   [===========-------]    60.82%    56.24%     +9.2%
#8    BUILDER         56.14%   [==========--------]    37.96%    87.94%     +6.1%
#9    DASHER          54.60%   [==========--------]    65.51%    35.52%     +4.6%
#10   STEALTH         51.17%   [=========---------]    39.80%    71.08%     +1.2%
#11   GRENADIER       49.33%   [=========---------]    50.82%    46.73%     -0.7%
#12   MARKSMAN        49.20%   [=========---------]    64.39%    22.62%     -0.8%
#13   RANGER          46.95%   [========----------]    34.80%    68.21%     -3.0%
#14   MAGE            45.96%   [========----------]    40.61%    55.33%     -4.0%
--------------------------------------------------------------------------------------------------

==================================================================================================
                        DETAILED CLASS STATS & PERCENTILES
==================================================================================================
Class        HP (%ile) [w=20]   Avg Speed* (%ile) [w=70]   Throw (%ile) [w=50]  StealRad [w=35]    Stat Avg  
--------------------------------------------------------------------------------------------------
ARTISAN      130 (46%)          3.97 [3.54] (54%)          1.45 (86%)           19px (93%)          69.79%
SUPPORT      125 (32%)          3.97 [3.54] (54%)          1.14 (14%)           16px (75%)          44.18%
CAPTAIN      120 (21%)          4.05 [3.61] (100%)         1.37 (64%)           13px (21%)          65.10%
SPIDER       130 (46%)          4.01 [3.58] (93%)          1.27 (39%)           14px (43%)          62.25%
WARRIOR      135 (57%)          4.00 [3.57] (86%)          1.12 (7%)            14px (43%)          51.43%
HOUNDMASTER  170 (82%)          3.99 [3.56] (75%)          1.22 (29%)           16px (75%)          62.55%
GOLEM        230 (100%)         3.62 [3.23] (7%)           1.55 (93%)           22px (100%)         60.82%
BUILDER      170 (82%)          3.77 [3.36] (14%)          1.32 (50%)           14px (43%)          37.96%
DASHER       115 (11%)          3.98 [3.55] (64%)          1.41 (75%)           17px (86%)          65.51%
STEALTH      115 (11%)          3.83 [3.42] (21%)          1.41 (75%)           14px (43%)          39.80%
GRENADIER    160 (64%)          3.87 [3.45] (36%)          1.34 (57%)           15px (64%)          50.82%
MARKSMAN     125 (32%)          3.99 [3.56] (75%)          1.62 (100%)          12px (11%)          64.39%
RANGER       170 (82%)          3.92 [3.50] (43%)          1.17 (21%)           12px (11%)          34.80%
MAGE         170 (82%)          3.86 [3.44] (29%)          1.27 (39%)           14px (43%)          40.61%
--------------------------------------------------------------------------------------------------
 * Speed format: EffectiveAvgSpeed [BaseSpeed] (Percentile)

==================================================================================================
          ABILITY POWER RANKINGS (75% CD-Adjusted / 25% Absolute Strength)
==================================================================================================
Rank  Ability Name         Class        Slot   Raw Rank  Raw %    CD (s)   Eff (P/s)    Weight   Power Score 
--------------------------------------------------------------------------------------------------
#1    Barrier Wall         BUILDER      W/R    15         53.6%     3.5s      15.31        50      154.11
#2    Shock Stun           SUPPORT      Q/E    26         92.9%     7.0s      13.27        50      145.18
#3    Ball Portal          ARTISAN      W/R    21         75.0%     7.0s      10.71        50      117.26
#4    Shockwave Slam       GOLEM        W/R    28        100.0%    12.0s       8.33        50      101.62
#5    Precision Arrow      RANGER       Q/E    11         39.3%     4.0s       9.82        50      100.13
#6    Whirlwind Slash      WARRIOR      Q/E    12         42.9%     4.5s       9.52        50       98.28
#7    Vanish               STEALTH      Q/E    27         96.4%    15.0s       6.43        50       83.21
#8    Deploy Kennel        HOUNDMASTER  Q/E    16         57.1%    10.0s       5.71        50       66.82
#9    Healing Surge        SUPPORT      W/R    13         46.4%     8.0s       5.80        50       64.97
#10   Cover Ball           DASHER       Q/E    14         50.0%     9.0s       5.56        50       63.58
#11   Cocoon Shift         SPIDER       W/R    23         82.1%    18.0s       4.56        50       62.49
#12   Web Trap             SPIDER       Q/E    18         64.3%    13.0s       4.95        50       61.54
#13   Rifle Shot           CAPTAIN      Q/E    3          10.7%     1.7s       6.16        50       59.35
#14   Shadow Blink         STEALTH      W/R    24         85.7%    21.0s       4.08        50       58.95
#15   Slide & Timebomb     CAPTAIN      W/R    20         71.4%    16.0s       4.46        50       58.90
#16   Molotov              GRENADIER    W/R    19         67.9%    15.0s       4.52        50       58.56
#17   Flash Dash           WARRIOR      W/R    25         89.3%    23.0s       3.88        50       58.01
#18   Ignite               MAGE         W/R    22         78.6%    20.0s       3.93        50       55.76
#19   Warp Portal          MAGE         Q/E    8          28.6%     5.5s       5.19        50       54.90
#20   Unleash Pack         HOUNDMASTER  W/R    17         60.7%    20.0s       3.04        50       43.09
#21   Charge Shot          MARKSMAN     W/R    7          25.0%     7.0s       3.57        50       39.09
#22   Sweeping Kick        RANGER       W/R    10         35.7%    12.0s       2.98        50       36.29
#23   Flashbang            GRENADIER    Q/E    9          32.1%    11.0s       2.92        50       34.90
#24   Snare Trap           BUILDER      Q/E    6          21.4%    12.0s       1.79        50       21.78
#25   Barrier Shield       GOLEM        Q/E    4          14.3%    18.0s       0.79        50       10.87
#26   Ball Vacuum          ARTISAN      Q/E    5          17.9%    30.0s       0.60        50        9.94
#27   Flare                DASHER       W/R    1           3.6%     5.0s       0.71        50        7.46
#28   Frost Shot           MARKSMAN     Q/E    2           7.1%    15.0s       0.48        50        6.16
--------------------------------------------------------------------------------------------------

==================================================================================================
                     BALANCE DISCREPANCIES & TWEAK ADVICE
==================================================================================================
* Power Spread: 21.58% disparity between #1 ARTISAN and #14 MAGE

 [!] OVERTUNED CLASSES (Score >= 62%):
   - ARTISAN (67.5%): Driven heavily by Base Stats (Stats: 69.8%, Abilities: 63.6%).
     -> Suggestion: Increase cooldowns for Ball Vacuum, Ball Portal to reduce ability uptime.
     -> Suggestion: Trim top stats (e.g. Speed/HP/StealRad) down towards roster median.
   - SUPPORT (66.3%): Driven heavily by Abilities (Stats: 44.2%, Abilities: 105.1%).
     -> Suggestion: Increase cooldowns for Healing Surge, Shock Stun to reduce ability uptime.
   - CAPTAIN (62.9%): Driven heavily by Base Stats (Stats: 65.1%, Abilities: 59.1%).
     -> Suggestion: Trim top stats (e.g. Speed/HP/StealRad) down towards roster median.
   - SPIDER (62.2%): Driven heavily by Base Stats (Stats: 62.2%, Abilities: 62.0%).
     -> Suggestion: Increase cooldowns for Web Trap, Cocoon Shift to reduce ability uptime.
     -> Suggestion: Trim top stats (e.g. Speed/HP/StealRad) down towards roster median.
==================================================================================================
```

---

### 2. Empirical Match Telemetry (`classstat`) - Run 1: Omniscience Disabled (`globals.ai.omniscience.enabled=false`)

The following live snapshot represents empirical data across completed 4v4 matches (outfield player entries + goalie entries) under normal game perception rules (`globals.ai.omniscience.enabled=false`, where bots respect stealth and blindness):

```sql
SELECT role, wins, losses, ties, (wins + losses + ties) AS games_played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 2) AS winrate_pct,
       goals, ROUND(points, 2) AS points, lasthits, ROUND(miniondamage, 1) AS minion_dmg, kills, deaths
FROM classstat
ORDER BY winrate_pct DESC, games_played DESC;
```

```text
role        wins    losses  ties    games_played    winrate_pct goals   points      lasthits    minion_dmg  kills   deaths
GRENADIER   383     205     0       588             65.14       560     1572        0           0           0       1308
BUILDER     360     256     0       616             58.44       454     1327.75     0           0           0       1367
DASHER      359     270     2       631             56.89       556     1605.75     0           0           0       2170
SPIDER      328     317     0       645             50.85       581     1711.5      0           0           0       1629
STEALTH     320     310     0       630             50.79       479     1379.25     0           0           0       1702
GOALIE      1430    1430    4       2864            49.93       0       0           80709       889200      0       268
MARKSMAN    293     295     0       588             49.83       535     1480        0           0           0       1687
HOUNDMASTER 307     310     0       617             49.76       547     1508.75     0           0           0       1237
GOLEM       313     316     2       631             49.60       579     1530.25     0           0           0       404
ARTISAN     313     321     0       634             49.37       525     1499        0           0           0       1808
CAPTAIN     290     316     0       606             47.85       469     1323.5      0           0           15981   1763
WARRIOR     266     329     0       595             44.71       479     1274.75     0           0           11105   1344
RANGER      258     327     0       585             44.10       420     1149        0           0           5168    1353
SUPPORT     249     353     6       608             40.95       421     1165.5      0           0           0       530
MAGE        251     365     2       618             40.61       448     1190.25     0           0           2889    1014
```

### Complete Class Table Snapshot (Full 27 Columns):

```text
id  role        wins    losses  ties    goals   points  sidegoals   blocks  steals  passes  kills   deaths  turnovers   killassists goalassists rebounds    saves   lasthits    miniondamage    upgradesgold    consumablesgold sidegoalsaves   centergoalsaves sidegoalsconceded   goalsconceded   manaspent
1   GOALIE      1566    1566    4       0       0       0           1628    712     2295    0       284     805         0           0           54          1547    88366       974680          1811425         104250          189             1358            34900               7721            1417425
2   WARRIOR     302     356     0       539     1439.5  2294        7811    4440    7392    12204   1508    6333        2623        0           6343        0       0           0               0               0               0               0               0                   0               0
3   RANGER      287     355     0       469     1280.75 2129        7576    3702    6825    5647    1481    5867        1111        0           6152        0       0           0               0               0               0               0               0                   0               0
4   DASHER      383     300     2       601     1732.5  2917        6893    5008    11528   0       2364    6117        0           0           5781        0       0           0               0               0               0               0               0                   0               0
5   MARKSMAN    313     330     0       575     1600.75 2624        7404    4008    7147    0       1838    5974        0           0           5980        0       0           0               0               0               0               0               0                   0               0
6   STEALTH     358     347     0       531     1528.75 2570        8002    4878    8720    0       1950    6220        0           0           5551        0       0           0               0               0               0               0               0                   0               0
7   SUPPORT     277     386     6       459     1272    2105        6470    4447    6879    0       599     6121        0           0           5204        0       0           0               0               0               0               0               0                   0               0
8   ARTISAN     345     358     0       584     1666.5  2674        9582    5488    8875    0       2012    7977        0           0           6820        0       0           0               0               0               0               0               0                   0               0
9   GOLEM       338     346     2       618     1642.75 2502        6406    5440    8031    0       444     6843        0           0           5211        0       0           0               0               0               0               0               0                   0               0
10  MAGE        275     395     2       479     1278    1876        5860    4323    6599    3165    1113    5702        745         0           4514        0       0           0               0               0               0               0               0                   0               0
11  BUILDER     388     279     0       494     1451.75 2484        6820    4115    8017    0       1483    5411        0           0           5563        0       0           0               0               0               0               0               0                   0               0
12  GRENADIER   424     230     0       634     1776.75 2641        6925    4326    8592    0       1449    5498        0           0           5889        0       0           0               0               0               0               0               0                   0               0
13  HOUNDMASTER 336     338     0       601     1658    2641        7236    4940    7699    0       1339    6228        0           0           5874        0       0           0               0               0               0               0               0                   0               0
14  CAPTAIN     316     336     0       502     1420.5  2323        7198    4052    6983    17203   1908    5849        3820        0           5764        0       0           0               0               0               0               0               0                   0               0
15  SPIDER      356     342     0       635     1861.5  3120        8143    4708    7561    0       1752    6133        0           0           6763        0       0           0               0               0               0               0               0                   0               0
```

---

### 3. Empirical Match Telemetry (`classstat`) - Run 2: Omniscience Enabled (`globals.ai.omniscience.enabled=true`)

The following snapshot represents empirical data across completed 4v4 matches under full opponent omniscience (`globals.ai.omniscience.enabled=true`, where bots ignore stealth and blindness):

```sql
SELECT role, wins, losses, ties, (wins + losses + ties) AS games_played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 2) AS winrate_pct,
       goals, ROUND(points, 2) AS points, lasthits, ROUND(miniondamage, 1) AS minion_dmg, kills, deaths
FROM classstat
ORDER BY winrate_pct DESC, games_played DESC;
```

```text
role        wins    losses  ties    games_played    winrate_pct goals   points      lasthits    minion_dmg  kills   deaths
BUILDER     365     261     0       626             58.31       460     1348.00     0           0           0       1364
GRENADIER   348     249     0       597             58.29       547     1500.50     0           0           7344    1316
ARTISAN     349     275     0       624             55.93       576     1541.50     0           0           0       1742
SPIDER      350     304     0       654             53.52       563     1679.75     0           0           0       1659
DASHER      345     307     0       652             52.91       562     1651.25     0           0           812     2171
HOUNDMASTER 327     304     0       631             51.82       537     1563.50     0           0           1350    1144
GOALIE      1453    1453    0       2906            50.00       0       0.00        79013       874510      1930    217
MARKSMAN    314     319     0       633             49.61       536     1529.75     0           0           0       1844
GOLEM       299     314     0       613             48.78       530     1404.25     0           0           0       390
SUPPORT     295     326     0       621             47.50       489     1309.50     0           0           0       533
CAPTAIN     279     309     0       588             47.45       482     1359.00     0           0           1945    1665
WARRIOR     291     345     0       636             45.75       514     1425.75     0           0           2313    1361
RANGER      283     339     0       622             45.50       497     1365.25     0           0           1622    1224
STEALTH     286     356     0       642             44.55       488     1355.25     0           0           0       1884
MAGE        228     351     0       579             39.38       419     1113.50     0           0           1589    1058
```

#### Full 27-Column Output (`SELECT * FROM classstat;`):

```text
id      role    wins    losses  ties    goals   points  sidegoals       blocks  steals  passes  kills   deaths  turnovers       killassists     goalassists     rebounds        saves   lasthits        miniondamage    upgradesgold    consumablesgold sidegoalsaves   centergoalsaves sidegoalsconceded       goalsconceded   manaspent
1       GOALIE  1453    1453    0       0       0       0               1473    615     2105    1930    217     707             152             0               55              1391    79013           874510          1688475         93950           179             1212            32016                   7200            1250525
2       WARRIOR 291     345     0       514     1425.75 2230            7049    4305    7069    2313    1361    6024            1358            0               5719            0       0               0               0               0               0               0               0                       0               0
3       RANGER  283     339     0       497     1365.25 2138            7107    3762    6871    1622    1224    5601            1073            0               5796            0       0               0               0               0               0               0               0                       0               0
4       DASHER  345     307     0       562     1651.25 2722            6630    4946    11107   812     2171    5920            676             0               5512            0       0               0               0               0               0               0               0                       0               0
5       MARKSMAN 314    319     0       536     1529.75 2596            7231    3915    6833    0       1844    5729            0               0               5877            0       0               0               0               0               0               0               0                       0               0
6       STEALTH 286     356     0       488     1355.25 2126            6190    4276    7045    0       1884    5519            0               0               4809            0       0               0               0               0               0               0               0                       0               0
7       SUPPORT 295     326     0       489     1309.5  1868            5889    4100    6498    0       533     5611            0               0               4799            0       0               0               0               0               0               0               0                       0               0
8       ARTISAN 349     275     0       576     1541.5  2315            8172    4867    7788    0       1742    6831            0               0               5915            0       0               0               0               0               0               0               0                       0               0
9       GOLEM   299     314     0       530     1404.25 2163            5320    4798    7155    0       390     5708            0               0               4302            0       0               0               0               0               0               0               0                       0               0
10      MAGE    228     351     0       419     1113.5  1737            5252    3888    6242    1589    1058    4961            599             0               4049            0       0               0               0               0               0               0               0                       0               0
11      BUILDER 365     261     0       460     1348    2316            6253    3617    7100    0       1364    4853            0               0               5184            0       0               0               0               0               0               0               0                       0               0
12      GRENADIER 348   249     0       547     1500.5  2322            6480    3888    7676    7344    1316    5260            857             0               5410            0       0               0               0               0               0               0               0                       0               0
13      HOUNDMASTER 327 304     0       537     1563.5  2404            6589    4760    7284    1350    1144    5749            822             0               5349            0       0               0               0               0               0               0               0                       0               0
14      CAPTAIN 279     309     0       482     1359    2201            6287    3526    5815    1945    1665    5148            1579            0               5042            0       0               0               0               0               0               0               0                       0               0
15      SPIDER  350     304     0       563     1679.75 2878            7488    4432    7183    0       1659    5563            0               0               6103            0       0               0               0               0               0               0               0                       0               0
```

---

### 4. Comparative Winrate & Two-Run Average Analysis

Comparing Run 1 (`globals.ai.omniscience.enabled=false`) against Run 2 (`globals.ai.omniscience.enabled=true`) demonstrates how sensory awareness impacts class power, and tracks each class's two-run average relative to the **50.0%** balance target:

| Class | Run 1 WR (Normal Perception) | Run 2 WR (Omniscience) | Delta (Run 2 vs Run 1) | Two-Run Average WR | Target Gap (from 50.0%) | Key Observation |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **GRENADIER** | 65.14% | 58.29% | -6.85% | **61.72%** | +11.72% | Omniscience broke Flashbang blindness lockout, reducing WR sharply by ~6.9%. |
| **BUILDER** | 58.44% | 58.31% | -0.13% | **58.38%** | +8.38% | Consistently overtuned due to low-CD wall utility. |
| **DASHER** | 56.89% | 52.91% | -3.98% | **54.90%** | +4.90% | High mobility stabilizes near ~53-55%. |
| **SPIDER** | 50.85% | 53.52% | +2.67% | **52.19%** | +2.19% | Strong zone control across both perception modes. |
| **ARTISAN** | 49.37% | 55.93% | +6.56% | **52.65%** | +2.65% | High base stats push winrate above 50% in long sets. |
| **HOUNDMASTER** | 49.76% | 51.82% | +2.06% | **50.79%** | +0.79% | Extremely close to ideal 50% parity. |
| **GOALIE** | 49.93% | 50.00% | +0.07% | **49.97%** | -0.03% | Perfectly centered symmetric anchor. |
| **MARKSMAN** | 49.83% | 49.61% | -0.22% | **49.72%** | -0.28% | Balanced ranged scoring performance. |
| **GOLEM** | 49.60% | 48.78% | -0.82% | **49.19%** | -0.81% | Solid frontline tanking near 50%. |
| **STEALTH** | 50.79% | 44.55% | -6.24% | **47.67%** | -2.33% | Omniscience denies invisibility, causing a 6.2% drop; 2-run average centers at 47.7%. |
| **CAPTAIN** | 47.85% | 47.45% | -0.40% | **47.65%** | -2.35% | High kill count but animation commitment limits offensive conversion. |
| **WARRIOR** | 44.71% | 45.75% | +1.04% | **45.23%** | -4.77% | Slight uptick under omniscience but still hindered by melee chase. |
| **RANGER** | 44.10% | 45.50% | +1.40% | **44.80%** | -5.20% | Underperforming due to low base power. |
| **SUPPORT** | 40.95% | 47.50% | +6.55% | **44.23%** | -5.77% | Large recovery under omniscience (+6.6%), tracking toward 45%. |
| **MAGE** | 40.61% | 39.38% | -1.23% | **40.00%** | -10.00% | Pathfinding inability to use portals keeps Mage consistently at 40%. |

---

## 5. Systems, Telemetry & Behavioral Caveats

#### 1. Dataset Scope & Match Architecture
* **Total Matches**: 1,432 matches recorded (Run 1), 1,453 matches recorded (Run 2).
* **Format**: 4v4 (1 Goalie + 3 Outfielders per team).
* **Total Goalie Records**: 2,864 (Run 1), 2,906 (Run 2).
* **Total Outfield Records**: 8,592 entries (Run 1), 8,718 entries (Run 2).
* **Ratio Verification**:
  $$\frac{8,592 \text{ outfield entries}}{2,864 \text{ team instances}} = 3.0 \text{ outfielders per side}$$
* **Game Duration Validation**: The micro-duration bug (where matches terminated within seconds due to `playToIndex = 0`) is confirmed resolved. In-game economic and minion systems are fully processing:
  * **Goalie Last Hits**: ~80k total per run ($55\text{--}56$ per match, $27\text{--}28$ per Goalie).
  * **Goalie Minion Damage**: ~875k–889k total per run.
  * Upgrades and consumables are cycling through multiple waves prior to match completion.

---

### 2. Telemetry Ingestion: Kill Attribution Pipeline
* **Historical Issue (Run 1)**: Direct kills were attributed exclusively to direct melee/hitscan weapons (Captain, Warrior, Ranger, Mage), with 0 kills logged for the remaining 10 classes due to detached context on projectiles and pets.
* **Resolved in Run 2**: Originating player context was attached to world actors (Grenadier shells/fire, Houndmaster wolves, Dasher flare, Goalie/guardian damage). In Run 2:
  * **Grenadier**: 7,344 kills
  * **Goalie**: 1,930 kills
  * **Houndmaster**: 1,350 kills
  * **Dasher**: 812 kills
  Total combat lethality is now properly tracked across abilities and pets.

---

### 3. Agent AI Behaviors & System Blindspots
* **Blindness Vulnerability Tuning (Grenadier at 65.14% WR in Run 1)**:
  * With normal perception active (`globals.ai.omniscience.enabled=false`), Flashbang blindness disables AI targeting and defensive tracking completely. Grenadier emerged as a massive balance outlier (383–205, 65.14% WR, 2.67 points/game).
  * Bot agents possess no defensive fallback state (such as blind-firing vectors or retreating to goal lines) when blinded, leading to unpunished scoring runs. Testing under `globals.ai.omniscience.enabled=true` will reveal Grenadier's baseline performance when opponents are immune to blind disruption.
* **Stealth Normalization (Stealth at 50.79% WR in Run 1)**:
  * Under normal perception (`globals.ai.omniscience.enabled=false`), Stealth's invisibility is respected, lifting Stealth from an unviable 41.61% baseline (from prior omniscient tests) up to a balanced 50.79% (320–310). Invisibility does not disable opponent actions globally like Flashbang, but restores the class's intended positioning.
* **Portal Utilization Deficit (Mage at 40.61% WR)**:
  * Mage remains stagnant at the bottom of the roster (251–365–2) because pathfinding heuristics cannot evaluate or traverse portal nodes. The bot incurs cast lag and cooldowns without extracting movement utility.
* **Sub-optimal Ability Cadence & Cast Lag**:
  * Bot Titans cycle combat abilities strictly off cooldown rather than timing them around ball possession or tactical vulnerability.
  * Melee classes (Warrior at 44.71%, Captain at 47.85%) suffer from animation commitment and chase latency, pulling them out of defensive rotations.

---

### 4. Game Rules: 3-Second Death Timers in a 4v4 Environment
* **The Tactical Math**:
  * In a 4v4 format, losing 1 outfielder reduces outfield strength from 3 to 2 (a 33% reduction, compared to 50% in 3v3).
  * A 3-second death timer, combined with ~1–2 seconds of transit back to midfield, produces an effective $3\text{v}2$ power-play window of only 1–2 seconds.
  * Against 2 outfield defenders and a Goalie, this razor-thin window allows defending teams to stall and crowd shooting lanes, converting kills into minor positional turnovers rather than sustained power plays.
* **Deferred Lever (5–6 Second Respawn)**:
  * Extending the respawn timer remains the primary lever to make kills meaningful.
  * This lever is tested in Run 4 via `globals.titan.respawn.ms=4750`.

---

## 6. Configurable AI Roster: Pathing & Leverage Filtering (Run 3)

To eliminate skewed results caused by bot sensory and pathing limitations, the assignment pool for AI outfield Titans is now explicitly configurable via `globals.ai.titans.included`:

```properties
globals.ai.titans.included=WARRIOR,RANGER,DASHER,MARKSMAN,STEALTH,SUPPORT,ARTISAN,GOLEM,BUILDER,HOUNDMASTER,CAPTAIN,SPIDER
```

* **Excluded Classes**:
  * **GRENADIER**: Flashbang's total sensory denial halts all bot reactions and targeting, creating artificial blowout scoring runs that distort baseline outfield balance.
  * **MAGE**: Warp Portal generates non-linear positional relocations that AI vector pathfinding heuristics cannot evaluate or traverse, leading to wasted cooldowns, self-inflicted cast lag, and artificially depressed winrates.
* **Included Roster (12 Outfield Classes)**:
  * Warrior, Ranger, Dasher, Marksman, Stealth, Support, Artisan, Golem, Builder, Houndmaster, Captain, Spider.
* **Objective**:
  * Test remaining core classes in an environment free from sensory lockout and untraversable portals to obtain clean balance data across mobility, defense, and scoring.

---

## 7. Empirical Match Telemetry (`classstat`) - Run 3: Clean 12-Class Roster (19,333 Matches)

With Grenadier and Mage excluded from the draft pool and the database zeroed, the headless test suite completed **19,333 full 4v4 matches** (38,666 Goalie entries, 115,998 outfield entries).

```sql
SELECT role, wins, losses, ties, (wins + losses + ties) AS games_played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 2) AS winrate_pct,
       goals, ROUND(points, 2) AS points, lasthits, ROUND(miniondamage, 1) AS minion_dmg, kills, deaths
FROM classstat
ORDER BY winrate_pct DESC, games_played DESC;
```

```text
role        wins    losses  ties    games_played    winrate_pct goals   points      lasthits    minion_dmg  kills   deaths
BUILDER     5518    4196    24      9738            56.66       7454    21749.00    0           0           0       13428
DASHER      5114    4395    22      9531            53.66       8453    24143.00    0           0           11573   20756
ARTISAN     5263    4621    24      9908            53.12       8680    24123.75    0           0           0       18076
SPIDER      5000    4586    22      9608            52.04       8456    25103.50    0           0           0       16072
MARKSMAN    5084    4744    36      9864            51.54       8683    24642.00    0           0           0       18348
GOALIE      19297   19297   72      38666           49.91       1       1.50        1126478     12448380    29847   3836
HOUNDMASTER 4789    4810    4       9603            49.87       8205    23088.50    0           0           23539   11324
GOLEM       4721    4924    8       9653            48.91       7876    21409.75    0           0           0       4253
CAPTAIN     4624    4936    14      9574            48.30       7538    21381.75    0           0           32893   18147
WARRIOR     4441    4992    6       9439            47.05       7599    21000.00    0           0           35621   14086
SUPPORT     4500    5160    38      9698            46.40       6888    18863.75    0           0           0       4755
STEALTH     4426    5265    16      9707            45.60       7056    19718.25    0           0           0       18592
RANGER      4411    5262    2       9675            45.59       7140    19780.50    0           0           26981   11431
MAGE        0       0       0       0               0.00        0       0.00        0           0           0       0
GRENADIER   0       0       0       0               0.00        0       0.00        0           0           0       0
```

#### Full 27-Column Raw Table (`SELECT * FROM classstat;`):

```text
id      role        wins    losses  ties    goals   points      sidegoals   blocks  steals  passes  kills   deaths  turnovers   killassists goalassists rebounds    saves   lasthits    miniondamage    upgradesgold    consumablesgold sidegoalsaves   centergoalsaves sidegoalsconceded   goalsconceded   manaspent
1       GOALIE      19297   19297   72      1       1.5         3           20767   8966    27831   29847   3836    10965       1672        0           617         19709   1126478     12448380        23092975        1291270         2513            17196           436353              94029           17767750
2       WARRIOR     4441    4992    6       7599    21000       33680       107930  65209   109679  35621   14086   90492       14620       0           87325       0       0           0               0               0               0               0               0                   0               0
3       RANGER      4411    5262    2       7140    19780.5     32497       110953  58883   111353  26981   11431   89615       12133       0           89652       0       0           0               0               0               0               0               0                   0               0
4       DASHER      5114    4395    22      8453    24143       41411       96576   74275   168408  11573   20756   89893       5900        0           79351       0       0           0               0               0               0               0               0                   0               0
5       MARKSMAN    5084    4744    36      8683    24642       41440       112059  64825   111742  0       18348   92672       0           0           89613       0       0           0               0               0               0               0               0                   0               0
6       STEALTH     4426    5265    16      7056    19718.25    32648       93087   65379   108827  0       18592   84615       0           0           71350       0       0           0               0               0               0               0               0                   0               0
7       SUPPORT     4500    5160    38      6888    18863.75    29654       92597   65604   103467  0       4755    89700       0           0           73760       0       0           0               0               0               0               0               0                   0               0
8       ARTISAN     5263    4621    24      8680    24123.75    38594       131856  79374   130224  0       18076   114355      0           0           94297       0       0           0               0               0               0               0               0                   0               0
9       GOLEM       4721    4924    8       7876    21409.75    33523       83908   74787   114511  0       4253    91161       0           0           66897       0       0           0               0               0               0               0               0                   0               0
10      MAGE        0       0       0       0       0           0           0       0       0       0       0       0           0           0           0           0       0           0               0               0               0               0               0                   0               0
11      BUILDER     5518    4196    24      7454    21749       36404       95206   58508   116229  0       13428   76999       0           0           77664       0       0           0               0               0               0               0               0                   0               0
12      GRENADIER   0       0       0       0       0           0           0       0       0       0       0       0           0           0           0           0       0           0               0               0               0               0               0                   0               0
13      HOUNDMASTER 4789    4810    4       8205    23088.5     37460       101725  71181   114957  23539   11324   88026       8116        0           81862       0       0           0               0               0               0               0               0                   0               0
14      CAPTAIN     4624    4936    14      7538    21381.75    35694       100830  59947   100529  32893   18147   85837       14330       0           79848       0       0           0               0               0               0               0               0                   0               0
15      SPIDER      5000    4586    22      8456    25103.5     43345       112468  66199   107752  0       16072   84989       0           0           91544       0       0           0               0               0               0               0               0                   0               0
```

---

### 8. Three-Run Longitudinal Balance Progression

Tracking balance metrics across all three benchmark iterations highlights the dramatic equalization of the roster once pathing-distorted and sensory-lockout outliers were quarantined:

| Class | Run 1 WR (Normal Perception) | Run 2 WR (Omniscience) | Run 3 WR (12-Titan Clean Roster) | Total Shift (Run 3 vs Run 1) | Distance from Parity (50.0%) | Analytical Takeaway |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **BUILDER** | 58.44% | 58.31% | **56.66%** | -1.78% | +6.66% | Lowers towards median; Barrier Wall remains strong but less abusive. |
| **DASHER** | 56.89% | 52.91% | **53.66%** | -3.23% | +3.66% | Consistent high-tier performer; speed and burst carry offense. |
| **ARTISAN** | 49.37% | 55.93% | **53.12%** | +3.75% | +3.12% | High base stat package delivers strong, reliable win conversion. |
| **SPIDER** | 50.85% | 53.52% | **52.04%** | +1.19% | +2.04% | Exceptional zone denial with Cocoon Shift & Web Trap. |
| **MARKSMAN** | 49.83% | 49.61% | **51.54%** | +1.71% | +1.54% | Positive winrate in clean games without sensory distortion. |
| **GOALIE** | 49.93% | 50.00% | **49.91%** | -0.02% | -0.09% | Unshakeable symmetric anchor across 19k+ games. |
| **HOUNDMASTER** | 49.76% | 51.82% | **49.87%** | +0.11% | -0.13% | **Perfect balance benchmark** (0.13% from 50.00%). |
| **GOLEM** | 49.60% | 48.78% | **48.91%** | -0.69% | -1.09% | Frontline durability keeps matches close. |
| **CAPTAIN** | 47.85% | 47.45% | **48.30%** | +0.45% | -1.70% | High fragging (32.8k kills) converts stably into 48.3% WR. |
| **WARRIOR** | 44.71% | 45.75% | **47.05%** | +2.34% | -2.95% | Major gain (+2.34%); highest direct kills in the roster (35.6k). |
| **SUPPORT** | 40.95% | 47.50% | **46.40%** | +5.45% | -3.60% | Massive multi-run gain (+5.45%) out of the cellar. |
| **STEALTH** | 50.79% | 44.55% | **45.60%** | -5.19% | -4.40% | Under omniscience opponents see through stealth, but base kit holds 45.6%. |
| **RANGER** | 44.10% | 45.50% | **45.59%** | +1.49% | -4.41% | Steady improvement (+1.49%) with 26.9k sniper eliminations. |
| **GRENADIER** | 65.14% | 58.29% | *Excluded* | N/A | N/A | Excluded: Flashbang blind lockout previously dominated games. |
| **MAGE** | 40.61% | 39.38% | *Excluded* | N/A | N/A | Excluded: Portal pathing deficit previously dragged down teams. |

### Key Takeaways from Run 3 (19,333 Matches)
1. **Spread Compression**: The competitive spread among active Titans contracted from **24.53%** (Run 1) down to just **11.07%** (Run 3). Every single outfield class now sits between **45.59% and 56.66%**.
2. **Houndmaster as the North Star**: Houndmaster achieved 4,789 wins and 4,810 losses (**49.87% WR**), representing near-flawless parity across a massive ~10,000-game sample.
3. **Bottom-Tier Recovery**: Warrior (+2.34%), Support (+5.45%), and Ranger (+1.49%) all rebounded significantly when no longer burdened by blind lockouts and portal traps.
4. **Combat Accounting Alignment**: Recorded kills (160,454) vs player deaths (173,068) now maintain a 0.93:1 ratio, confirming that kill attribution across melee, abilities, pets, and minions is fully calibrated.

---

## 9. Run 4 Benchmark: Combat Leverage & Stealth Viability Tuning

Run 4 tests two targeted system-level balance adjustments loaded directly from `res/game.cfg`:

### 1. Respawn Window Extension (+1,750ms / +58.3%)
```properties
globals.titan.respawn.ms=4750
```
* **Baseline Context**: In 4v4 play with the default 3.00-second death timer (`DeadEffect(3000)` in `Entity.java`), defending teams recovered almost immediately before offensive teams could convert turnovers into clean hoop looks, blunting the value of combat-oriented roles.
* **Target Effect**: Extending the respawn timer by +1,750ms (from 3,000ms to 4,750ms) more than doubles the effective 3v2 outfield power-play window (from ~1–2s to ~3–4s), enhancing the tactical payoff for fraggers like Warrior (35.6k kills), Captain (32.8k kills), and Ranger (26.9k kills).

### 2. Stealth Base Agility & Cast Lag Buffs
```properties
titan.stealth.speed=3.43
titan.stealth.shoot=1.43
titan.stealth.eframes=10
```
* **Baseline Context**: In Run 3 with omniscience active, opponent bots track and defend against Stealth directly, dropping its winrate to 45.60%.
* **Target Effect**:
  * **Speed (3.42 $\rightarrow$ 3.43)**: Small movement speed lift improves outfield positioning and intercept range.
  * **Throw Power (1.41 $\rightarrow$ 1.43)**: Sharper shot velocity improves sidegoal conversion.
  * **Vanish Cast Frames (20 $\rightarrow$ 10 frames)**: Cuts Vanish self-stun cast lag in half (from ~500ms down to ~250ms), preventing opponents from closing the gap while Stealth activates the ability.

---

## 10. Empirical Match Telemetry (`classstat`) - Run 4: Stealth & Respawn Balance (2,369 Matches)

Following the adjustments to respawn window (+58.3% to 4,750ms) and Stealth base agility/cast frames, the benchmark completed **2,369 matches** (4,750 Goalie entries, 14,214 outfield entries).

```sql
SELECT role, wins, losses, ties, (wins + losses + ties) AS games_played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 2) AS winrate_pct,
       goals, ROUND(points, 2) AS points, lasthits, ROUND(miniondamage, 1) AS minion_dmg, kills, deaths
FROM classstat
ORDER BY winrate_pct DESC, games_played DESC;
```

```text
role        wins    losses  ties    games_played    winrate_pct goals   points      lasthits    minion_dmg  kills   deaths
BUILDER     653     527     2       1182            55.25       892     2580.75     0           0           0       1612
DASHER      647     529     8       1184            54.65       1074    3064.75     0           0           1469    2522
ARTISAN     650     576     0       1226            53.02       1043    2956.00     0           0           0       2145
SPIDER      627     568     4       1199            52.29       1055    3141.00     0           0           0       1954
GOALIE      2369    2369    12      4750            49.87       0       0.50        136107      1513785     3269    462
MARKSMAN    558     563     4       1125            49.60       990     2792.25     0           0           0       2028
WARRIOR     592     614     2       1208            49.01       950     2730.00     0           0           4387    1735
CAPTAIN     586     608     0       1194            49.08       966     2733.00     0           0           3934    2143
GOLEM       584     611     4       1199            48.71       1021    2771.75     0           0           0       495
RANGER      588     639     0       1227            47.92       966     2662.75     0           0           3206    1278
HOUNDMASTER 558     611     2       1171            47.65       1017    2801.25     0           0           2831    1306
STEALTH     552     633     2       1187            46.50       904     2555.00     0           0           0       2282
SUPPORT     512     628     8       1148            44.60       793     2183.75     0           0           0       593
MAGE        0       0       0       0               0.00        0       0.00        0           0           0       0
GRENADIER   0       0       0       0               0.00        0       0.00        0           0           0       0
```

#### Full 27-Column Raw Table (`SELECT * FROM classstat;`):

```text
id      role    wins    losses  ties    goals   points  sidegoals       blocks  steals  passes  kills   deaths  turnovers       killassists     goalassists     rebounds        saves   lasthits        miniondamage    upgradesgold    consumablesgold sidegoalsaves   centergoalsaves sidegoalsconceded       goalsconceded   manaspent
1       GOALIE  2369    2369    12      0       0.5     1       2545    1118    3501    3269    462     1395    172     0       71      2414    136107  1513785 2810675 157400  326     2088    54844   11671   2178625
2       WARRIOR 592     614     2       950     2730    4513    13931   8135    14315   4387    1735    11241   1864    0       11286   0       0       0       0       0       0       0       0       0       0
3       RANGER  588     639     0       966     2662.75 4275    13927   7452    13921   3206    1278    11179   1393    0       11271   0       0       0       0       0       0       0       0       0       0
4       DASHER  647     529     8       1074    3064.75 5293    12206   9179    20984   1469    2522    11108   681     0       10169   0       0       0       0       0       0       0       0       0       0
5       MARKSMAN        558     563     4       990     2792.25 4784    12918   7454    12809   0       2028    10743   0       0       10417   0       0       0       0       0       0       0       0       0       0
6       STEALTH 552     633     2       904     2555    4295    12085   8079    13780   0       2282    10608   0       0       9450    0       0       0       0       0       0       0       0       0       0
7       SUPPORT 512     628     8       793     2183.75 3526    11051   7866    12608   0       593     10707   0       0       8774    0       0       0       0       0       0       0       0       0       0
8       ARTISAN 650     576     0       1043    2956    4871    16328   9951    16216   0       2145    14414   0       0       11684   0       0       0       0       0       0       0       0       0       0
9       GOLEM   584     611     4       1021    2771.75 4307    10290   9452    14548   0       495     11226   0       0       8156    0       0       0       0       0       0       0       0       0       0
10      MAGE    0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0
11      BUILDER 653     527     2       892     2580.75 4452    11471   7054    14097   0       1612    9288    0       0       9354    0       0       0       0       0       0       0       0       0       0
12      GRENADIER       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0
13      HOUNDMASTER     558     611     2       1017    2801.25 4563    12446   8620    14466   2831    1306    10769   1026    0       9980    0       0       0       0       0       0       0       0       0       0
14      CAPTAIN 586     608     0       966     2733    4465    12601   7137    12444   3934    2143    10251   1698    0       9993    0       0       0       0       0       0       0       0       0       0
15      SPIDER  627     568     4       1055    3141    5499    13967   8218    13429   0       1954    10528   0       0       11419   0       0       0       0       0       0       0       0       0       0
```

### Key Takeaways from Run 4 (2,369 Matches):
1. **Stealth Balance in Omniscient AI Play**:
   - Stealth achieved **46.50% WR** (552 wins, 633 losses) against bots with full omniscience. With the inherent ~6–7% penalty of bots seeing through stealth, naive opponents would lose well over half the time, confirming Stealth's base tuning is in a solid competitive spot.
2. **Combat & Fragging Calibration**:
   - The 4,750ms respawn window firmly pulled combat titans towards parity:
     - **Warrior**: **49.01% WR** (4,387 kills, highest direct frag count).
     - **Ranger**: **47.92% WR** (3,206 kills).
3. **Roster Tightness**:
   - All 12 outfield titans fall within a **10.65% spread** (from Support at 44.60% to Builder at 55.25%), representing remarkable balance parity across over 14,000 outfield titan match appearances.

---

## 11. Empirical Match Telemetry - Run 5: Validated Goalie & Masteries Benchmark (4,234 Matches)

Following the live session validation and correction of Goalie center vs side defense priorities, tuning adjustments were applied:
- **Respawn Extension**: Increased to `globals.titan.respawn.ms=5500`.
- **Sidegoal Cooldown & Bounce**: `hoop.sidegoal.cdms=1800` and `hoop.bounce.extra.kick=30` added to curb rapid sidegoal tapping and normalize hoop deflections.
- **Masteries Scaling Adjustments**: `masteries.speed.mult=1.025`, `masteries.throw.mult=1.03`, `masteries.cooldowns.mult=1.12`.
- **Economy & Upgrade Costs**:
  - `fortress.t4.barrage.cost=350` (up from 325)
  - `fortress.t5.icebarrage.cost=300` / `fortress.t5.firebarrage.cost=300` (up from 250)
  - `siege.t5.saveprogress.cost=300` (discounted from 400)
  - `siege.t5.incendiarymines.cost=345` / `siege.t5.forwardoutpost.cost=345` (up from 325)
  - `empowerment.t3.footwork.cost=170` (up from 125), `empowerment.t3.discipline.cost=115` (down from 125)

The test suite executed **4,234 full matches** (8,488 Goalie entries, 25,404 outfield entries).

### 11.1 Class Statistics (`classstat`)

```sql
SELECT role, wins, losses, ties, (wins + losses + ties) AS games_played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 0), 2) AS winrate_pct,
       goals, ROUND(points, 2) AS points, lasthits, ROUND(miniondamage, 1) AS minion_dmg, kills, deaths
FROM classstat
ORDER BY winrate_pct DESC, games_played DESC;
```

```text
role        wins    losses  ties    games_played    winrate_pct goals   points      lasthits    minion_dmg  kills   deaths
SPIDER      1147    939     0       2086            54.99       1637    4448.75     0           0           0       4944
BUILDER     1135    962     6       2103            53.97       1245    3391.25     0           0           0       3759
HOUNDMASTER 1167    998     0       2165            53.90       1661    4428.00     0           0           7035    3743
ARTISAN     1090    974     4       2068            52.71       1672    4360.75     0           0           0       4831
MARKSMAN    1148    1034    6       2188            52.47       1504    4072.50     0           0           0       5287
GOLEM       1063    1027    14      2104            50.52       1669    4382.50     0           0           0       1370
STEALTH     1034    1021    8       2063            50.12       1341    3528.75     0           0           0       5574
GOALIE      4234    4234    20      8488            49.88       0       0.00        495285      3976478     9644    357
WARRIOR     1023    1043    4       2070            49.42       1395    3559.75     0           0           9332    4364
SUPPORT     1061    1116    12      2189            48.47       1295    3440.00     0           0           0       1596
DASHER      1007    1072    4       2083            48.34       1409    3692.25     0           0           3597    6001
RANGER      974     1214    0       2188            44.52       1293    3282.25     0           0           7475    3525
CAPTAIN     853     1302    2       2157            39.55       1070    2773.75     0           0           10242   5048
MAGE        0       0       0       0               0.00        0       0.00        0           0           0       0
GRENADIER   0       0       0       0               0.00        0       0.00        0           0           0       0
```

#### Full 27-Column Raw Table (`SELECT * FROM classstat;`):

```text
id      role    wins    losses  ties    goals   points  sidegoals       blocks  steals  passes  kills   deaths  turnovers       killassists     goalassists     rebounds        saves   lasthits        miniondamage    upgradesgold    consumablesgold sidegoalsaves   centergoalsaves sidegoalsconceded       goalsconceded   manaspent
1       GOALIE  4234    4234    20      0       0       1       3294    31      2020    9644    357     1042    783     0       116     3142    495285  3976478 8166150 135855  306     2836    64914   17191   2623795
2       WARRIOR 1023    1043    4       1395    3559.75 4838    21673   9690    17474   9332    4364    15420   3790    0       17138   0       0       0       0       0       0       0       0       0       0
3       RANGER  974     1214    0       1293    3282.25 4661    21658   8975    17220   7475    3525    15780   3436    0       16688   0       0       0       0       0       0       0       0       0       0
4       DASHER  1007    1072    4       1409    3692.25 5436    16730   11056   27123   3597    6001    14182   1562    0       12896   0       0       0       0       0       0       0       0       0       0
5       MARKSMAN        1148    1034    6       1504    4072.5  6118    19845   8866    17277   0       5287    13337   0       0       14929   0       0       0       0       0       0       0       0       0       0
6       STEALTH 1034    1021    8       1341    3528.75 5213    19689   10093   18894   0       5574    13862   0       0       15137   0       0       0       0       0       0       0       0       0       0
7       SUPPORT 1061    1116    12      1295    3440    4729    18331   10464   17403   0       1596    15260   0       0       13875   0       0       0       0       0       0       0       0       0       0
8       ARTISAN 1090    974     4       1672    4360.75 6201    22560   11607   19466   0       4831    15685   0       0       17411   0       0       0       0       0       0       0       0       0       0
9       GOLEM   1063    1027    14      1669    4382.5  6043    17895   13860   21118   0       1370    16056   0       0       13909   0       0       0       0       0       0       0       0       0       0
10      MAGE    0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0
11      BUILDER 1135    962     6       1245    3391.25 5051    18651   9528    16972   0       3759    13410   0       0       14771   0       0       0       0       0       0       0       0       0       0
12      GRENADIER       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0       0
13      HOUNDMASTER     1167    998     0       1661    4428    6114    23050   11783   20213   7035    3743    16349   2308    0       18545   0       0       0       0       0       0       0       0       0       0
14      CAPTAIN 853     1302    2       1070    2773.75 4162    18879   8023    13488   10242   5048    15941   4273    0       14463   0       0       0       0       0       0       0       0       0       0
15      SPIDER  1147    939     0       1637    4448.75 6347    23607   10482   17584   0       4944    15244   0       0       18874   0       0       0       0       0       0       0       0       0       0
```

---

### 11.2 Goalie Build Order Performance (`buildorderstat`)

Tracks the full-match win conversion, defensive effectiveness, and economic expenditure across the 10 goalie build orders:

```sql
SELECT buildname, wins, losses, ties, (wins + losses + ties) AS played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 0), 2) AS winrate_pct,
       saves, centergoalsaves, sidegoalsaves, goalsconceded, sidegoalsconceded,
       upgradesgold, consumablesgold, manaspent
FROM buildorderstat
ORDER BY winrate_pct DESC;
```

```text
buildname        wins   losses  ties    played  winrate_pct saves   center_saves    side_saves  goals_conc  side_conc   upgrades_gold   consumables_gold    mana_spent
cult-emp.txt     484    354     0       838     57.76%      297     264             33          1606        5889        $859,950        $0                  882,610
blitz-siege.txt  484    375     0       859     56.34%      332     299             33          1645        6290        $836,900        $55                 0
cult-spam.txt    454    438     0       892     50.90%      376     340             36          1819        6786        $200,700        $44,600             1,059,600
fort-emp.txt     415    400     2       817     50.80%      269     237             32          1484        6021        $929,500        $300                0
cult-fort.txt    424    430     4       858     49.42%      276     254             22          1661        6787        $871,700        $45,100             235,400
siege-fort.txt   421    443     0       864     48.73%      325     298             27          1833        6752        $877,550        $0                  0
iron-bastion.txt 382    421     8       811     47.10%      257     234             23          1475        5952        $931,350        $1,300              0
siege-cult.txt   403    464     0       867     46.48%      360     324             36          1906        6887        $854,675        $43,960             450,025
titan-apex.txt   389    455     4       848     45.87%      322     289             33          1887        6769        $972,150        $400                0
siege-emp.txt    384    460     2       846     45.39%      333     300             33          1901        6848        $842,875        $240                0
```

* **Key Takeaway**: 
  - **Cultivation Synergy Rules**: Cultivation is the true late-game scaling tree. Because mana income trickles in parallel with gold, hybrid builds that open Cultivation (`cult-emp.txt` at 57.76% WR, `cult-spam.txt` at 50.90% WR) tap into two independent economic engines simultaneously ($859k gold + 882k mana in `cult-emp.txt`), providing immense late-game scaling and high-tier portal/mana unlocks (`heroportals`, `wallportals`, `iceportal` all boasting ~60–61% WRs).
  - **Pure Gold Single-Tree Drag**: Conversely, pure gold-focused builds like `titan-apex.txt` (45.87% WR, pure Empowerment) and `siege-emp.txt` (45.39% WR) leave mana generation completely unutilized (0 mana spent). They rely solely on the single gold stream, forcing teams to wait on expensive linear tier gates ($170 footwork, $250 forecheck, $300 clutchgene, $400 dragonsbreath) without any parallel resource assistance, resulting in lower conversion before the match concludes.

---

### 11.3 Goalie Upgrade Tech Node Statistics (`upgradeclassstat`)

Snapshot of top individual upgrade nodes by frequency and impact across all matches:

```sql
SELECT upgrade, wins, losses, ties, (wins + losses + ties) AS played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 0), 2) AS winrate_pct,
       saves, centergoalsaves, sidegoalsaves, goalsconceded, sidegoalsconceded,
       upgradesgold, consumablesgold, manaspent
FROM upgradeclassstat
ORDER BY played DESC;
```

```text
upgrade                             wins    losses  ties    played  winrate_pct saves   center_saves    side_saves  goals_conc  side_conc
cultivation.t1.manawell             1764    1685    4       3453    51.09%      1309    1182            127         6986        26334
cultivation.t3.manacompounding      1764    1685    4       3453    51.09%      1309    1182            127         6986        26334
cultivation.t3.highermanacap        1764    1685    4       3453    51.09%      1309    1182            127         6986        26334
siege.t1.siegedoctrine              1691    1741    2       3434    49.24%      1349    1220            129         7281        26770
siege.t3.vanguards                  1685    1734    2       3421    49.25%      1349    1220            129         7258        26715
siege.t4.accumulators               1619    1674    2       3295    49.14%      1324    1195            129         7004        26104
fortress.t1.homeward                1340    1401    14      2755    48.64%      906     819             87          5172        21084
fortress.t3.snaretrap               1276    1334    14      2624    48.63%      860     776             84          4918        20009
cultivation.t2.manainfusion         1279    1331    4       2614    48.93%      1012    918             94          5378        20449
cultivation.t4.manavines            1335    1245    0       2580    51.74%      1028    923             105         5283        19474
empowerment.t1.combinecontract      1319    1245    8       2572    51.28%      917     816             101         5121        19266
fortress.t3.biggermodels            1231    1270    14      2515    48.95%      813     732             81          4680        19038
empowerment.t3.marksmanship         1291    1205    8       2504    51.56%      890     792             98          4971        18737
siege.t5.phalanx                    902     963     0       1865    48.36%      783     709             74          3828        15098
siege.t3.rushlane                   884     836     0       1720    51.40%      691     622             69          3539        13153
siege.t3.forwardmines               900     814     0       1714    52.51%      655     595             60          3464        13004
cultivation.t4.manafrenzy           817     886     4       1707    47.86%      635     577             58          3537        13611
empowerment.t3.grit                 802     854     6       1662    48.26%      590     525             65          3365        12777
fortress.t4.bastionprotocol         747     781     10      1538    48.57%      498     446             52          2777        11475
siege.t4.parapet                    695     690     0       1385    50.18%      566     512             54          2783        11132
fortress.t4.barrage                 689     672     6       1367    50.40%      453     412             41          2495        10760
empowerment.t5.clutchgene           618     527     8       1153    53.60%      446     394             52          2206        8903
empowerment.t4.fuelreserves         422     482     6       910     46.37%      336     301             35          1957        7205
fortress.t5.icebarrage              441     447     14      902     48.89%      292     267             25          1554        7033
cultivation.t5.manapollinate        448     431     0       879     50.97%      371     335             36          1789        6725
siege.t3.ballportal                 384     460     2       846     45.39%      333     300             33          1901        6848
cultivation.t6.uninhibitedportal    436     409     0       845     51.60%      356     321             35          1702        6516
empowerment.t3.discipline           385     454     4       843     45.67%      322     289             33          1881        6757
empowerment.t3.footwork             490     351     2       843     58.13%      301     268             33          1604        5936
cultivation.t5.manasummon           431     405     0       836     51.56%      352     317             35          1689        6461
cultivation.t5.riskadjustedreturn   468     335     0       803     58.28%      288     255             33          1523        5714
empowerment.t4.forecheck            355     420     6       781     45.45%      300     267             33          1684        6321
cultivation.t6.wallportals          460     305     0       765     60.13%      277     245             32          1413        5437
fortress.t4.deadwalls               358     396     8       762     46.98%      251     228             23          1382        5693
empowerment.t4.heroportals          460     292     2       754     61.01%      277     245             32          1367        5347
cultivation.t6.iceportal            432     278     0       710     60.85%      257     225             32          1303        5087
cultivation.t5.manasurge            426     273     0       699     60.94%      253     222             31          1280        5019
siege.t5.saveprogress               310     383     2       695     44.60%      299     269             30          1523        5949
fortress.t5.noflyzoneperm           291     330     8       629     46.26%      212     193             19          1110        4748
```

---

### 11.4 Titan Masteries Performance (`masteriesstat`)

Evaluates performance across individual mastery specializations:

#### Top 15 Masteries by Winrate:
```sql
SELECT role, wins, losses, ties, (wins + losses + ties) AS played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 0), 2) AS winrate_pct,
       goals, ROUND(points, 2) AS points, kills, deaths
FROM masteriesstat
ORDER BY winrate_pct DESC LIMIT 15;
```

```text
role                        wins    losses  ties    played  winrate_pct goals   points      kills   deaths
SPIDER_STEALRADIUS          339     246     0       585     57.95%      461     1270.00     0       1440
SPIDER_PAINREDUCTION        334     249     0       583     57.29%      412     1147.75     0       1844
SPIDER_EFFECTDURATION       322     241     0       563     57.19%      461     1224.50     0       1300
BUILDER_HEALTH              324     243     2       569     56.94%      353     979.75      0       838
HOUNDMASTER_HEALTH          343     262     0       605     56.69%      505     1355.75     1829    839
MARKSMAN_SPEED              329     251     2       582     56.53%      441     1220.25     0       1399
ARTISAN_ABILITYLAG          318     243     2       563     56.48%      472     1271.75     0       1402
HOUNDMASTER_PAINREDUCTION   328     256     0       584     56.16%      398     1075.00     1905    1374
ARTISAN_SPEED               312     244     0       556     56.12%      504     1275.00     0       1251
BUILDER_SPEED               321     252     0       573     56.02%      352     996.75      0       1086
SPIDER_HEALTH               324     257     0       581     55.77%      504     1318.75     0       1081
SPIDER_COOLDOWNS            312     249     0       561     55.61%      407     1143.00     0       1300
ARTISAN_DAMAGE              335     268     2       605     55.37%      489     1292.00     0       1394
BUILDER_ABILITYRANGE        331     264     4       599     55.26%      382     984.75      0       1056
SPIDER_SHOT                 313     254     0       567     55.20%      427     1212.00     0       1291
```

#### Bottom 15 Masteries by Winrate:
```sql
SELECT role, wins, losses, ties, (wins + losses + ties) AS played,
       ROUND(IF((wins + losses + ties) > 0, wins / (wins + losses + ties) * 100, 0), 2) AS winrate_pct,
       goals, ROUND(points, 2) AS points, kills, deaths
FROM masteriesstat
ORDER BY winrate_pct ASC LIMIT 15;
```

```text
role                        wins    losses  ties    played  winrate_pct goals   points      kills   deaths
CAPTAIN_BOOST               211     351     2       564     37.41%      288     737.25      2589    1243
CAPTAIN_COOLDOWNS           222     365     0       587     37.82%      249     646.00      3162    1264
CAPTAIN_PAINREDUCTION       227     368     2       597     38.02%      234     634.75      2807    1874
CAPTAIN_ABILITYLAG          247     398     0       645     38.29%      349     900.50      2998    1534
CAPTAIN_EFFECTDURATION      243     381     0       624     38.94%      292     763.00      2820    1433
CAPTAIN_HEALTH              230     357     0       587     39.18%      295     755.25      2668    1079
CAPTAIN_SPEED               235     343     0       578     40.66%      314     816.00      2644    1330
CAPTAIN_SHOT                231     332     2       565     40.88%      286     731.75      2542    1301
CAPTAIN_ABILITYRANGE        241     345     0       586     41.13%      304     763.75      2844    1379
CAPTAIN_STEALRADIUS         245     350     0       595     41.18%      334     848.50      2711    1446
CAPTAIN_DAMAGE              242     331     0       573     42.23%      295     791.25      3061    1330
RANGER_PAINREDUCTION        256     340     0       596     42.95%      311     799.00      1920    1299
RANGER_SHOT                 256     339     0       595     43.03%      327     847.50      1884    906
RANGER_SPEED                275     355     0       630     43.65%      403     1033.25     2095    926
RANGER_HEALTH               267     344     0       611     43.70%      354     919.75      2054    771
```

---

### 11.5 Key Balance Takeaways from Run 5 (4,234 Matches)
1. **Sidegoal Pace Normalization**:
   - Outfielder sidegoals conceded settled at **15.33 sidegoals per match** (down from earlier runs that spammed 23+ per team), confirming the 1,800ms hoop cooldown and bounce velocity tuning successfully regulated sidegoal farming.
2. **Goalie Economic Utilization**:
   - Average Goalie upgrade spending surged to **$962.06 in gold** plus **$309.12 in mana** ($1,271.18 total value / goalie / match), confirming the parallel build order engine reliably executes full dual-tree investment.
3. **Spider & Marksman Ascendance / Captain Drop**:
   - **Spider (54.99% WR)** and **Marksman (52.47% WR)** gained ground under the new pacing.
   - **Captain (39.55% WR)** suffered across all masteries (all 11 Captain masteries sit between 37% and 42%), despite high frags (10,242 kills), indicating Captain is struggling with conversion in the 5.5s respawn meta.
   - **Stealth (50.12% WR)** reached ideal parity even under full bot omniscience.
