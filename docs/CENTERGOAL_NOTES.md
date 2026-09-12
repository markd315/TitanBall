# Center Goal Scoring & Goalie Mixup Mechanics

## 1. Executive Summary & Design Overview

With the lob subphase hitbox exemption (`contactExemptBall()`), an attacker in possession stationed at the appropriate range without outfield defenders in the lane creates an unreactable **read-and-react spatial dilemma** for the opposing Guardian/Goalie.

Because the Goalie's static intercept collider ($90 \times 50\text{px}$) cannot physically straddle both the front cutoff point and the back rim of the center hoop ($100\text{px}$ scoring aperture) simultaneously:
1. **Front-Cutoff Stance (Stepped Up)**: Denies line-drive snipes, but concedes an overhead lob arcing cleanly through the airborne blue subphase (frames 3–8) into the back of the net.
2. **Back-Pocket Stance (Deep in Net)**: Contests the lob landing drop, but forfeits the front net rim and lateral angles to direct line-drive shots.
3. **Ability 1 (`BLOCK`)**: The Goalie's sole universal counter. Expands the collider to $1.5\times$ ($135\text{px}$) and overrides `contactExemptBall()`, pulling airborne lobs out of mid-air. Once consumed, the goalie enters cooldown and becomes vulnerable to the triple threat.
4. **Triple Threat & Angle of Attack (AoA)**: Direct Shot vs. Overhead Lob vs. Wing Pass. Attackers can pump-fake or position to bait an early panic `BLOCK`, cycle possession for 2.4s, or pass laterally to shift the Angle of Attack.

---

## 2. Dimensional & Hitbox Reference

All coordinates, bounds, and constants are parameterized in [`res/game.cfg`](../res/game.cfg) and [`src/gameserver/models/Game.java`](../src/gameserver/models/Game.java).

### A. Center Goal Hoop (`hiGoal`)
* **Hoop Dimensions**: Width $W_G = 70\text{px}$, Height $H_G = 84\text{px}$.
* **Hoop Ellipse**: Radii $R_x = 35\text{px}$, $R_y = 42\text{px}$.
* **Ball Dimensions**: $30 \times 30\text{px}$ (Radius $r_b = 15\text{px}$, `centerDist` = $15\text{px}$).
* **Effective Scoring Footprint**:
  Collision triggers when the ball bounding box intersects the goal bounding box ($70 + 30 = \mathbf{100\text{px}}$ effective scoring span along the X-axis).
  * **Away Center Goal** (`AWAY_HI_X = 1786`, `goal.hi.y = 583`):
    * Front rim entry: $X = 1786$ (ball center at $X = 1771$ touches front rim).
    * Net center: $X = 1821$.
    * Back pocket: $X = 1856$ (ball center at $X = 1871$ touches back rim).
  * **Home Center Goal** (`HOME_HI_X = 256`, `goal.hi.y = 583`):
    * Front rim entry: $X = 326$ (facing right towards attackers).
    * Net center: $X = 291$.
    * Back pocket: $X = 256$.

### B. Goalie Crease & Movement Constraints
* **Crease Box** (`goalie.box.xa = 1736`, `goalie.box.w = 88`):
  * Away Goalie $X$ mobility range: $X \in [1736, 1824]$ ($\mathbf{88\text{px}}$ lateral travel freedom).
  * **High Crease (Stepped Up)**: Advanced up to $50\text{px}$ in front of the goal line ($X = 1736$).
  * **Low Crease (Deep in Net)**: Retreated up to $38\text{px}$ behind the front rim ($X = 1824$, slightly past center $1821$).

### C. Goalie Hitbox Dimensions
* **Normal Intercept Collider**:
  * Width $W_I = \mathbf{90\text{px}}$, Height $H_I = \mathbf{50\text{px}}$ (centered horizontally and vertically on Goalie sprite).
* **Solid Body Blocker Hitbox**:
  * Width $W_S = \mathbf{50\text{px}}$, Height $H_S = \mathbf{30\text{px}}$ (centered horizontally, top-aligned).
* **Ability 1 (`BLOCK`) Active**:
  * $1.5\times$ scale multiplier: Width $W_B = \mathbf{135\text{px}}$, Height $H_B = \mathbf{75\text{px}}$.

---

## 3. Lob Kinematics & Subphase Trajectory Math

From `GameEngine.java`:
$$\text{Total Distance } D = 230 \times \text{throwPower}$$

The distance traveled during tick $f \in [1, 19]$ is:
$$\Delta d(f) = D \times \frac{20 - f}{190}$$

Cumulative distance traveled through frame $F$ is $d(F) = \sum_{f=1}^{F} \Delta d(f)$:

| Phase | Frames | Cumulative $\%$ of $D$ | Ball State & Physics Rules |
| :--- | :---: | :---: | :--- |
| **Ascent / Launch** | $1 - 2$ | $0\% \to 19.5\%$ | Ground-level / Blockable by defenders & obstacles |
| **Airborne Lob Subphase** | **$3 - 8$** | **$19.5\% \to 65.3\%$** | **High Air / Collision-Exempt (`contactExemptBall()`)** |
| **Descent / Landing** | $9 - 20$ | $65.3\% \to 100\%$ | Descending / Catches, Scores & Hoop Bounces Active |

