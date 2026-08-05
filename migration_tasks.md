# TitanBall V2 — Subagent Task Specifications

This file documents every parallelizable workstream as a self-contained task specification.
Each section can be handed to an agent (or new conversation) as a standalone prompt.

The implementation plan is in the same repo at `.agents/implementation_plan.md` (if copied) or in the Antigravity artifact directory.

---

## Task 1A: CollisionMath + Remove JavaFX from Server

**Goal**: Remove all `javafx.*` dependencies from the server-side game engine code and replace them with pure-math equivalents.

**Context**: `GameEngine.java` (1839 LOC) imports `javafx.geometry.Bounds`, `javafx.scene.shape.Ellipse`, and `javafx.scene.shape.Rectangle` for collision detection. These must be replaced because the server will run in a headless Docker container without JavaFX.

**Files to create**:

1. `src/gameserver/engine/CollisionMath.java` — New utility class with:
   - `public record EllipseData(double centerX, double centerY, double radiusX, double radiusY)`
   - `public record Bounds(double minX, double minY, double width, double height)`
   - `public static boolean ellipseBoundsIntersect(EllipseData e1, EllipseData e2)` — checks if the bounding boxes of two ellipses overlap (this is what the original code does via `e.getBoundsInLocal().intersects(...)`)
   - `public static boolean boundsIntersect(Bounds b1, Bounds b2)` — AABB overlap check

**Files to modify**:

1. `src/gameserver/engine/GameEngine.java`:
   - Remove imports: `javafx.geometry.Bounds`, `javafx.scene.shape.Ellipse`, `javafx.scene.shape.Rectangle`
   - `ballIntersectsEllipse(GoalHoop goal)` at ~line 175: Replace `Ellipse g = goal.ellipseCentered(); Ellipse b = ball.ellipseCentered(); return b.getBoundsInLocal().intersects(g.getBoundsInLocal());` with `CollisionMath.EllipseData g = goal.ellipseData(); CollisionMath.EllipseData b = ball.ellipseData(); return CollisionMath.ellipseBoundsIntersect(b, g);`
   - `minorHoopBounce()` at ~line 240: Replace `Bounds ballBounds = ball.asBounds();` with `CollisionMath.Bounds ballBounds = ball.asBounds();` and replace `Ellipse ell = goal.ellipseCentered();` / `ell.intersects(ballBounds)` with the CollisionMath equivalent. The `ell.getCenterX()` / `ell.getCenterY()` calls become `ellData.centerX()` / `ellData.centerY()`.
   - Search for any other `javafx` usage with `grep -n "javafx" GameEngine.java` and fix all.
   - Also remove imports of `client.graphical.GoalSprite` and `client.graphical.ScreenConst` — move any constants used from `ScreenConst` into `Const.java` or inline them.

2. `src/gameserver/engine/GoalHoop.java`:
   - Change `ellipseCentered()` method: currently returns `javafx.scene.shape.Ellipse`, change to return `CollisionMath.EllipseData`
   - Remove `javafx` imports

3. `src/gameserver/entity/Box.java`:
   - Change `asBounds()` method: currently returns `javafx.geometry.Bounds` (likely `BoundingBox`), change to return `CollisionMath.Bounds`
   - Change `ellipseCentered()` if it exists: return `CollisionMath.EllipseData`
   - Remove `javafx` imports

4. `src/gameserver/targeting/ShapePayload.java`:
   - Check for any `javafx` shape imports and replace with pure-math equivalents

**Verification**: After changes, run `grep -rn "javafx" src/gameserver/` — should return zero results.

---

## Task 1B: Jackson JSON Serialization for All Game State Classes

**Goal**: Make the entire `Game` object graph serializable to JSON via Jackson `ObjectMapper`, replacing Kryo binary serialization.

**Context**: The server currently serializes the full `GameEngine extends Game` object via KryoNet (Kryo binary). For the browser client, this must be JSON. Jackson is already a dependency (`com.fasterxml.jackson.core:jackson-databind:2.13.5`).

**Key constraint**: The `Game` class and all its nested types must round-trip through `objectMapper.writeValueAsString(game)` and `objectMapper.readValue(json, Game.class)` without error.

**Files to modify** (add `@JsonIgnoreProperties(ignoreUnknown = true)` to class level, ensure default no-arg constructors exist, add `@JsonIgnore` on non-serializable fields):

1. `src/gameserver/models/Game.java`:
   - Add `@JsonIgnore` on `locked` (AtomicBoolean)
   - Add `@JsonIgnore` on `c` (Const — file reader, not serializable)
   - Replace `public Instant now;` (Joda) with `public long nowEpochMs;` — update all usages in `ManagedGame.java` that set `update.now = Instant.now()` to `update.nowEpochMs = System.currentTimeMillis()`
   - Remove `implements Serializable`
   - Add `@JsonTypeInfo` and `@JsonSubTypes` on the `players` field if needed for polymorphic Titan deserialization (probably not needed since Titan is concrete)

