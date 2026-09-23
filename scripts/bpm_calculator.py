#!/usr/bin/env python3
"""
TitanBall Single-Game Player BPM (Box Plus/Minus) Regression Engine
------------------------------------------------------------------
Constrained Multivariable Linear Regression for:
1. Offensive Point Differential (OBPM): Target = points_for - mean(points_for)
2. Defensive Point Differential (DBPM): Target = mean(points_against) - points_against (points prevented)
3. Total Box Plus/Minus (TBPM): Target = point_diff (points_for - points_against)

Applies domain bounds:
- CG >= CG assist, with CG + CG assist <= 3.0
- deaths strictly negative (<= -0.01)
- kills strictly positive (>= +0.01)
- passes strictly positive (>= +0.005)
- rebounds strictly positive (>= +0.01)
- turnovers strictly negative (<= -0.01)

Compares:
- Model A: Standard Split (Kills & Deaths in DBPM)
- Model B: Kills & Deaths moved to OBPM (DBPM = Steals & Blocks)
- Model C: Only Deaths moved to OBPM (Kills remain in DBPM)
"""

import sys
import math

# All available outfield metrics and their user-friendly labels
ALL_METRICS = {
    "goals": "Center Goals",
    "sidegoals": "Side Goals",
    "cg_assists": "CG Assists",
    "sg_assists": "SG Assists",
    "passes": "Passes",
    "rebounds": "Rebounds",
    "turnovers": "Turnovers",
    "steals": "Steals",
    "blocks": "Blocks",
    "kills": "Kills",
    "deaths": "Deaths",
    "killassists": "Kill Assists",
}

# Domain bounds to eliminate multicollinearity sign inversions
DEFAULT_BOUNDS = {
    "goals": (0.20, 2.50),
    "cg_assists": (0.10, 2.00),
    "sidegoals": (0.10, 1.00),
    "sg_assists": (0.05, 0.75),
    "passes": (0.005, None),      # strictly positive
    "rebounds": (0.010, None),    # strictly positive
    "turnovers": (None, -0.010),  # strictly negative
    "steals": (0.050, None),      # strictly positive
    "blocks": (0.050, None),      # strictly positive
    "kills": (0.010, None),       # strictly positive
    "deaths": (None, -0.010),     # strictly negative
    "killassists": (0.005, None), # strictly positive
}

def compute_pearson_r(x_vals, y_vals):
    n = len(x_vals)
    if n == 0:
        return 0.0
    mean_x = sum(x_vals) / n
    mean_y = sum(y_vals) / n
    cov = sum((x - mean_x) * (y - mean_y) for x, y in zip(x_vals, y_vals))
    var_x = sum((x - mean_x) ** 2 for x in x_vals)
    var_y = sum((y - mean_y) ** 2 for y in y_vals)
    if var_x <= 1e-9 or var_y <= 1e-9:
        return 0.0
    return cov / math.sqrt(var_x * var_y)

def solve_constrained_ols(X, y, feature_keys, bounds=DEFAULT_BOUNDS, enforce_cg_rule=True, max_iter=200):
    """
    Solves constrained least-squares regression using Coordinate Descent
    over the precomputed Gram matrix G = X^T X and b = X^T y.
    Executes in milliseconds even for large n.
    """
    n = len(y)
    p = len(X[0])

    # Precalculate Gram matrix G = X^T X and vector b = X^T y in ONE fast pass
    G = [[0.0] * p for _ in range(p)]
    b = [0.0] * p
    for row, y_val in zip(X, y):
        for i in range(p):
            b[i] += row[i] * y_val
            for j in range(i, p):
                G[i][j] += row[i] * row[j]
    for i in range(p):
        for j in range(i):
            G[i][j] = G[j][i]

    beta = [0.0] * p
    beta[0] = sum(y) / n

    cg_idx = (feature_keys.index("goals") + 1) if "goals" in feature_keys else -1
    cg_ast_idx = (feature_keys.index("cg_assists") + 1) if "cg_assists" in feature_keys else -1
    k_idx = (feature_keys.index("kills") + 1) if "kills" in feature_keys else -1
    kast_idx = (feature_keys.index("killassists") + 1) if "killassists" in feature_keys else -1

    for _ in range(max_iter):
        # 1. Update intercept (unconstrained)
        g_0 = sum(G[0][k] * beta[k] for k in range(p)) - b[0]
        if G[0][0] > 1e-9:
            beta[0] -= g_0 / G[0][0]

        # 2. Update feature coefficients with bounds
        for j in range(1, p):
            if G[j][j] < 1e-9:
                continue
            g_j = sum(G[j][k] * beta[k] for k in range(p)) - b[j]
            b_cand = beta[j] - g_j / G[j][j]

            k = feature_keys[j - 1]
            if bounds and k in bounds:
                b_min, b_max = bounds[k]
                if b_min is not None and b_cand < b_min:
                    b_cand = b_min
                if b_max is not None and b_cand > b_max:
                    b_cand = b_max
            beta[j] = b_cand

        # 3. Enforce CG >= CG assist and CG + CG assist <= 3.0
        if enforce_cg_rule and cg_idx != -1 and cg_ast_idx != -1:
            if beta[cg_idx] < beta[cg_ast_idx]:
                mid = (beta[cg_idx] + beta[cg_ast_idx]) / 2.0
                beta[cg_idx] = mid
                beta[cg_ast_idx] = mid

            if (beta[cg_idx] + beta[cg_ast_idx]) > 3.0:
                excess = (beta[cg_idx] + beta[cg_ast_idx] - 3.0) / 2.0
                beta[cg_idx] -= excess
                beta[cg_ast_idx] -= excess

        # 4. Enforce kills >= killassists (kpg >= kastpg)
        if k_idx != -1 and kast_idx != -1:
            if beta[k_idx] < beta[kast_idx]:
                mid = (beta[k_idx] + beta[kast_idx]) / 2.0
                beta[k_idx] = mid
                beta[kast_idx] = mid

    # R-squared
    y_mean = sum(y) / n
    ss_tot = sum((val - y_mean) ** 2 for val in y)
    y_pred = [sum(X[i][j] * beta[j] for j in range(p)) for i in range(n)]
    ss_res = sum((y[i] - y_pred[i]) ** 2 for i in range(n))
    r_squared = 1.0 - (ss_res / ss_tot) if ss_tot > 0 else 0.0

    return beta, r_squared, y_pred

