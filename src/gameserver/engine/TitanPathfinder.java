package gameserver.engine;

import gameserver.entity.Box;
import gameserver.entity.Entity;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import gameserver.entity.minions.LaneMinion;
import gameserver.entity.minions.Parapet;
import util.Util;

import java.util.*;

/**
 * Theta* any-angle pathfinding utility for Titan movement.
 * Operates on a discrete 2D grid covering the titan play area,
 * producing 1-10 straight-line waypoints around solid obstacles.
 */
public class TitanPathfinder {

    public static final boolean PATH_DEBUG_CLIENT_TOGGLE = false;

    // Grid dimensions and bounds
    public static final int CELL_SIZE = 20;
    public static final int WORLD_MIN_X = -10;
    public static final int WORLD_MAX_X = 2030;
    public static final int WORLD_MIN_Y = 170;
    public static final int WORLD_MAX_Y = 950;

    public static final int GRID_COLS = (WORLD_MAX_X - WORLD_MIN_X + CELL_SIZE - 1) / CELL_SIZE; // 102
    public static final int GRID_ROWS = (WORLD_MAX_Y - WORLD_MIN_Y + CELL_SIZE - 1) / CELL_SIZE; // 39
    public static final int TOTAL_CELLS = GRID_COLS * GRID_ROWS;

    // Titan hitbox inflation (conservative half-dimensions for regular titan hitbox: 20w x 52h)
    public static final int INFLATE_X = 10;
    public static final int INFLATE_Y = 26;
    public static final int MAX_WAYPOINTS = 10;

    // Precomputed 8-directional neighbor offsets (dx, dy, cost)
    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double[] STEP_COST = {SQRT2, 1.0, SQRT2, 1.0, 1.0, SQRT2, 1.0, SQRT2};

    public static int worldToCol(double wx) {
        int col = (int) Math.floor((wx - WORLD_MIN_X) / CELL_SIZE);
        if (col < 0) col = 0;
        if (col >= GRID_COLS) col = GRID_COLS - 1;
        return col;
    }

    public static int worldToRow(double wy) {
        int row = (int) Math.floor((wy - WORLD_MIN_Y) / CELL_SIZE);
        if (row < 0) row = 0;
        if (row >= GRID_ROWS) row = GRID_ROWS - 1;
        return row;
    }

    public static int toCellIdx(int col, int row) {
        return row * GRID_COLS + col;
    }

    public static double cellToWorldCenterX(int col) {
        return WORLD_MIN_X + col * CELL_SIZE + CELL_SIZE / 2.0;
    }

    public static double cellToWorldCenterY(int row) {
        return WORLD_MIN_Y + row * CELL_SIZE + CELL_SIZE / 2.0;
    }

    /**
     * Builds static obstacle grid from non-titan solid entities.
     * Skips non-solid entities (e.g. LaneMinion) to avoid iterating irrelevant entities.
     */
    public static boolean[] buildStaticGrid(GameEngine context) {
        boolean[] grid = new boolean[TOTAL_CELLS];
        if (context == null) return grid;

        Entity[] solids = context.allSolids;
        if (solids != null) {
            for (Entity e : solids) {
                if (e == null || e instanceof Titan) continue;
                if (!e.solid && !(e instanceof Parapet)) continue;
                if (e.health <= 0) continue;
                markObstacle(grid, context, e);
            }
        } else if (context.entityPool != null) {
            for (Entity e : context.entityPool) {
                if (e == null || e instanceof Titan) continue;
                if (!e.solid && !(e instanceof Parapet)) continue;
                if (e.health <= 0) continue;
                markObstacle(grid, context, e);
            }
        }
        return grid;
    }

    private static void markObstacle(boolean[] grid, GameEngine context, Entity e) {
        double minX = e.X;
        double minY = e.Y;
        double w = e.width;
        double h = e.height;

        if (e instanceof Parapet p) {
            CollisionMath.Bounds b = p.getEnemySolidBounds();
            minX = b.minX();
            minY = b.minY();
            w = b.width();
            h = b.height();
        }

        // Apply Minkowski sum inflation for titan radius
        markAABB(grid, minX - INFLATE_X, minY - INFLATE_Y, minX + w + INFLATE_X, minY + h + INFLATE_Y);
    }

