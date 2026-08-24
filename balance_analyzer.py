#!/usr/bin/env python3
"""
TitanBall Balance Analyzer
==========================
Standalone Python script to analyze Titan class balance, relative strength,
stat percentiles, and cooldown-discounted ability rankings.

All stats, cooldowns, and boost parameters are loaded dynamically from res/game.cfg.
You can sort ABILITY_ORDER and adjust STAT_WEIGHTS / ABILITY_WEIGHTS directly
in the configuration section below.

Usage:
    python balance_analyzer.py               # Run analysis using in-script sorting & weights
    python balance_analyzer.py --interactive # Interactive CLI to re-rank, tweak weights & stats
    python balance_analyzer.py --no-boost    # Compare unboosted raw base movement speed
    python balance_analyzer.py --json        # Output raw results as JSON
"""

import os
import sys
import json
import argparse
from typing import Dict, List, Tuple, Any, Optional

# ==============================================================================
# 1. USER CONFIGURATION: ABILITY POWER SORTING (Weakest [1] -> Strongest [24])
# ==============================================================================
# Sort this list from LEAST powerful (top, Rank 1) to MOST powerful (bottom, Rank 24)
# in terms of raw un-discounted impact.
ABILITY_ORDER = [
    "dasher_flare",           # Flare [DASHER] (W/R)
    "marksman_slow",          # Frost Shot [MARKSMAN] (Q/E)
    "golem_shield",           # Barrier Shield [GOLEM] (Q/E)
    "artisan_suck",           # Ball Vacuum [ARTISAN] (Q/E)
    "builder_trap",           # Snare Trap [BUILDER] (Q/E)
    "marksman_shoot",         # Charge Shot [MARKSMAN] (W/R)
    "mage_portal",            # Warp Portal [MAGE] (Q/E)
    "grenadier_flashbang",    # Flashbang [GRENADIER] (Q/E)
    "ranger_kick",            # Sweeping Kick [RANGER] (W/R)
    "ranger_arrow",           # Precision Arrow [RANGER] (Q/E)
    "warrior_slash",          # Whirlwind Slash [WARRIOR] (Q/E)
    "support_heal",           # Healing Surge [SUPPORT] (W/R)
    "dasher_hide",            # Cover Ball [DASHER] (Q/E)
    "builder_wall",           # Barrier Wall [BUILDER] (W/R)
    "houndmaster_cage",       # Deploy Kennel [HOUNDMASTER] (Q/E)
    "houndmaster_wolf",       # Unleash Pack [HOUNDMASTER] (W/R)
    "grenadier_molotov",      # Molotov [GRENADIER] (W/R)
    "artisan_bportal",        # Ball Portal [ARTISAN] (W/R)
    "mage_ignite",            # Ignite [MAGE] (W/R)
    "stealth_flash",          # Shadow Blink [STEALTH] (W/R)
    "warrior_flash",          # Flash Dash [WARRIOR] (W/R)
    "support_stun",           # Shock Stun [SUPPORT] (Q/E)
    "stealth_hide",           # Vanish [STEALTH] (Q/E)
    "golem_scatter",          # Shockwave Slam [GOLEM] (W/R)
]

# ==============================================================================
# 2. USER CONFIGURATION: STAT WEIGHTS (1 to 100)
# ==============================================================================
# Assign any weight from 1 to 100 for each stat. Equal weights = equal 1/n weighting.
STAT_WEIGHTS = {
    "hp": 20,
    "speed": 70,
    "shoot": 50,
    "stealrad": 35,
}

# ==============================================================================
# 3. USER CONFIGURATION: ABILITY WEIGHTS (1 to 100)
# ==============================================================================
# Assign any weight from 1 to 100 for individual abilities. Default is 50.
ABILITY_WEIGHTS = {
    # WARRIOR
    "warrior_slash": 50,
    "warrior_flash": 50,
    # RANGER
    "ranger_arrow": 50,
    "ranger_kick": 50,
    # MAGE
    "mage_portal": 50,
    "mage_ignite": 50,
    # MARKSMAN
    "marksman_slow": 50,
    "marksman_shoot": 50,
    # DASHER
    "dasher_hide": 50,
    "dasher_flare": 50,
    # GOLEM
    "golem_shield": 50,
    "golem_scatter": 50,
    # BUILDER
    "builder_trap": 50,
    "builder_wall": 50,
    # SUPPORT
    "support_stun": 50,
    "support_heal": 50,
    # ARTISAN
    "artisan_suck": 50,
    "artisan_bportal": 50,
    # STEALTH
    "stealth_hide": 50,
    "stealth_flash": 50,
    # GRENADIER
    "grenadier_flashbang": 50,
    "grenadier_molotov": 50,
    # HOUNDMASTER
    "houndmaster_cage": 50,
    "houndmaster_wolf": 50,
}