def run_model(model_name, off_keys, def_keys, records, pts_for, pts_against, net_diff, mean_pts_for, mean_pts_against):
    n = len(records)
    target_off = [p - mean_pts_for for p in pts_for]
    target_def = [mean_pts_against - p for p in pts_against]

    # Matrix Offense
    X_off = [[1.0] + [r.get(k, 0.0) for k in off_keys] for r in records]
    beta_off, r2_off, pred_off = solve_constrained_ols(X_off, target_off, off_keys)

    # Matrix Defense
    X_def = [[1.0] + [r.get(k, 0.0) for k in def_keys] for r in records]
    beta_def, r2_def, pred_def = solve_constrained_ols(X_def, target_def, def_keys)

    print("=" * 92)
    print(f" {model_name}")
    print("=" * 92)

    # 1. OBPM Table
    print(f"\n [1] OFFENSIVE BPM (OBPM) | Model Fit: R^2 = {r2_off:.4f} | Base Offset = {beta_off[0]:+.3f}")
    print("-" * 92)
    print(f"{'Offensive Metric':<20} | {'Mean/G':<8} | {'StdDev':<8} | {'r (Pts For)':<12} | {'r (Margin)':<12} | {'OBPM Beta Weight':<18}")
    print("-" * 92)
    for idx, k in enumerate(off_keys):
        vals = [r.get(k, 0.0) for r in records]
        mean_v = sum(vals) / n
        std_v = math.sqrt(sum((v - mean_v) ** 2 for v in vals) / n) if n > 1 else 0.0
        r_pts = compute_pearson_r(vals, pts_for)
        r_diff = compute_pearson_r(vals, net_diff)
        b = beta_off[idx + 1]
        label = ALL_METRICS.get(k, k)
        print(f"{label:<20} | {mean_v:<8.2f} | {std_v:<8.2f} | {r_pts:>+10.4f}  | {r_diff:>+10.4f}  | {b:>+14.4f} pts")

    # 2. DBPM Table
    print(f"\n [2] DEFENSIVE BPM (DBPM) | Model Fit: R^2 = {r2_def:.4f} | Base Offset = {beta_def[0]:+.3f}")
    print("-" * 92)
    print(f"{'Defensive Metric':<20} | {'Mean/G':<8} | {'StdDev':<8} | {'r (Prevent)':<12} | {'r (Margin)':<12} | {'DBPM Beta Weight':<18}")
    print("-" * 92)
    for idx, k in enumerate(def_keys):
        vals = [r.get(k, 0.0) for r in records]
        mean_v = sum(vals) / n
        std_v = math.sqrt(sum((v - mean_v) ** 2 for v in vals) / n) if n > 1 else 0.0
        r_prev = compute_pearson_r(vals, target_def)
        r_diff = compute_pearson_r(vals, net_diff)
        b = beta_def[idx + 1]
        label = ALL_METRICS.get(k, k)
        print(f"{label:<20} | {mean_v:<8.2f} | {std_v:<8.2f} | {r_prev:>+10.4f}  | {r_diff:>+10.4f}  | {b:>+14.4f} pts")

    # Formulas
    print("\n" + "-" * 92)
    obpm_terms = [f"{beta_off[i+1]:+.3f}*{k}" for i, k in enumerate(off_keys)]
    dbpm_terms = [f"{beta_def[i+1]:+.3f}*{k}" for i, k in enumerate(def_keys)]
    print(f"OBPM = {beta_off[0]:+.3f} " + " ".join(obpm_terms))
    print(f"DBPM = {beta_def[0]:+.3f} " + " ".join(dbpm_terms))
    print("-" * 92)

    # Class Breakdown
    classes = sorted(list(set(r.get("outfieldclass", "") for r in records if r.get("outfieldclass"))))
    if classes:
        print(f"\n{'Class':<14} | {'Games':<6} | {'Win %':<7} | {'Pts For':<8} | {'Pts Agst':<8} | {'Net Diff':<9} | {'OBPM':<7} | {'DBPM':<7} | {'TBPM':<7}")
        print("-" * 92)
        class_rows = []
        for c in classes:
            c_records = [r for r in records if r.get("outfieldclass") == c]
            cn = len(c_records)
            if cn == 0:
                continue
            c_pf = sum(r.get("points_for", 0.0) for r in c_records) / cn
            c_pa = sum(r.get("points_against", 0.0) for r in c_records) / cn
            c_diff = c_pf - c_pa
            c_win = (sum(r.get("won", 0.0) for r in c_records) / cn) * 100.0

            c_obpm = beta_off[0] + sum(beta_off[i+1] * (sum(r.get(k, 0.0) for r in c_records) / cn) for i, k in enumerate(off_keys))
            c_dbpm = beta_def[0] + sum(beta_def[i+1] * (sum(r.get(k, 0.0) for r in c_records) / cn) for i, k in enumerate(def_keys))
            c_tbpm = c_obpm + c_dbpm
            class_rows.append((c, cn, c_win, c_pf, c_pa, c_diff, c_obpm, c_dbpm, c_tbpm))

        class_rows.sort(key=lambda x: x[8], reverse=True)
        for c, cn, c_win, c_pf, c_pa, c_diff, c_obpm, c_dbpm, c_tbpm in class_rows:
            print(f"{c:<14} | {cn:<6} | {c_win:>5.1f}% | {c_pf:<8.2f} | {c_pa:<8.2f} | {c_diff:>+7.2f}   | {c_obpm:>+5.2f} | {c_dbpm:>+5.2f} | {c_tbpm:>+5.2f}")
        print("-" * 92 + "\n")

