# Agent instructions (Gemini)

## Server Builds & Testing
- **DO NOT** run `mvn` or `maven` commands directly on the host / Windows machine.
- If you need to build or run tests for the Java backend, run them inside a Docker container (e.g. `docker-compose up --build -d` or `docker run --rm ... maven:... mvn test`) or do not run them directly at all.
- `git diff`, `git stat`, and `git status` are read-only inspection commands and are always approved.

## Web client development

When running the browser client locally:

```bash
docker-compose up --build -d
cd client-web
npm run build   # run before starting — catches compile-time issues
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` and `/game` (WebSocket) to the Docker server on port 3030.

## Tournament Strings & GameOptions in Practice

Tournament strings configure match rules, team sizes, and pacing via slash-delimited tokens (e.g. `"/1/0/1/10/2/9999/10/12/2/1"`).

### Token Format & Meaning
When parsed by `new GameOptions(tournamentCode)` on the server, tokens are mapped directly:
- `split[1]` - **`playerIndex`**: Index into `playersVal = [3, 4, 5, 0, 1, 2, 6, 7, 8, ...]`.
  - `0`: 3v3 (3 per team)
  - `1`: 4v4 (Goalie + 3 field titans per team = 8 players total)
  - `4`: 1v1
- `split[2]` - **`goalieIndex`**: Goalies enabled mode (`0` = Goalies on, `1` = Goalies off, `2` = Permanent goalies).
- `split[3]` - **`bestOfIndex`**: Match series length (`1` = Best of 1).
- `split[4]` - **`playToIndex`** *(CRITICAL: Literal Value)*: The soft point goal required to win (e.g. `10` = first to 10 points). **Not** an array index. If set to `0`, matches end on the very first 0.25-point sidegoal!
- `split[5]` - **`winByIndex`** *(CRITICAL: Literal Value)*: Margin of victory required with soft win (e.g. `2` = win by 2 points). **Not** an array index.
- `split[6]` - **`hardWinIndex`** *(CRITICAL: Literal Value)*: Hard score limit where a match terminates immediately regardless of point differential (e.g. `9999` = disabled, or `20` = mercy/blowout limit).
- `split[7]` - **`suddenDeathIndex`** *(CRITICAL: Literal Minutes)*: Time in minutes before sudden death triggers (`framesSinceStart / FPS > suddenDeathIndex * 60`). If set to `0`, sudden death activates immediately at tick 0 and any non-tied score ends the match on the next frame. Use `10` for 10:00 minutes.
- `split[8]` - **`tieIndex`** *(CRITICAL: Literal Minutes)*: Time in minutes before an automatic draw is forced (e.g. `20` for 20:00 minutes).
- `split[9]` *(Optional)* - **`aiDifficultyIndex`**: AI reaction speed tier (`0` = Easy 1200–1700ms, `1` = Medium 500–1200ms, `2` = Hard 200–700ms).
- `split[10]` *(Optional)* - **`isHybrid`**: `1` or `true` activates hybrid/bot slot reservation and AI tactics.

### Canonical Examples
- **Standard 4v4 Headless Balance Benchmark**:
  `"/1/0/1/10/2/9999/10/20/2/1"` (4v4, Goalies on, Bo1, Play to 10, Win by 2, Hard win OFF, Sudden death at 10:00, Hardcore sudden death at 15:00, Draw at 20:00, Hard AI difficulty, Hybrid bot mode).
- **Default 1v1 Scrimmage**:
  `"/4/1/1/5/2/9999/10/20"` (1v1 scrimmage, play to 5, win by 2, Sudden death at 10:00, Draw at 20:00).