# Registry linking abilities to classes, slots, and game.cfg cooldown keys
ABILITIES_REGISTRY = [
    {"id": "warrior_slash", "name": "Whirlwind Slash", "class": "WARRIOR", "slot": "Q/E", "cd_key": "titan.slash.cdms", "default_cd": 4.5},
    {"id": "warrior_flash", "name": "Flash Dash", "class": "WARRIOR", "slot": "W/R", "cd_key": "titan.flash.warrior.cds", "default_cd": 23.0},
    {"id": "ranger_arrow", "name": "Precision Arrow", "class": "RANGER", "slot": "Q/E", "cd_key": "titan.arrow.cdms", "default_cd": 4.0},
    {"id": "ranger_kick", "name": "Sweeping Kick", "class": "RANGER", "slot": "W/R", "cd_key": "titan.kick.cdms", "default_cd": 12.0},
    {"id": "mage_portal", "name": "Warp Portal", "class": "MAGE", "slot": "Q/E", "cd_key": "titan.portal.cdms", "default_cd": 5.5},
    {"id": "mage_ignite", "name": "Ignite", "class": "MAGE", "slot": "W/R", "cd_key": "titan.ignite.cds", "default_cd": 20.0},
    {"id": "marksman_slow", "name": "Frost Shot", "class": "MARKSMAN", "slot": "Q/E", "cd_key": "titan.slow.cdms", "default_cd": 15.0},
    {"id": "marksman_shoot", "name": "Charge Shot", "class": "MARKSMAN", "slot": "W/R", "cd_key": "titan.shoot.cdms", "default_cd": 9.0},
    {"id": "dasher_hide", "name": "Cover Ball", "class": "DASHER", "slot": "Q/E", "cd_key": "titan.hide.cdms", "default_cd": 9.0},
    {"id": "dasher_flare", "name": "Flare", "class": "DASHER", "slot": "W/R", "cd_key": "titan.flare.cds", "default_cd": 5.0},
    {"id": "golem_shield", "name": "Barrier Shield", "class": "GOLEM", "slot": "Q/E", "cd_key": "titan.shield.cdms", "default_cd": 18.0},
    {"id": "golem_scatter", "name": "Shockwave Slam", "class": "GOLEM", "slot": "W/R", "cd_key": "titan.scatter.cdms", "default_cd": 12.0},
    {"id": "builder_trap", "name": "Snare Trap", "class": "BUILDER", "slot": "Q/E", "cd_key": "titan.trap.cdms", "default_cd": 15.0},
    {"id": "builder_wall", "name": "Barrier Wall", "class": "BUILDER", "slot": "W/R", "cd_key": "titan.wall.cdms", "default_cd": 3.5},
    {"id": "support_stun", "name": "Shock Stun", "class": "SUPPORT", "slot": "Q/E", "cd_key": "titan.stun.cdms", "default_cd": 7.0},
    {"id": "support_heal", "name": "Healing Surge", "class": "SUPPORT", "slot": "W/R", "cd_key": "titan.heal.cdms", "default_cd": 8.0},
    {"id": "artisan_suck", "name": "Ball Vacuum", "class": "ARTISAN", "slot": "Q/E", "cd_key": "titan.suck.cdms", "default_cd": 30.0},
    {"id": "artisan_bportal", "name": "Ball Portal", "class": "ARTISAN", "slot": "W/R", "cd_key": "titan.bportal.cdms", "default_cd": 7.0},
    {"id": "stealth_hide", "name": "Vanish", "class": "STEALTH", "slot": "Q/E", "cd_key": "titan.stealth.cdms", "default_cd": 15.0},
    {"id": "stealth_flash", "name": "Shadow Blink", "class": "STEALTH", "slot": "W/R", "cd_key": "titan.flash.stealth.cds", "default_cd": 21.0},
    {"id": "grenadier_flashbang", "name": "Flashbang", "class": "GRENADIER", "slot": "Q/E", "cd_key": "titan.flashbang.cdms", "default_cd": 11.0},
    {"id": "grenadier_molotov", "name": "Molotov", "class": "GRENADIER", "slot": "W/R", "cd_key": "titan.molotov.cdms", "default_cd": 15.0},
    {"id": "houndmaster_cage", "name": "Deploy Kennel", "class": "HOUNDMASTER", "slot": "Q/E", "cd_key": "titan.cage.cdms", "default_cd": 10.0},
    {"id": "houndmaster_wolf", "name": "Unleash Pack", "class": "HOUNDMASTER", "slot": "W/R", "cd_key": "titan.wolf.cdms", "default_cd": 20.0},
]


# ==============================================================================
# 4. DYNAMIC CONFIG LOADER (Reads res/game.cfg directly)
# ==============================================================================
ACTIVE_CFG_PATH = "res/game.cfg"