    /**
     * Overlays other active Titans as obstacles on top of an existing grid.
     */
    public static void overlayTitans(boolean[] grid, GameEngine context, Titan self) {
        if (context == null || context.players == null) return;

        for (Titan other : context.players) {
            if (other == null || other == self || other.id.equals(self.id)) continue;
            if (other.health <= 0) continue;
            if (context.effectPool != null && context.effectPool.hasEffect(other, gameserver.effects.EffectId.DEAD)) continue;

            double minX, minY, w, h;
            if (other.getType() == TitanType.GOALIE) {
                double xOffset = (other.width - context.GOALIE_SOLID_W) / 2.0;
                minX = other.X + xOffset;
                minY = other.Y;
                w = context.GOALIE_SOLID_W;
                h = context.GOALIE_SOLID_H;
            } else {
                minX = other.X + context.SPRITE_X_EMPTY / 2.0;
                minY = other.Y + context.SPRITE_Y_EMPTY / 2.0;
                w = other.width - context.SPRITE_X_EMPTY;
                h = other.height - context.SPRITE_Y_EMPTY;
            }

            // Inflate by titan half-hitbox
            markAABB(grid, minX - INFLATE_X, minY - INFLATE_Y, minX + w + INFLATE_X, minY + h + INFLATE_Y);
        }
    }

    private static void markAABB(boolean[] grid, double minX, double minY, double maxX, double maxY) {
        int startCol = Math.max(0, worldToCol(minX));
        int endCol = Math.min(GRID_COLS - 1, worldToCol(maxX));
        int startRow = Math.max(0, worldToRow(minY));
        int endRow = Math.min(GRID_ROWS - 1, worldToRow(maxY));

        for (int r = startRow; r <= endRow; r++) {
            int rowOffset = r * GRID_COLS;
            for (int c = startCol; c <= endCol; c++) {
                grid[rowOffset + c] = true;
            }
        }
    }

    /**
     * Bresenham line-of-sight raycast on the grid.
     * Returns true if clear line of sight between start and goal world coordinates exists.
     */
    public static boolean hasLineOfSight(boolean[] grid, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dist = Math.hypot(dx, dy);
        if (dist <= 0.001) return true;

        // Step at half-cell resolution
        double stepDist = CELL_SIZE / 2.0;
        int numSteps = (int) Math.ceil(dist / stepDist);
        double stepX = dx / numSteps;
        double stepY = dy / numSteps;

        double curX = x0;
        double curY = y0;
        for (int i = 0; i <= numSteps; i++) {
            int col = worldToCol(curX);
            int row = worldToRow(curY);
            if (grid[toCellIdx(col, row)]) {
                return false;
            }
            curX += stepX;
            curY += stepY;
        }
        return true;
    }

