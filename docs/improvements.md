# Improvements

Known gaps and shortcuts taken deliberately to keep early slices small.
Not urgent, but should be revisited before this goes further than friends
testing it.

## Master authorization

**Status:** Implemented.

The master receives an opaque, cryptographically random token (32 bytes,
URL-safe base64) in the response body of `POST /api/rooms`, once, at room
creation time. Master-only endpoints now require it via the
`X-Master-Token` header:

- `GET /api/rooms/{code}` — room state (used by the master's live view)
- `DELETE /api/rooms/{code}/players/{playerId}` — kick a player

A missing or mismatched token returns `403 Forbidden`
(`MasterTokenMismatchException`). Joining a room (`POST
/api/rooms/{code}/players`) remains unauthenticated by design — the room
code is the only credential a player needs.

The frontend stores the token in `sessionStorage`, keyed by room code, so
a page refresh on the room-created screen doesn't lock the master out.
Since it's session-scoped, opening the room-created URL in a new tab or
after closing the browser will not restore master access — the user is
redirected home with a message in that case. There is currently no way
to recover a lost master token (by design, since it's a secret only
returned once); if that turns out to be too strict for real usage,
consider a "claim this room" recovery flow tied to something else (e.g.
device/browser fingerprint) — deliberately not built yet since it adds
real complexity for a low-stakes, friends-only game.

**Known follow-ups, still open:**
- No token rotation/expiry — a room's master token is valid for as long
  as the room row exists (no cleanup job for old rooms yet either).
- No multi-device master support — if the master wants to control the
  room from two devices/tabs, only the one that created the room (or
  that had `sessionStorage` synced some other way) can act as master.

## Player count bounds: frontend/backend drift risk

**Status:** Backend implemented as configuration; frontend still hardcoded.

The backend's min/max player count (`GameRules`) is now a
`@ConfigurationProperties` bean bound to `lupus.game.min-players` /
`lupus.game.max-players` in `application.yaml` (overridable via env vars,
e.g. `LUPUS_GAME_MIN_PLAYERS`). The frontend still has its own hardcoded
copies in `frontend/src/api/gameRules.ts` used to bound the room-creation
wizard's stepper.

If a deployment ever overrides the backend values away from the
defaults (4–30), the frontend wizard will disagree with what the server
actually accepts — e.g. the wizard might let the master pick 4 players
while the server (configured with `min-players: 6`) rejects it, or vice
versa.

**Suggested fix:** expose the effective min/max via a small public
endpoint (e.g. `GET /api/rooms/rules`) that the frontend fetches once on
load, instead of hardcoding the range client-side. Not done yet since no
deployment currently overrides the defaults.

## Game phase engine: known limitations

**Status:** Implemented for a single werewolf + priest ruleset. Several
things were intentionally deferred:

- **No new-role extensibility beyond alignment.** `Alignment` (GOOD/EVIL)
  drives the priest's check and win conditions generically, but the
  phase sequence itself (`GamePhase` enum) is hardcoded for exactly one
  werewolf-style role and one priest-style role. Adding a genuinely new
  role with its own night action (e.g. a bodyguard) requires extending
  `GamePhase` and `GameService.advancePhase`'s switch statement by hand
  — there's no generic "night action" abstraction yet.
- **No action if the master refreshes mid-selection.** The pending
  selections (`pendingWerewolfVictimId`, etc.) are persisted on `Room`,
  so a master's page refresh during `MasterGamePage` correctly restores
  state via `getGameState`. This was verified manually but doesn't have
  an automated test.
- **No player-facing live updates during the game.** Per an explicit
  product decision, players only ever see a single static "your role"
  screen after the game starts — no notifications when they die, no
  "the game has ended" banner, nothing. All of that is communicated
  verbally by the master at the table. This is intentional for now, but
  flagged in case it becomes a rough edge for solo/remote play in the
  future.
- **Tie-breaking on the EVIL-wins condition uses `>=`.** If alive
  werewolves equal alive villagers, EVIL wins immediately (matches the
  answer given when this was designed), but this hasn't been validated
  against how your group's house rules define a "tie" — worth
  double-checking against the physical rulebook you use.
