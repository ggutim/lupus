export type GameMode = 'CLASSIC'

export type Role = 'VILLAGER' | 'WEREWOLF' | 'PRIEST' | 'GRAVEDIGGER' | 'IDIOT' | 'CORRUPTED_JUDGE'

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
}

export interface Room {
  code: string
  masterToken: string
  gameMode: GameMode
  playerCount: number
  roleCounts: RoleCounts
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

export async function createRoom(request: CreateRoomRequest): Promise<Room> {
  const response = await fetch(`${API_BASE_URL}/api/rooms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile creare la stanza. Riprova.')
  }

  return response.json()
}

export async function getRoomState(code: string, masterToken: string): Promise<RoomState> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}`, {
    headers: { 'X-Master-Token': masterToken },
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile trovare la stanza.')
  }

  return response.json()
}

export async function joinRoom(code: string, nickname: string): Promise<JoinRoomResponse> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/players`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile unirsi alla stanza. Riprova.')
  }

  return response.json()
}

export interface PlayerRoleState {
  role: Role
  alive: boolean
}

export interface VillagePlayer {
  id: number
  nickname: string
  alive: boolean
}

export async function getVillageOverview(code: string): Promise<VillagePlayer[]> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/village`)

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile recuperare lo stato del villaggio.')
  }

  const body = await response.json()
  return body.players
}

export async function getPlayerRole(code: string, playerId: number, playerToken: string): Promise<PlayerRoleState> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/players/${playerId}/role`, {
    headers: { 'X-Player-Token': playerToken },
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile recuperare il tuo ruolo.')
  }

  return response.json()
}

export async function kickPlayer(code: string, playerId: number, masterToken: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/players/${playerId}`, {
    method: 'DELETE',
    headers: { 'X-Master-Token': masterToken },
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile rimuovere il giocatore. Riprova.')
  }
}

export async function startGame(code: string, masterToken: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/start`, {
    method: 'POST',
    headers: { 'X-Master-Token': masterToken },
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile avviare la partita. Riprova.')
  }
}
