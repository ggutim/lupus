const STORAGE_PREFIX = 'lupus:masterToken:'

/**
 * Master tokens are secrets returned exactly once by the backend, right
 * after room creation. We persist them in sessionStorage (keyed by room
 * code) so a page refresh on the master's room screen doesn't lock them
 * out of master-only actions like kicking players.
 */
export function storeMasterToken(code: string, token: string): void {
  sessionStorage.setItem(STORAGE_PREFIX + code, token)
}

export function getMasterToken(code: string): string | null {
  return sessionStorage.getItem(STORAGE_PREFIX + code)
}
