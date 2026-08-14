/**
 * Global constraints shared across the room-creation wizard and the
 * master's start-game action. Mirrors the backend's default
 * `lupus.game.min-players` / `lupus.game.max-players` values.
 *
 * NOTE: the backend now reads these from application.yaml (configurable
 * per-deployment), while these are still hardcoded here. If the backend
 * values are ever overridden away from the defaults, these must be
 * updated to match or the wizard's client-side bounds will disagree
 * with what the server actually accepts.
 */
export const MIN_PLAYERS = 4
export const MAX_PLAYERS = 30