2. `src/gameserver/entity/Entity.java` — Remove `Serializable`, add `@JsonIgnoreProperties(ignoreUnknown = true)`, ensure default constructor
3. `src/gameserver/entity/Titan.java` — Add `@JsonIgnoreProperties(ignoreUnknown = true)`, ensure enums serialize as strings
4. `src/gameserver/entity/Box.java` — Add `@JsonIgnoreProperties(ignoreUnknown = true)`
5. `src/gameserver/entity/RangeCircle.java` — Ensure serializable
6. `src/gameserver/entity/Coordinates.java` — Ensure serializable
7. `src/gameserver/effects/EffectPool.java` — Add `@JsonIgnoreProperties(ignoreUnknown = true)`
8. `src/gameserver/effects/effects/*.java` — Add `@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)` on base `Effect` class for polymorphic serialization. Ensure all have no-arg constructors.
9. `src/gameserver/effects/cooldowns/*.java` — Same as effects
10. `src/gameserver/entity/minions/*.java` (BallPortal, Cage, Fire, Portal, Trap, Wall, Wolf) — Ensure no-arg constructors
11. `src/gameserver/engine/GoalHoop.java`, `Team.java`, `StatEngine.java`, `Masteries.java`, `GameOptions.java` — Ensure JSON-serializable
12. `src/gameserver/targeting/ShapePayload.java`, `Targeting.java` — Ensure serializable
13. `src/networking/ClientPacket.java` — Remove `Serializable`
14. `src/networking/PlayerDivider.java` — Remove `Serializable`, `@JsonIgnore` on Connection fields
15. `src/networking/KeyDifferences.java` — Remove `Serializable`
16. `src/gameserver/gamemanager/GamePhase.java` — Remove `Serializable`

**Verification**: Create a test that serializes a `GameEngine` to JSON and back without errors.

---

## Task 1C: WebSocket Networking + Server Refactoring

**Goal**: Replace KryoNet TCP with Spring WebSocket, merge into single Spring Boot app on port 8080.

**Prerequisites**: Tasks 1A and 1B must be complete first.

**Files to create**:

1. `src/gameserver/gamemanager/WebSocketConfig.java` — Spring `@Configuration` + `@EnableWebSocket`, registers `GameWebSocketHandler` at `/game`, allows origins `blockforger.net` and `localhost:5173`

2. `src/gameserver/gamemanager/GameWebSocketHandler.java` — `@Component` extending `TextWebSocketHandler`:
   - `afterConnectionEstablished()`: Log new connection
   - `handleTextMessage()`: Deserialize JSON → `ClientPacket`, validate JWT, call `ServerApplication.delegatePacket()`
   - `afterConnectionClosed()`: Cleanup

3. `src/networking/WebSocketPlayerConnection.java` — Extends `PlayerDivider`, wraps `WebSocketSession`:
   - `isConnected()` → `session.isOpen()`
   - `sendJson(String json)` → `session.sendMessage(new TextMessage(json))`
   - `id` field set to `session.getId().hashCode()`

**Files to modify**:

1. `ServerApplication.java` — Delete `main()`, keep `states`/`addNewGame`/`delegatePacket`/`checkGameExpiry`, remove KryoNet imports
2. `ManagedGame.java` — Replace `PlayerConnection` with `WebSocketPlayerConnection`, replace `deepClone()` with Jackson JSON clone, replace `sendTCP` with `sendJson`
3. `LoginController.java` — Prefix endpoints with `/api/`, add CORS, add `/api/health`
4. `SpringSecurityConfig.java` — Allow `/game` and `/api/health`, configure CORS
5. `pom.xml` — Remove JavaFX/KryoNet/cloning deps, add `spring-boot-starter-websocket`, change `finalName`
6. `application.properties` — Port 8080, remove SSL, env var overrides, `titanball` database name

**Files to delete**: All 10 `client/` files + 4 KryoNet networking files (see list above)

---

## Task 2: Browser Client (Full Greenfield)

**Goal**: Build a complete browser game client in `client-web/` using vanilla JS + HTML5 Canvas + Vite.

**Reference**: `src/client/TitanballClient.java` (2423 LOC) — read every method.

**Setup**: `npx -y create-vite@latest ./ --template vanilla` in `client-web/`

**Architecture**:
```
client-web/src/
├── main.js          # rAF loop, phase router
├── state.js         # game state holder
├── constants.js     # game.cfg values
├── network/auth.js  # REST client
├── network/socket.js # WebSocket client
├── input/keyboard.js + mouse.js + keybindings.js
├── render/canvas.js + field.js + goals.js + players.js + ball.js + minions.js + effects.js + hud.js + targeting.js
├── screens/credits.js + controls.js + gameModes.js + classSelect.js + masteries.js + lobby.js + countdown.js + draft.js + tournament.js + results.js + tutorial.js
├── assets/sprites.js + images.js + audio.js
└── util/math.js
```

**Asset strategy**: Copy or symlink `res/` into `client-web/public/res/`

**Critical**: Port the exact game phase flow from `TitanballClient.paint()`, all rendering from `doDrawing()`, and input handling from `handle()`.

---

## Task 3: Infrastructure

**Goal**: Dockerfile, DB init script, docker-compose for local dev.

**Files to create**:
1. `db/init.sql` — 3 tables (users, classstat, premadestats) + seed data from existing `Dockerfie`
2. `Dockerfile` — Multi-stage Maven build → JRE Alpine runtime
3. `docker-compose.yml` — MySQL 8 + game server services

---

## Execution Order

```
Task 1A (CollisionMath)  ──┐
Task 1B (Jackson)         ──┼──→ Task 1C (WebSocket) ──→ Server builds ✓
Task 3  (Infrastructure)  ──┘

Task 2  (Browser Client)  ───────────────────────────→ Integration test
```

Tasks 1A, 1B, 2, and 3 can all run in parallel.
Task 1C depends on 1A and 1B being complete.
Integration testing requires both server (1C) and client (2) complete.
