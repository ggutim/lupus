export type GameMode = 'CLASSIC'

export type Role = 'VILLAGER' | 'WEREWOLF' | 'PRIEST'

export type RoleCounts = Record<Role, number>

export type RoomStatus = 'WAITING_FOR_PLAYERS' | 'STARTED'

export interface CreateRoomRequest {
  gameMode: GameMode
  playerCount: number
  werewolfCount: number
  priestCount: number
}

export interface Room {
  code: string
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

export class ApiError extends Error {
  fieldErrors?: Record<string, string>

  constructor(message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.fieldErrors = fieldErrors
  }
}

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function handleErrorResponse(response: Response, fallbackMessage: string): Promise<never> {
  const body = await response.json().catch(() => null)
  throw new ApiError(body?.message ?? fallbackMessage, body?.fieldErrors)
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

export async function getRoomState(code: string): Promise<RoomState> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}`)

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile trovare la stanza.')
  }

  return response.json()
}

export async function joinRoom(code: string, nickname: string): Promise<Player> {
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

export async function kickPlayer(code: string, playerId: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/players/${playerId}`, {
    method: 'DELETE',
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile rimuovere il giocatore. Riprova.')
  }
}
