export type GameMode = 'CLASSIC'

export type Role = 'VILLAGER' | 'WEREWOLF' | 'PRIEST'

export type RoleCounts = Record<Role, number>

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

export class ApiError extends Error {
  fieldErrors?: Record<string, string>

  constructor(message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.fieldErrors = fieldErrors
  }
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export async function createRoom(request: CreateRoomRequest): Promise<Room> {
  const response = await fetch(`${API_BASE_URL}/api/rooms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(
      body?.message ?? 'Impossibile creare la stanza. Riprova.',
      body?.fieldErrors,
    )
  }

  return response.json()
}
