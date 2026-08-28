export type GameMode = 'CLASSIC' | 'AFTERLIFE'

export type Role =
  | 'VILLAGER'
  | 'WEREWOLF'
  | 'PRIEST'
  | 'GRAVEDIGGER'
  | 'IDIOT'
  | 'CORRUPTED_JUDGE'
  | 'SURVIVOR'
  | 'GHOST'
  | 'ANGEL'
  | 'GUARDIAN'
  | 'KILLER'
  | 'MAYOR'

export type RoleCounts = Record<Role, number>

export type RoomStatus = 'WAITING_FOR_PLAYERS' | 'STARTED'

export interface CreateRoomRequest {
  gameMode: GameMode
  playerCount: number
  werewolfCount: number
  priestCount: number
  gravediggerCount: number
  idiotCount: number
  corruptedJudgeCount: number
  survivorCount: number
  guardianCount: number
  killerCount: number
  mayorCount: number
  remoteJoin: boolean
  manualRoles: boolean
}

export interface Room {
  code: string
  masterToken: string
  gameMode: GameMode
  playerCount: number
  roleCounts: RoleCounts
  remoteJoin: boolean
  manualRoles: boolean
}

export interface Player {
  id: number
  nickname: string
}

export interface RoomState {
  code: string
  status: RoomStatus
  playerCount: number
  players: Player[]
}

export interface ManualPlayer {
  id: number
  nickname: string
  role: Role | null
}

export interface MasterRoomState {
  code: string
  status: RoomStatus
  playerCount: number
  remoteJoin: boolean
  manualRoles: boolean
  roleCounts: RoleCounts
  players: ManualPlayer[]
}

export interface JoinRoomResponse {
  id: number
  nickname: string
  playerToken: string
}

export class ApiError extends Error {
  status: number
  fieldErrors?: Record<string, string>

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function handleErrorResponse(response: Response, fallbackMessage: string): Promise<never> {
  const body = await response.json().catch(() => null)
  throw new ApiError(response.status, body?.message ?? fallbackMessage, body?.fieldErrors)
}

/**
 * Shared `fetch → check status → parse JSON` shape every API call in
 * this module and `api/game.ts` follows. `path` is relative to {@link
 * API_BASE_URL}; `fallbackMessage` is shown when the error response
 * carries no message of its own. Use {@link apiFetchVoid} for
 * endpoints with no response body (a 200/204 with nothing to parse).
 */
export async function apiFetch<T>(path: string, fallbackMessage: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init)
  if (!response.ok) {
    await handleErrorResponse(response, fallbackMessage)
  }
  return response.json()
}

/** Like {@link apiFetch}, for endpoints whose success response has no body worth parsing. */
export async function apiFetchVoid(path: string, fallbackMessage: string, init?: RequestInit): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${path}`, init)
  if (!response.ok) {
    await handleErrorResponse(response, fallbackMessage)
  }
}

export function createRoom(request: CreateRoomRequest): Promise<Room> {
  return apiFetch<Room>('/api/rooms', 'Impossibile creare la stanza. Riprova.', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

export function getRoomState(code: string, masterToken: string): Promise<RoomState> {
  return apiFetch<RoomState>(`/api/rooms/${code}`, 'Impossibile trovare la stanza.', {
    headers: { 'X-Master-Token': masterToken },
  })
}

/**
 * Same safe fields as {@link getRoomState} (no roles), but doesn't need
 * a master token — for a player's own client to catch up on room state
 * (has the game started?) whenever its WebSocket connects or
 * reconnects, in case a push was missed. Used by `subscribeToRoom`.
 */
export function getPublicRoomState(code: string): Promise<RoomState> {
  return apiFetch<RoomState>(`/api/rooms/${code}/status`, 'Impossibile trovare la stanza.')
}

export function joinRoom(code: string, nickname: string): Promise<JoinRoomResponse> {
  return apiFetch<JoinRoomResponse>(`/api/rooms/${code}/players`, 'Impossibile unirsi alla stanza. Riprova.', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  })
}

export interface PlayerRoleState {
  role: Role
  alive: boolean
  /** Whether this player currently holds the mayor status — always visible to them, whatever their role. */
  mayor: boolean
}

export interface VillagePlayer {
  id: number
  nickname: string
  alive: boolean
  /** Non-null only once made public by an in-game reveal (the killer's guess, or the mayor's reveal). */
  revealedRole: Role | null
  /** True only when this player currently holds the mayor status and that fact is itself public. */
  mayor: boolean
}

export async function getVillageOverview(code: string): Promise<VillagePlayer[]> {
  const body = await apiFetch<{ players: VillagePlayer[] }>(
    `/api/rooms/${code}/village`,
    'Impossibile recuperare lo stato del villaggio.',
  )
  return body.players
}

export function getPlayerRole(code: string, playerId: number, playerToken: string): Promise<PlayerRoleState> {
  return apiFetch<PlayerRoleState>(`/api/rooms/${code}/players/${playerId}/role`, 'Impossibile recuperare il tuo ruolo.', {
    headers: { 'X-Player-Token': playerToken },
  })
}

export function addPlayerManually(
  code: string,
  masterToken: string,
  nickname: string,
  role?: Role,
): Promise<ManualPlayer> {
  return apiFetch<ManualPlayer>(`/api/rooms/${code}/players/manual`, 'Impossibile aggiungere il giocatore. Riprova.', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Master-Token': masterToken },
    body: JSON.stringify({ nickname, role: role ?? null }),
  })
}

export function getRoomRoster(code: string, masterToken: string): Promise<MasterRoomState> {
  return apiFetch<MasterRoomState>(`/api/rooms/${code}/roster`, 'Impossibile trovare la stanza.', {
    headers: { 'X-Master-Token': masterToken },
  })
}

export function kickPlayer(code: string, playerId: number, masterToken: string): Promise<void> {
  return apiFetchVoid(`/api/rooms/${code}/players/${playerId}`, 'Impossibile rimuovere il giocatore. Riprova.', {
    method: 'DELETE',
    headers: { 'X-Master-Token': masterToken },
  })
}

export function startGame(code: string, masterToken: string): Promise<void> {
  return apiFetchVoid(`/api/rooms/${code}/start`, 'Impossibile avviare la partita. Riprova.', {
    method: 'POST',
    headers: { 'X-Master-Token': masterToken },
  })
}