    private record Node(int cell, double g, double f) implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            return Double.compare(this.f, o.f);
        }
    }

    /**
     * Computes the shortest path of 1-10 straight line segments using Theta*.
     * Returns an array of waypoints [x, y] or null if no valid path found.
     */
    public static int[][] computePath(double startX, double startY, double goalX, double goalY, boolean[] grid) {
        // Fast direct LOS check
        if (hasLineOfSight(grid, startX, startY, goalX, goalY)) {
            return new int[][] { {(int) Math.round(goalX), (int) Math.round(goalY)} };
        }

        int startCol = worldToCol(startX);
        int startRow = worldToRow(startY);
        int startCell = toCellIdx(startCol, startRow);

        int goalCol = worldToCol(goalX);
        int goalRow = worldToRow(goalY);
        int goalCell = toCellIdx(goalCol, goalRow);

        if (startCell == goalCell) {
            return new int[][] { {(int) Math.round(goalX), (int) Math.round(goalY)} };
        }

        double[] gScore = new double[TOTAL_CELLS];
        Arrays.fill(gScore, Double.POSITIVE_INFINITY);
        int[] parent = new int[TOTAL_CELLS];
        Arrays.fill(parent, -1);
        boolean[] closed = new boolean[TOTAL_CELLS];

        PriorityQueue<Node> open = new PriorityQueue<>(128);

        gScore[startCell] = 0.0;
        parent[startCell] = startCell;
        double startH = Math.hypot(goalCol - startCol, goalRow - startRow);
        open.add(new Node(startCell, 0.0, startH));

        boolean found = false;

        while (!open.isEmpty()) {
            Node curr = open.poll();
            int u = curr.cell;
            if (closed[u]) continue;
            closed[u] = true;

            if (u == goalCell) {
                found = true;
                break;
            }

            int uCol = u % GRID_COLS;
            int uRow = u / GRID_COLS;
            int p = parent[u];
            int pCol = p % GRID_COLS;
            int pRow = p / GRID_COLS;
            double pWorldX = cellToWorldCenterX(pCol);
            double pWorldY = cellToWorldCenterY(pRow);

            for (int i = 0; i < 8; i++) {
                int vCol = uCol + DX[i];
                int vRow = uRow + DY[i];
                if (vCol < 0 || vCol >= GRID_COLS || vRow < 0 || vRow >= GRID_ROWS) continue;

                int v = toCellIdx(vCol, vRow);
                if (closed[v] || grid[v]) continue;

                double vWorldX = cellToWorldCenterX(vCol);
                double vWorldY = cellToWorldCenterY(vRow);

                // Theta* LOS shortcut from parent(u) to v
                if (hasLineOfSight(grid, pWorldX, pWorldY, vWorldX, vWorldY)) {
                    double distPV = Math.hypot(vCol - pCol, vRow - pRow);
                    double tentativeG = gScore[p] + distPV;
                    if (tentativeG < gScore[v]) {
                        gScore[v] = tentativeG;
                        parent[v] = p;
                        double h = Math.hypot(goalCol - vCol, goalRow - vRow);
                        open.add(new Node(v, tentativeG, tentativeG + h));
                    }
                } else {
                    double tentativeG = gScore[u] + STEP_COST[i];
                    if (tentativeG < gScore[v]) {
                        gScore[v] = tentativeG;
                        parent[v] = u;
                        double h = Math.hypot(goalCol - vCol, goalRow - vRow);
                        open.add(new Node(v, tentativeG, tentativeG + h));
                    }
                }
            }
        }

        if (!found) {
            return null; // Fallback to naive movement
        }

        // Reconstruct path of cell waypoints
        List<int[]> waypoints = new ArrayList<>();
        int curr = goalCell;
        while (curr != startCell && curr != -1) {
            int c = curr % GRID_COLS;
            int r = curr / GRID_COLS;
            if (curr == goalCell) {
                waypoints.add(new int[] {(int) Math.round(goalX), (int) Math.round(goalY)});
            } else {
                waypoints.add(new int[] {(int) Math.round(cellToWorldCenterX(c)), (int) Math.round(cellToWorldCenterY(r))});
            }
            int next = parent[curr];
            if (next == curr) break;
            curr = next;
        }

        Collections.reverse(waypoints);

        // String pulling / waypoint reduction pass with LOS
        List<int[]> pruned = new ArrayList<>();
        double currentX = startX;
        double currentY = startY;
        int idx = 0;
        while (idx < waypoints.size()) {
            // Find furthest visible waypoint
            int furthest = idx;
            for (int k = waypoints.size() - 1; k >= idx; k--) {
                int[] wp = waypoints.get(k);
                if (hasLineOfSight(grid, currentX, currentY, wp[0], wp[1])) {
                    furthest = k;
                    break;
                }
            }
            int[] targetWp = waypoints.get(furthest);
            pruned.add(targetWp);
            currentX = targetWp[0];
            currentY = targetWp[1];
            idx = furthest + 1;
            if (pruned.size() >= MAX_WAYPOINTS) break;
        }

        return pruned.toArray(new int[0][]);
    }

    /**
     * Unified execution logic for programmed titan movement.
     * Evaluates path recalculation invalidations, follows waypoints,
     * and performs axis-split collision checks.
     */
    public static void executeProgrammedMovement(GameEngine context, Titan t) {
        if (!t.programmed) return;

        boolean canRun = context.isActionMovementUnlocked(t);
        if (context.effectPool.isRooted(t) || !canRun) {
            return;
        }

        // Ball following sentinel check
        if (t.marchingOrderX == -1 && t.marchingOrderY == -1) {
            if (context.ball != null) {
                t.marchingOrderX = (int) (context.ball.X + context.ball.centerDist);
                t.marchingOrderY = (int) (context.ball.Y + context.ball.centerDist);
            } else {
                return;
            }
        }

        // Check path invalidations
        if (shouldInvalidatePath(t)) {
            recalculateTitanPath(context, t);
        }

        // Target waypoint coords
        int targetX = t.marchingOrderX;
        int targetY = t.marchingOrderY;
        if (t.pathWaypoints != null && t.pathWaypointIdx < t.pathWaypoints.length) {
            targetX = t.pathWaypoints[t.pathWaypointIdx][0];
            targetY = t.pathWaypoints[t.pathWaypointIdx][1];
        }

        double titanCenterX = t.X + t.width / 2.0;
        double titanCenterY = t.Y + t.height / 2.0;

        double ang = Util.degreesFromCoords(targetX - titanCenterX, targetY - titanCenterY);
        double cosAng = Math.cos(Math.toRadians(ang));
        double sinAng = Math.sin(Math.toRadians(ang));
        double dirX = (cosAng > 0.001) ? 1.0 : ((cosAng < -0.001) ? -1.0 : 0.0);

        double speedX = t.actualSpeed(context, dirX);
        double speedY = t.actualSpeed(context, 0.0);

        double dx = speedX * cosAng;
        double dy = speedY * sinAng;

        // Clamp overshoot towards current waypoint
        if (dx > 0 && dx > targetX - titanCenterX) {
            dx = targetX - titanCenterX;
        }
        if (dy > 0 && dy > targetY - titanCenterY) {
            dy = targetY - titanCenterY;
        }
        if (dx < 0 && dx > titanCenterX - targetX) {
            dx = titanCenterX - targetX;
        }
        if (dy < 0 && dy > titanCenterY - targetY) {
            dy = titanCenterY - targetY;
        }

        boolean atLocation = 0.1 * t.actualSpeed(context) > (Math.abs(dx) + Math.abs(dy));
        if (atLocation) {
            t.runningFrame = 0;
            t.runningFrameCounter = 0;
        }

        // Axis-split movement
        if (!atLocation && !t.collidesSolid(context, context.allSolids, 0, dx)) {
            t.facing = (int) ang;
            t.diagonalRunDir = dx > 0 ? 2 : 1;
            t.dirToBall = t.diagonalRunDir;
            t.translateBounded(context, dx, 0.0);
            t.runningFrameCounter += 1;
            if (t.runningFrameCounter == 5) t.runningFrame = 1;
            if (t.runningFrameCounter == 10) {
                t.runningFrame = 2;
                t.runningFrameCounter = 0;
            }
        }
        if (!atLocation && !t.collidesSolid(context, context.allSolids, dy, 0)) {
            t.translateBounded(context, 0.0, dy);
        }

        // Check waypoint completion
        double currentCenterX = t.X + t.width / 2.0;
        double currentCenterY = t.Y + t.height / 2.0;
        double distToWp = Math.hypot(targetX - currentCenterX, targetY - currentCenterY);

        if (distToWp <= 10.0 || atLocation) {
            if (t.pathWaypoints != null && t.pathWaypointIdx < t.pathWaypoints.length) {
                t.pathWaypointIdx++;
                if (t.pathWaypointIdx >= t.pathWaypoints.length) {
                    // Final destination reached
                    t.programmed = false;
                    t.invalidatePath();
                }
            } else {
                t.programmed = false;
                t.invalidatePath();
            }
        }
    }

    private static boolean shouldInvalidatePath(Titan t) {
        if (t.pathWaypoints == null) return true;
        if (t.marchingOrderX != t.pathAnchorOrderX || t.marchingOrderY != t.pathAnchorOrderY) return true;
        if (t.isBoosting != t.pathAnchorBoosting) return true;
        if (Math.abs(t.X - t.pathAnchorX) > 12.0 || Math.abs(t.Y - t.pathAnchorY) > 12.0) return true;
        return false;
    }

    public static void recalculateTitanPath(GameEngine context, Titan t) {
        boolean[] baseGrid = context.getOrCreateStaticObstacleGrid();
        boolean[] grid = Arrays.copyOf(baseGrid, baseGrid.length);

        overlayTitans(grid, context, t);

        double startX = t.X + t.width / 2.0;
        double startY = t.Y + t.height / 2.0;

        int[][] path = computePath(startX, startY, t.marchingOrderX, t.marchingOrderY, grid);
        t.pathWaypoints = path;
        t.pathWaypointIdx = 0;
        t.pathAnchorX = t.X;
        t.pathAnchorY = t.Y;
        t.pathAnchorBoosting = t.isBoosting;
        t.pathAnchorOrderX = t.marchingOrderX;
        t.pathAnchorOrderY = t.marchingOrderY;
    }
}
