# QA simulator

Drives a real, visible Chrome window through the master's screens end to
end: creates a narrate-only room, manually deals player names and roles
(so you can force a specific role onto a player and go watch it), starts
the game, then randomly plays through night/day phases until the game
ends.

It only exercises the master flow (create → roster → game) — it does not
open extra tabs to simulate players joining from their phones.

## Setup

```bash
npm install
```

(`npx playwright install chromium` runs automatically via `postinstall`.)

Make sure the app stack is running first:

```bash
cd .. && docker compose up --build -d
```

## Usage

From this directory:

```bash
./run.sh --players 10 --werewolves 2 --force PRIEST
./run.sh --force PRIEST --force GRAVEDIGGER --delay 900
./run.sh --help
```

Or:

```bash
npm run sim -- --players 10 --werewolves 2 --force PRIEST
```

| Flag | Default | Notes |
|---|---|---|
| `--base-url` | `http://lupus.localhost` | |
| `--players` | `8` | 4-30 |
| `--werewolves` | `1` | |
| `--priests` | `1` | |
| `--gravediggers` | `0` | |
| `--idiots` | `0` | |
| `--corrupted-judges` | `0` | 0-1 |
| `--survivors` | `0` | |
| `--force <ROLE>` | — | Forces this role onto a specific player. Repeatable. Auto-bumps that role's count if needed. The forced player(s) are logged at the top of the run so you know who to watch. Selection during play is still fully random — a forced role can still get killed/voted out like anyone else. |
| `--delay` | `500` (ms) | Pause between simulated actions — the whole point is to be able to watch it. |
| `--seed` | random | Pass a fixed number to reproduce the exact same shuffle/decisions from a previous run. |

The browser stays open after the run (or after a failure) so you can
inspect the final state — press Ctrl+C in the terminal to close it.