def analyze_records(records):
    n = len(records)
    if n < 10:
        print(f"[!] Need at least 10 player-game rows for regression. Currently have: {n}")
        return

    pts_for = [r.get("points_for", 0.0) for r in records]
    pts_against = [r.get("points_against", 0.0) for r in records]
    net_diff = [records[i].get("point_diff", pts_for[i] - pts_against[i]) for i in range(n)]

    mean_pts_for = sum(pts_for) / n
    mean_pts_against = sum(pts_against) / n
    mean_net_diff = sum(net_diff) / n

    print("=" * 92)
    print(f"       TITANBALL CONSTRAINED BPM REGRESSION ENGINE (Sample: {n:,} Outfield Player-Games)")
    print("=" * 92)
    print(f" Baseline Team Averages | Points For: {mean_pts_for:.2f} | Points Against: {mean_pts_against:.2f} | Net Margin: {mean_net_diff:+.2f}")
    print(" Constraints Enforced: CG >= CG Assist (CG+Ast <= 3.0), Kills/Pass/Reb > 0, Deaths/Turnovers < 0")
    print("=" * 92 + "\n")

    # -------------------------------------------------------------
    # OFFICIAL SPECIFICATION: MODEL C (Deaths in OBPM, Kills in DBPM)
    # -------------------------------------------------------------
    off_c = ["goals", "sidegoals", "cg_assists", "sg_assists", "passes", "rebounds", "turnovers", "deaths"]
    def_c = ["steals", "blocks", "kills", "killassists"]
    run_model("OFFICIAL TITANBALL BPM: MODEL C (Deaths in OBPM, Kills in DBPM)", off_c, def_c, records, pts_for, pts_against, net_diff, mean_pts_for, mean_pts_against)

if __name__ == "__main__":
    records = []
    headers = None
    for line in sys.stdin:
        parts = line.strip().split("\t")
        if not parts or not parts[0]:
            continue
        if headers is None:
            headers = [p.lower() for p in parts]
            continue
        row = {}
        for h, v in zip(headers, parts):
            try:
                row[h] = float(v)
            except ValueError:
                row[h] = v
        records.append(row)
    analyze_records(records)