* **Airborne Blue Zone Span**: $\frac{124 - 37}{190} = \mathbf{45.79\%}$ of total throw distance.
* **Landing Yellow Zone Span**: $\frac{190 - 124}{190} = \mathbf{34.74\%}$ of total throw distance.

---

## 4. Class Throw Distance Matrix

| Titan Class | Throw Power (`shoot`) | Total Lob Dist ($D_{\text{lob}}$) | Airborne Subphase ($19.5\% \to 65.3\%$) | Landing Zone ($65.3\% \to 100\%$) | Total Shot Dist ($D_{\text{shot}}$) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Marksman** | 1.62 | 372.6px | 72.5px – 243.2px | 243.2px – 372.6px | 511.9px |
| **Goalie / Golem** | 1.55 | 356.5px | 69.4px – 232.7px | 232.7px – 356.5px | 489.8px |
| **Artisan** | 1.45 | 333.5px | 64.9px – 217.6px | 217.6px – 333.5px | 458.2px |
| **Stealth** | 1.43 | 328.9px | 64.0px – 214.6px | 214.6px – 328.9px | 451.9px |
| **Dasher** | 1.41 | 324.3px | 63.1px – 211.6px | 211.6px – 324.3px | 445.6px |
| **Captain** | 1.37 | 315.1px | 61.3px – 205.6px | 205.6px – 315.1px | 432.9px |
| **Grenadier** | 1.34 | 308.2px | 60.0px – 201.1px | 201.1px – 308.2px | 423.4px |
| **Builder** | 1.32 | 303.6px | 59.1px – 198.1px | 198.1px – 303.6px | 417.1px |
| **Spider / Mage** | 1.27 | 292.1px | 56.9px – 190.6px | 190.6px – 292.1px | 401.3px |
| **Houndmaster** | 1.22 | 280.6px | 54.6px – 183.1px | 183.1px – 280.6px | 385.5px |
| **Ranger** | 1.17 | 269.1px | 52.4px – 175.6px | 175.6px – 269.1px | 370.0px |
| **Support** | 1.14 | 262.2px | 51.0px – 171.1px | 171.1px – 262.2px | 360.2px |
| **Warrior** | 1.12 | 257.6px | 50.1px – 168.1px | 168.1px – 257.6px | 353.9px |

---

## 5. Mathematical Proof of the Coverage Dilemma

Assume an attacker (e.g., Dasher, $D_{\text{lob}} = 324\text{px}$) positioned at $X = 1530$, facing the Away Center Goal ($X \in [1786, 1856]$):

```
Attacker                      Goalie (Stepped Up)              Goal Hoop
(X = 1530)                        (X = 1750)                [1786 ---- 1856]
   |                                 |                             |
   o========= Blue Lob Arc =========[==============================] 
 (0px)      (Fly: 63px - 212px)      |       (Landing: 212px - 324px)
                                  Intercept                Scores in
                                  Collider:               Back Pocket!
                                [1705 - 1795]            [1795 - 1856]
```

### Scenario A: Goalie steps up to contest the front rim ($X \approx 1750$)
* **Direct Shot**: Ray collides with Goalie's intercept collider ($X \in [1705, 1795]$) and solid blocker ($X \in [1725, 1775]$) prior to reaching the front rim at $X = 1786$. **Shot is saved.**
* **Overhead Lob**:
  * Distance to Goalie: $1750 - 1530 = 220\text{px}$.
  * At $X \in [1705, 1795]$ (where Goalie stands), the lob is at distance $175\text{px} \to 265\text{px}$.
  * The ball is in frames $3 - 8$ (up to $212\text{px}$) and transitions into landing at $X \approx 1742$.
  * The ball enters the scoring window ($X \ge 1786$) and lands in the back pocket ($X \in [1795, 1856]$).
  * Goalie's rear collider ends at $X = 1795$, leaving $61\text{px}$ of the net back pocket completely uncovered.
  * **Result**: **Uncontested Lob Goal**.

### Scenario B: Goalie sags deep into the net to intercept the lob drop ($X \approx 1824$)
* **Overhead Lob**: Intercept collider shifts to $X \in [1779, 1869]$, occupying the drop zone inside the hoop ($X \in [1786, 1856]$). **Lob is caught.**
* **Direct Shot**:
  * Front of net is at $X = 1786$.
  * Goalie's solid blocker box ($50\text{px}$) is centered at $X = 1824$ ($X \in [1799, 1849]$).
  * Goalie has conceded **$28\text{px}$ of open net aperture at the front rim** ($X \in [1771, 1799]$).
  * Forward angle-cutting is zero, opening up top and bottom corner snipes.
  * **Result**: **Concedes Direct Line-Drive Goal**.

### Scenario C: Goalie burns Ability 1 (`BLOCK`)
* Intercept collider expands to $W_B = \mathbf{135\text{px}}$, covering $X \in [1712.5, 1847.5]$ when positioned at $X = 1780$.
* Covers the front rim, the net interior, and $73\text{px}$ of the approach simultaneously.
* Overrides `contactExemptBall()`, intercepting airborne lobs during frames $3 - 8$.
* **Result**: **Both options denied**, but consumes a critical 2.4s defensive resource and enters cooldown.