def load_game_cfg(cfg_path: str = "res/game.cfg") -> Tuple[Dict[str, float], str]:
    """Parse game.cfg dynamically from the filesystem."""
    cfg = {}
    actual_path = cfg_path
    if not os.path.exists(actual_path):
        alt_paths = [os.path.join("..", cfg_path), os.path.join(os.path.dirname(__file__), cfg_path)]
        for p in alt_paths:
            if os.path.exists(p):
                actual_path = p
                break

    if os.path.exists(actual_path):
        resolved = os.path.abspath(actual_path)
        with open(actual_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or line.startswith("//"):
                    continue
                if "=" in line:
                    k, v = line.split("=", 1)
                    k = k.strip()
                    v = v.strip()
                    try:
                        cfg[k] = float(v)
                    except ValueError:
                        pass
        return cfg, resolved
    return cfg, f"{cfg_path} (NOT FOUND)"


def compute_effective_boost_speed(
    base_speed: float,
    class_name: str,
    cfg: Dict[str, float],
    use_boost_speed: bool = True
) -> Tuple[float, float, float]:
    """
    Computes effective average speed assuming optimal boost uptime.
    Duty cycle u = R_fast / (D + R_fast).
    Speed multiplier = 1 + u * (boost_factor - 1).
    """
    if not use_boost_speed:
        return base_speed, 0.0, 1.0

    drain = cfg.get("globals.boost.drain", 0.75)
    regen_fast = cfg.get("globals.boost.regen.fast", 0.24)
    duty_cycle = regen_fast / (drain + regen_fast) if (drain + regen_fast) > 0 else 0.0

    if class_name.upper() == "DASHER":
        boost_factor = cfg.get("dasher.boost.boostFactor", 1.45)
    else:
        boost_factor = cfg.get("globals.boost.boostFactor", 1.33)

    speed_multiplier = 1.0 + duty_cycle * (boost_factor - 1.0)
    effective_speed = base_speed * speed_multiplier
    return round(effective_speed, 4), duty_cycle, boost_factor


def load_titan_stats_from_cfg(
    cfg: Dict[str, float],
    use_boost_speed: bool = True,
    include_goalie: bool = False
) -> Dict[str, Dict[str, float]]:
    """
    Extracts all Titan stats dynamically from game.cfg.
    Discovers every titan class present in game.cfg with titan.<class>.* keys.
    """
    discovered_classes = set()
    for k in cfg.keys():
        if k.startswith("titan."):
            parts = k.split(".")
            if len(parts) >= 3 and parts[2] in ("health", "speed", "shoot", "stealrad"):
                c_name = parts[1].upper()
                if include_goalie or c_name != "GOALIE":
                    discovered_classes.add(c_name)

    stats = {}
    for c_key in sorted(discovered_classes):
        c_lower = c_key.lower()
        base_hp = cfg.get(f"titan.{c_lower}.health", 100.0)
        base_spd = cfg.get(f"titan.{c_lower}.speed", 5.0)
        base_shoot = cfg.get(f"titan.{c_lower}.shoot", 1.0)
        base_steal = cfg.get(f"titan.{c_lower}.stealrad", 11.0)

        eff_spd, duty, b_factor = compute_effective_boost_speed(base_spd, c_key, cfg, use_boost_speed)

        stats[c_key] = {
            "hp": base_hp,
            "speed": eff_spd,
            "base_speed": base_spd,
            "boost_factor": b_factor,
            "duty_cycle": duty,
            "shoot": base_shoot,
            "stealrad": base_steal,
        }
    return stats


def load_ability_cooldowns(cfg: Dict[str, float]) -> Dict[str, float]:
    """Extract ability cooldowns (in seconds) dynamically from game.cfg."""
    cds = {}
    for ab in ABILITIES_REGISTRY:
        cd_key = ab["cd_key"]
        default_cd = ab["default_cd"]
        if cd_key in cfg:
            raw_val = cfg[cd_key]
            if cd_key.endswith(".cdms"):
                cds[ab["id"]] = raw_val / 1000.0
            else:
                cds[ab["id"]] = raw_val
        else:
            cds[ab["id"]] = default_cd
    return cds


# ==============================================================================
# 5. CORE BALANCE MATH (Stat Percentiles, CD-Discount, Weighted Scoring)
# ==============================================================================
def compute_stat_percentiles(class_stats: Dict[str, Dict[str, float]]) -> Dict[str, Dict[str, float]]:
    """Computes exact percentiles (0-100) for each stat across all classes."""
    stat_keys = ["hp", "speed", "shoot", "stealrad"]
    percentiles: Dict[str, Dict[str, float]] = {c: {} for c in class_stats}

    for stat in stat_keys:
        sorted_classes = sorted(class_stats.keys(), key=lambda c: class_stats[c][stat])
        n = len(sorted_classes)
        val_map: Dict[float, List[str]] = {}
        for c in sorted_classes:
            v = class_stats[c][stat]
            val_map.setdefault(v, []).append(c)

        running_rank = 1
        for val, tied_classes in sorted(val_map.items()):
            tied_count = len(tied_classes)
            avg_rank = running_rank + (tied_count - 1) / 2.0
            pct = (avg_rank / n) * 100.0
            for c in tied_classes:
                percentiles[c][stat] = round(pct, 2)
            running_rank += tied_count

    return percentiles


def compute_ability_scores(
    ordered_ability_ids: List[str],
    cooldowns: Dict[str, float]
) -> Dict[str, Dict[str, Any]]:
    """
    Computes continuous ability power with a blend of CD-adjusted and absolute strength:
    1. Raw power percentile / absolute strength (0-100) based on sorted power rank (1 to M).
    2. Cooldown-adjusted continuous power = raw_pct * (avg_cooldown / cooldown_seconds).
    3. Final scaled power = 75% CD-adjusted ability strength + 25% absolute strength
       (as cooldowns are reset at start of game and after centergoals).
    """
    m = len(ordered_ability_ids)
    results = {}

    # 1. Compute raw power percentiles from sorted list
    raw_pcts = {}
    total_cd = 0.0
    for rank_idx, ab_id in enumerate(ordered_ability_ids, start=1):
        raw_pct = (rank_idx / m) * 100.0
        cd = max(0.1, cooldowns.get(ab_id, 1.0))
        total_cd += cd
        raw_pcts[ab_id] = {
            "rank": rank_idx,
            "raw_pct": round(raw_pct, 2),
            "cd": cd,
        }

    # Reference cooldown (mean cooldown across all abilities to anchor the 0-100 scale)
    avg_cd = total_cd / m if m > 0 else 10.0

    reg_map = {ab["id"]: ab for ab in ABILITIES_REGISTRY}
    for ab_id in ordered_ability_ids:
        meta = reg_map.get(ab_id, {})
        raw_pct = raw_pcts[ab_id]["raw_pct"]
        cd = raw_pcts[ab_id]["cd"]

        # Continuous efficiency (Power per second)
        efficiency = raw_pct / cd

        # CD-adjusted continuous scaled power (anchored to average roster cooldown scale)
        cd_scaled_power = raw_pct * (avg_cd / cd)

        # Blended power score: 75% CD-adjusted + 25% absolute strength
        # (as cooldowns are reset at start of game and after centergoals)
        scaled_power = 0.75 * cd_scaled_power + 0.25 * raw_pct

        results[ab_id] = {
            "id": ab_id,
            "name": meta.get("name", ab_id),
            "class": meta.get("class", ""),
            "slot": meta.get("slot", ""),
            "raw_rank": raw_pcts[ab_id]["rank"],
            "raw_pct": raw_pct,
            "cooldown_s": cd,
            "discounted_efficiency": round(efficiency, 4),
            "cd_scaled_power": round(cd_scaled_power, 2),
            "scaled_power": round(scaled_power, 2),
        }

    return results


def analyze_balance(
    class_stats: Dict[str, Dict[str, float]],
    ordered_ability_ids: List[str],
    cooldowns: Dict[str, float],
    stat_weights: Optional[Dict[str, float]] = None,
    ability_weights: Optional[Dict[str, float]] = None,
) -> Dict[str, Any]:
    """
    Executes complete balance evaluation:
    - Applies custom 1-100 weights per stat and ability.
    - Uses blended ability power (75% CD-adjusted + 25% absolute strength).
    - Generates sorted map of relative strength for each Titan class.
    """
    s_weights = dict(STAT_WEIGHTS)
    if stat_weights:
        s_weights.update(stat_weights)

    a_weights = dict(ABILITY_WEIGHTS)
    if ability_weights:
        a_weights.update(ability_weights)

    stat_pcts = compute_stat_percentiles(class_stats)
    ability_scores = compute_ability_scores(ordered_ability_ids, cooldowns)

    for ab_id, ab_data in ability_scores.items():
        ab_data["weight"] = max(1.0, min(100.0, float(a_weights.get(ab_id, 50.0))))

    class_abilities: Dict[str, List[Dict[str, Any]]] = {c: [] for c in class_stats}
    for ab_info in ability_scores.values():
        c = ab_info["class"]
        if c in class_abilities:
            class_abilities[c].append(ab_info)

    class_results = {}
    for c, stats in class_stats.items():
        s_pct = stat_pcts[c]

        # 1. Weighted Stat Average
        stat_w_sum = 0.0
        stat_weighted_pct_sum = 0.0
        for s_key in ["hp", "speed", "shoot", "stealrad"]:
            w = max(1.0, min(100.0, float(s_weights.get(s_key, 50.0))))
            stat_w_sum += w
            stat_weighted_pct_sum += w * s_pct[s_key]

        avg_stat_pct = stat_weighted_pct_sum / stat_w_sum if stat_w_sum > 0 else 50.0

        # 2. Weighted Ability Power (Blended: 75% CD-Adjusted + 25% Absolute)
        abs_for_class = class_abilities.get(c, [])
        ab_w_sum = 0.0
        ab_weighted_sum = 0.0
        if abs_for_class:
            for ab in abs_for_class:
                w = ab["weight"]
                ab_w_sum += w
                ab_weighted_sum += w * ab["scaled_power"]
            avg_ability_power = ab_weighted_sum / ab_w_sum if ab_w_sum > 0 else 50.0
        else:
            avg_ability_power = 50.0

        # 3. Overall Weighted Relative Strength
        total_weight = stat_w_sum + ab_w_sum
        overall_score = (stat_weighted_pct_sum + ab_weighted_sum) / total_weight if total_weight > 0 else 50.0

        class_results[c] = {
            "class": c,
            "raw_stats": stats,
            "stat_percentiles": s_pct,
            "avg_stat_pct": round(avg_stat_pct, 2),
            "abilities": abs_for_class,
            "avg_ability_pct": round(avg_ability_power, 2),  # Blended ability power
            "overall_strength": round(overall_score, 2),
            "relative_delta": round(overall_score - 50.0, 2),
        }

    sorted_classes = sorted(
        class_results.values(),
        key=lambda x: x["overall_strength"],
        reverse=True
    )

    return {
        "sorted_classes": sorted_classes,
        "class_results": class_results,
        "ability_scores": ability_scores,
        "class_stats": class_stats,
        "cooldowns": cooldowns,
        "stat_weights": s_weights,
        "ability_weights": a_weights,
    }


# Ensure safe stdout encoding for Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def make_bar(pct: float, width: int = 20) -> str:
    """Renders an ASCII bar chart."""
    filled = int(round((pct / 100.0) * width))
    filled = max(0, min(width, filled))
    return "=" * filled + "-" * (width - filled)


def print_balance_report(analysis: Dict[str, Any], cfg_source: str):
    """Print complete balance analysis report."""
    sorted_classes = analysis["sorted_classes"]
    ability_scores = analysis["ability_scores"]
    class_stats = analysis.get("class_stats", {})
    s_weights = analysis.get("stat_weights", STAT_WEIGHTS)
    a_weights = analysis.get("ability_weights", ABILITY_WEIGHTS)

    sample_stat = next(iter(class_stats.values()), {})
    has_boost = "boost_factor" in sample_stat and sample_stat.get("boost_factor", 1.0) > 1.0

    is_custom_stat_w = any(abs(v - 50.0) > 0.01 for v in s_weights.values())
    is_custom_ab_w = any(abs(v - 50.0) > 0.01 for v in a_weights.values())

    print("\n" + "=" * 98)
    print("                    TITANBALL BALANCE & RELATIVE STRENGTH MATRIX")
    print("=" * 98)
    print(f"Config Source:   {cfg_source} ({len(class_stats)} Titans loaded dynamically)")
    if is_custom_stat_w or is_custom_ab_w:
        w_summary = f"Custom (HP:{s_weights['hp']:.0f}, Spd:{s_weights['speed']:.0f}, Throw:{s_weights['shoot']:.0f}, Steal:{s_weights['stealrad']:.0f})"
        print(f"Weighting Model: {w_summary:<75}")
    else:
        print("Weighting Model: Equal 1/n per Stat (50/50) + Equal 1/n per Ability (75% CD-Adj / 25% Absolute)")

    if has_boost:
        print("Speed Model:     Average effective speed assuming optimal boost uptime")
        print("                 (Dasher 1.45x boost = 5.28 avg vs 4.76 base; standard titans 1.33x boost = +8.0% avg)")
    else:
        print("Speed Model:     Raw base movement speed without boost factor")
    print("Baseline Median: 50.00%  |  Sorted from STRONGEST to WEAKEST")
    print("-" * 98)

    # 1. SUMMARY RANKING MAP
    header = f"{'Rank':<5} {'Class':<14} {'Overall':<10} {'Strength Bar':<22} {'Stat Avg':<10} {'Abil Avg':<10} {'Delta':<8}"
    print(header)
    print("-" * 98)
    for idx, cr in enumerate(sorted_classes, start=1):
        bar = make_bar(cr["overall_strength"], 18)
        delta_str = f"+{cr['relative_delta']:.1f}%" if cr["relative_delta"] > 0 else f"{cr['relative_delta']:.1f}%"
        print(
            f"#{idx:<4} {cr['class']:<14} {cr['overall_strength']:>6.2f}%   [{bar}]   "
            f"{cr['avg_stat_pct']:>6.2f}%   {cr['avg_ability_pct']:>6.2f}%   {delta_str:>7}"
        )
    print("-" * 98)

    # 2. DETAILED STATS BREAKDOWN
    print("\n" + "=" * 98)
    print("                        DETAILED CLASS STATS & PERCENTILES")
    print("=" * 98)
    stat_header = (
        f"{'Class':<12} {'HP (%ile) [w=' + str(int(s_weights['hp'])) + ']':<18} "
        f"{'Avg Speed* (%ile) [w=' + str(int(s_weights['speed'])) + ']':<26} "
        f"{'Throw (%ile) [w=' + str(int(s_weights['shoot'])) + ']':<20} "
        f"{'StealRad [w=' + str(int(s_weights['stealrad'])) + ']':<18} {'Stat Avg':<10}"
    )
    print(stat_header)
    print("-" * 98)
    for cr in sorted_classes:
        c = cr["class"]
        raw = cr["raw_stats"]
        pct = cr["stat_percentiles"]
        hp_str = f"{raw['hp']:.0f} ({pct['hp']:.0f}%)"
        base_s = raw.get("base_speed", raw["speed"])
        if abs(base_s - raw["speed"]) > 0.001:
            spd_str = f"{raw['speed']:.2f} [{base_s:.2f}] ({pct['speed']:.0f}%)"
        else:
            spd_str = f"{raw['speed']:.2f} ({pct['speed']:.0f}%)"
        thr_str = f"{raw['shoot']:.2f} ({pct['shoot']:.0f}%)"
        stl_str = f"{raw['stealrad']:.0f}px ({pct['stealrad']:.0f}%)"
        print(f"{c:<12} {hp_str:<18} {spd_str:<26} {thr_str:<20} {stl_str:<18} {cr['avg_stat_pct']:>6.2f}%")
    print("-" * 98)
    print(" * Speed format: EffectiveAvgSpeed [BaseSpeed] (Percentile)")

    # 3. ABILITY BREAKDOWN & COOLDOWN DISCOUNTING
    print("\n" + "=" * 98)
    print("          ABILITY POWER RANKINGS (75% CD-Adjusted / 25% Absolute Strength)")
    print("=" * 98)
    ab_header = (
        f"{'Rank':<5} {'Ability Name':<20} {'Class':<12} {'Slot':<6} "
        f"{'Raw Rank':<9} {'Raw %':<8} {'CD (s)':<8} {'Eff (P/s)':<12} {'Weight':<8} {'Power Score':<12}"
    )
    print(ab_header)
    print("-" * 98)

    sorted_abs = sorted(ability_scores.values(), key=lambda a: a["scaled_power"], reverse=True)
    for idx, ab in enumerate(sorted_abs, start=1):
        print(
            f"#{idx:<4} {ab['name']:<20} {ab['class']:<12} {ab['slot']:<6} "
            f"{ab['raw_rank']:<9} {ab['raw_pct']:>5.1f}%   {ab['cooldown_s']:>5.1f}s   "
            f"{ab['discounted_efficiency']:>8.2f}     {ab.get('weight', 50):>5.0f}    {ab['scaled_power']:>8.2f}"
        )
    print("-" * 98)

    # 4. BALANCE DISCREPANCY INSIGHTS & TWEAK SUGGESTIONS
    print("\n" + "=" * 98)
    print("                     BALANCE DISCREPANCIES & TWEAK ADVICE")
    print("=" * 98)
    top = sorted_classes[0]
    bottom = sorted_classes[-1]
    spread = top["overall_strength"] - bottom["overall_strength"]

    print(f"* Power Spread: {spread:.2f}% disparity between #{1} {top['class']} and #{len(sorted_classes)} {bottom['class']}")
    print()

    overtuned = [c for c in sorted_classes if c["overall_strength"] >= 62.0]
    undertuned = [c for c in sorted_classes if c["overall_strength"] <= 38.0]

    if overtuned:
        print(" [!] OVERTUNED CLASSES (Score >= 62%):")
        for c in overtuned:
            high_driver = "Abilities" if c["avg_ability_pct"] > c["avg_stat_pct"] else "Base Stats"
            print(f"   - {c['class']} ({c['overall_strength']:.1f}%): Driven heavily by {high_driver} "
                  f"(Stats: {c['avg_stat_pct']:.1f}%, Abilities: {c['avg_ability_pct']:.1f}%).")
            if c["avg_ability_pct"] > 60:
                ab_names = [a["name"] for a in c["abilities"]]
                print(f"     -> Suggestion: Increase cooldowns for {', '.join(ab_names)} to reduce ability uptime.")
            if c["avg_stat_pct"] > 60:
                print(f"     -> Suggestion: Trim top stats (e.g. Speed/HP/StealRad) down towards roster median.")

    if undertuned:
        print("\n [!] UNDERTUNED CLASSES (Score <= 38%):")
        for c in undertuned:
            low_driver = "Abilities" if c["avg_ability_pct"] < c["avg_stat_pct"] else "Base Stats"
            print(f"   - {c['class']} ({c['overall_strength']:.1f}%): Held back by {low_driver} "
                  f"(Stats: {c['avg_stat_pct']:.1f}%, Abilities: {c['avg_ability_pct']:.1f}%).")
            if c["avg_ability_pct"] < 40:
                ab_names = [f"{a['name']} ({a['cooldown_s']}s)" for a in c["abilities"]]
                print(f"     -> Suggestion: Reduce cooldowns for {', '.join(ab_names)} or buff impact.")
            if c["avg_stat_pct"] < 40:
                print(f"     -> Suggestion: Buff base stats (e.g. Speed/Health/Shoot) to compensate.")

    if not overtuned and not undertuned:
        print(" [OK] Roster is relatively well-balanced (all classes within 38% - 62% range).")

    print("=" * 98 + "\n")


# ==============================================================================
# 6. INTERACTIVE CLI SESSION
# ==============================================================================
def interactive_session(
    cfg_path: str = "res/game.cfg",
    use_boost_speed: bool = True,
):
    """Interactive CLI to rank abilities, tweak 1-100 weights, tweak stats/cooldowns, and reload game.cfg."""
    cfg, cfg_source = load_game_cfg(cfg_path)
    current_order = list(ABILITY_ORDER)
    current_stats = load_titan_stats_from_cfg(cfg, use_boost_speed=use_boost_speed)
    current_cds = load_ability_cooldowns(cfg)
    current_stat_w = dict(STAT_WEIGHTS)
    current_ab_w = dict(ABILITY_WEIGHTS)
    use_boost = use_boost_speed
    reg_map = {ab["id"]: ab for ab in ABILITIES_REGISTRY}

    while True:
        analysis = analyze_balance(
            current_stats,
            current_order,
            current_cds,
            stat_weights=current_stat_w,
            ability_weights=current_ab_w
        )
        print_balance_report(analysis, cfg_source)

        print("INTERACTIVE MENU:")
        print("  1. Move / Re-rank an ability in the power hierarchy")
        print("  2. Swap two abilities in the ranking")
        print("  3. View current raw ability order (Rank 1 to 24)")
        print("  4. Tweak a Titan stat (HP, Base Speed, Shoot, StealRad)")
        print("  5. Tweak an Ability Cooldown")
        print("  6. Assign STAT Weights (HP, Speed, Shoot, StealRad: 1-100)")
        print("  7. Assign ABILITY Weights (1-100 per ability)")
        print("  8. View Current Weights Summary")
        print(f"  9. Toggle Boost-Adjusted Speed Model (Currently: {'ENABLED' if use_boost else 'DISABLED'})")
        print("  10. Reload all stats & cooldowns fresh from game.cfg")
        print("  11. Reset to script defaults")
        print("  q. Quit")

        choice = input("\nEnter choice (1-11, q): ").strip().lower()

        if choice in ("q", "quit", "exit"):
            break

        elif choice == "1":
            print("\nCurrent Ability Power Order (1 = Weakest, 24 = Strongest):")
            for idx, ab_id in enumerate(current_order, start=1):
                ab = reg_map[ab_id]
                print(f"  {idx:>2}. {ab['name']:<20} [{ab['class']}] ({ab['slot']})")
            try:
                from_idx = int(input("\nEnter current rank number to move (1-24): ").strip()) - 1
                to_idx = int(input(f"Enter target new rank number for '{reg_map[current_order[from_idx]]['name']}' (1-24): ").strip()) - 1
                if 0 <= from_idx < len(current_order) and 0 <= to_idx < len(current_order):
                    item = current_order.pop(from_idx)
                    current_order.insert(to_idx, item)
                    print(f"\n[OK] Moved '{reg_map[item]['name']}' to Rank #{to_idx + 1}!")
                else:
                    print("\n[!] Invalid rank number.")
            except (ValueError, IndexError):
                print("\n[!] Invalid input.")

        elif choice == "2":
            print("\nCurrent Ability Power Order:")
            for idx, ab_id in enumerate(current_order, start=1):
                ab = reg_map[ab_id]
                print(f"  {idx:>2}. {ab['name']:<20} [{ab['class']}]")
            try:
                idx1 = int(input("\nEnter first rank to swap (1-24): ").strip()) - 1
                idx2 = int(input("Enter second rank to swap (1-24): ").strip()) - 1
                if 0 <= idx1 < len(current_order) and 0 <= idx2 < len(current_order):
                    current_order[idx1], current_order[idx2] = current_order[idx2], current_order[idx1]
                    print(f"\n[OK] Swapped Rank #{idx1 + 1} and #{idx2 + 1}!")
                else:
                    print("\n[!] Invalid rank numbers.")
            except ValueError:
                print("\n[!] Invalid input.")

        elif choice == "3":
            print("\n" + "-" * 50)
            print("Current Raw Power Ranking (Weakest -> Strongest):")
            for idx, ab_id in enumerate(current_order, start=1):
                ab = reg_map[ab_id]
                print(f"  {idx:>2}. {ab['name']} [{ab['class']}]")
            print("-" * 50)
            input("Press Enter to continue...")

        elif choice == "4":
            print("\nClasses: " + ", ".join(current_stats.keys()))
            c_name = input("Enter Class name: ").strip().upper()
            if c_name in current_stats:
                print(f"Current stats for {c_name}: {current_stats[c_name]}")
                stat_name = input("Enter Stat to modify (hp, speed, shoot, stealrad): ").strip().lower()
                if stat_name == "speed":
                    try:
                        new_base = float(input(f"Enter new base speed for {c_name} (current base: {current_stats[c_name].get('base_speed', current_stats[c_name]['speed'])}): ").strip())
                        eff_s, duty, bf = compute_effective_boost_speed(new_base, c_name, cfg, use_boost)
                        current_stats[c_name]["base_speed"] = new_base
                        current_stats[c_name]["speed"] = eff_s
                        print(f"\n[OK] Updated {c_name} base speed = {new_base} -> effective avg speed = {eff_s}")
                    except ValueError:
                        print("\n[!] Invalid number.")
                elif stat_name in current_stats[c_name]:
                    try:
                        new_val = float(input(f"Enter new value for {c_name}.{stat_name} (current: {current_stats[c_name][stat_name]}): ").strip())
                        current_stats[c_name][stat_name] = new_val
                        print(f"\n[OK] Updated {c_name}.{stat_name} = {new_val}")
                    except ValueError:
                        print("\n[!] Invalid number.")
                else:
                    print("\n[!] Unknown stat.")
            else:
                print("\n[!] Unknown class.")

        elif choice == "5":
            print("\nAbilities:")
            for idx, ab_id in enumerate(current_order, start=1):
                ab = reg_map[ab_id]
                print(f"  {idx:>2}. {ab['name']:<20} (Current CD: {current_cds.get(ab_id, ab['default_cd']):.1f}s)")
            try:
                ab_idx = int(input("\nEnter Ability rank number (1-24): ").strip()) - 1
                if 0 <= ab_idx < len(current_order):
                    ab_id = current_order[ab_idx]
                    curr_cd = current_cds.get(ab_id, reg_map[ab_id]["default_cd"])
                    new_cd = float(input(f"Enter new cooldown in seconds for '{reg_map[ab_id]['name']}' (current: {curr_cd:.1f}s): ").strip())
                    if new_cd > 0:
                        current_cds[ab_id] = new_cd
                        print(f"\n[OK] Updated cooldown for '{reg_map[ab_id]['name']}' to {new_cd:.1f}s!")
                    else:
                        print("\n[!] Cooldown must be greater than 0.")
                else:
                    print("\n[!] Invalid rank number.")
            except ValueError:
                print("\n[!] Invalid input.")

        elif choice == "6":
            print("\nAssign Stat Weights (1-100):")
            for s_k in ["hp", "speed", "shoot", "stealrad"]:
                curr_w = current_stat_w.get(s_k, 50.0)
                val_input = input(f"Enter weight for '{s_k}' [1-100] (current: {curr_w:.0f}, press Enter to keep): ").strip()
                if val_input:
                    try:
                        w_val = float(val_input)
                        current_stat_w[s_k] = max(1.0, min(100.0, w_val))
                    except ValueError:
                        print(f"[!] Invalid number for {s_k}")
            print("\n[OK] Updated Stat Weights:", current_stat_w)

        elif choice == "7":
            print("\nAssign Ability Weights (1-100):")
            print("  a. Set weight for a single specific ability")
            print("  b. Set uniform weight for ALL abilities")
            sub_c = input("Enter choice (a/b): ").strip().lower()
            if sub_c == "a":
                for idx, ab_id in enumerate(current_order, start=1):
                    ab = reg_map[ab_id]
                    print(f"  {idx:>2}. {ab['name']:<20} [{ab['class']}] (Weight: {current_ab_w.get(ab_id, 50.0):.0f})")
                try:
                    ab_idx = int(input("\nEnter Ability rank number to set weight (1-24): ").strip()) - 1
                    if 0 <= ab_idx < len(current_order):
                        ab_id = current_order[ab_idx]
                        ab_name = reg_map[ab_id]["name"]
                        curr_w = current_ab_w.get(ab_id, 50.0)
                        new_w = float(input(f"Enter new weight for '{ab_name}' [1-100] (current: {curr_w:.0f}): ").strip())
                        current_ab_w[ab_id] = max(1.0, min(100.0, new_w))
                        print(f"\n[OK] Updated weight for '{ab_name}' = {current_ab_w[ab_id]:.0f}")
                    else:
                        print("\n[!] Invalid rank number.")
                except ValueError:
                    print("\n[!] Invalid input.")
            elif sub_c == "b":
                try:
                    all_w = float(input("Enter uniform weight for ALL abilities [1-100]: ").strip())
                    all_w = max(1.0, min(100.0, all_w))
                    for ab_id in current_ab_w:
                        current_ab_w[ab_id] = all_w
                    print(f"\n[OK] Set weight for all abilities to {all_w:.0f}!")
                except ValueError:
                    print("\n[!] Invalid input.")

        elif choice == "8":
            print("\n" + "=" * 50)
            print("                CURRENT WEIGHTS SUMMARY")
            print("=" * 50)
            print("Stat Weights (1-100):")
            for s_k, w in current_stat_w.items():
                print(f"  - {s_k:<12}: {w:.0f}")
            print("\nAbility Weights (1-100):")
            for ab_id in current_order:
                ab = reg_map[ab_id]
                print(f"  - {ab['name']:<20} [{ab['class']}]: {current_ab_w.get(ab_id, 50.0):.0f}")
            print("=" * 50)
            input("\nPress Enter to continue...")

        elif choice == "9":
            use_boost = not use_boost
            current_stats = load_titan_stats_from_cfg(cfg, use_boost_speed=use_boost)
            print(f"\n[OK] Boost-adjusted speed model is now {'ENABLED' if use_boost else 'DISABLED'}!")
            input("Press Enter to continue...")

        elif choice == "10":
            fresh_cfg, cfg_source = load_game_cfg(cfg_path)
            current_stats = load_titan_stats_from_cfg(fresh_cfg, use_boost_speed=use_boost)
            current_cds = load_ability_cooldowns(fresh_cfg)
            cfg = fresh_cfg
            print(f"\n[OK] Reloaded fresh stats and cooldowns from '{cfg_source}'!")
            input("Press Enter to continue...")

        elif choice == "11":
            current_order = list(ABILITY_ORDER)
            current_stats = load_titan_stats_from_cfg(cfg, use_boost_speed=use_boost)
            current_cds = load_ability_cooldowns(cfg)
            current_stat_w = dict(STAT_WEIGHTS)
            current_ab_w = dict(ABILITY_WEIGHTS)
            print("\n[OK] Reset to script defaults!")


# ==============================================================================
# 7. MAIN ENTRY POINT
# ==============================================================================
def main():
    parser = argparse.ArgumentParser(
        description="TitanBall Balance Analyzer: Computes Titan class relative strength matrix."
    )
    parser.add_argument(
        "--config",
        "-c",
        default="res/game.cfg",
        help="Path to game.cfg (default: res/game.cfg)"
    )
    parser.add_argument(
        "--no-boost",
        action="store_true",
        help="Disable boost uptime modeling (use raw base speeds without boost factor)"
    )
    parser.add_argument(
        "--interactive",
        "-i",
        action="store_true",
        help="Launch interactive terminal mode to re-rank abilities, assign weights, and tweak stats"
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Output raw analysis results as JSON"
    )

    args = parser.parse_args()

    cfg, cfg_source = load_game_cfg(args.config)
    class_stats = load_titan_stats_from_cfg(cfg, use_boost_speed=not args.no_boost)
    cooldowns = load_ability_cooldowns(cfg)

    if args.interactive:
        interactive_session(args.config, use_boost_speed=not args.no_boost)
    else:
        analysis = analyze_balance(
            class_stats,
            ABILITY_ORDER,
            cooldowns,
            stat_weights=STAT_WEIGHTS,
            ability_weights=ABILITY_WEIGHTS
        )
        if args.json:
            json_output = {
                "config_source": cfg_source,
                "relative_strength_rankings": [
                    {
                        "rank": idx,
                        "class": cr["class"],
                        "overall_strength_pct": cr["overall_strength"],
                        "delta_from_baseline": cr["relative_delta"],
                        "avg_stat_pct": cr["avg_stat_pct"],
                        "avg_ability_pct": cr["avg_ability_pct"],
                        "stat_percentiles": cr["stat_percentiles"],
                        "raw_stats": cr["raw_stats"],
                        "abilities": cr["abilities"],
                    }
                    for idx, cr in enumerate(analysis["sorted_classes"], start=1)
                ],
                "stat_weights": analysis["stat_weights"],
                "ability_weights": analysis["ability_weights"],
            }
            print(json.dumps(json_output, indent=2))
        else:
            print_balance_report(analysis, cfg_source)


if __name__ == "__main__":
    main()
