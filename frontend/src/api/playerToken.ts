const STORAGE_PREFIX = 'lupus:playerToken:'

/**
 * Player tokens are secrets returned exactly once by the backend, right
 * after joining a room. We persist them in sessionStorage (keyed by
 * room code) so a page refresh on the player's waiting/role screen
 * doesn't lock them out of fetching their own role.
 */
export function storePlayerToken(code: string, playerId: number, token: string): void {
  sessionStorage.setItem(STORAGE_PREFIX + code, JSON.stringify({ playerId, token }))
}

export function getPlayerToken(code: string): { playerId: number; token: string } | null {
  const raw = sessionStorage.getItem(STORAGE_PREFIX + code)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}
