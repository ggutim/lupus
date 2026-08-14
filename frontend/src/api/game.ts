import { ApiError, API_BASE_URL, type Role } from './rooms'

export type GamePhase =
  | 'ROLES_ASSIGNED'
  | 'NIGHT_START'
  | 'WEREWOLVES_WAKE_UP'
  | 'WEREWOLVES_SELECT_VICTIM'
  | 'PRIEST_WAKE_UP'
  | 'PRIEST_SELECT_TARGET'
  | 'MORNING_REVEAL'
  | 'DISCUSSION'
  | 'VOTE_SELECT_TARGET'
  | 'GAME_OVER'

export type Alignment = 'GOOD' | 'EVIL'

export interface MasterPlayerView {
  id: number
  nickname: string
  alive: boolean
  role: Role
}

export interface MasterGameState {
  code: string
  phase: GamePhase
  roundNumber: number
  players: MasterPlayerView[]
  pendingWerewolfVictimId: number | null
  pendingPriestTargetId: number | null
  priestCheckResult: Alignment | null
  lastNightVictimId: number | null
  pendingVoteVictimId: number | null
  winner: Alignment | null
}

async function handleErrorResponse(response: Response, fallbackMessage: string): Promise<never> {
  const body = await response.json().catch(() => null)
  throw new ApiError(response.status, body?.message ?? fallbackMessage, body?.fieldErrors)
}

export async function getGameState(code: string, masterToken: string): Promise<MasterGameState> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/game`, {
    headers: { 'X-Master-Token': masterToken },
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile recuperare lo stato della partita.')
  }

  return response.json()
}

export async function advancePhase(code: string, masterToken: string): Promise<MasterGameState> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/game/advance`, {
    method: 'POST',
    headers: { 'X-Master-Token': masterToken },
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile avanzare alla fase successiva.')
  }

  return response.json()
}

async function selectTarget(
  path: string,
  code: string,
  masterToken: string,
  playerId: number | null,
): Promise<MasterGameState> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/game/${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Master-Token': masterToken,
    },
    body: JSON.stringify({ playerId }),
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile registrare la selezione.')
  }

  return response.json()
}

export function selectWerewolfVictim(code: string, masterToken: string, playerId: number): Promise<MasterGameState> {
  return selectTarget('select-werewolf-victim', code, masterToken, playerId)
}

export function selectPriestTarget(code: string, masterToken: string, playerId: number): Promise<MasterGameState> {
  return selectTarget('select-priest-target', code, masterToken, playerId)
}

export function selectVoteVictim(
  code: string,
  masterToken: string,
  playerId: number | null,
): Promise<MasterGameState> {
  return selectTarget('select-vote-victim', code, masterToken, playerId)
}
