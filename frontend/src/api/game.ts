import { ApiError, API_BASE_URL, type Role } from './rooms'

export type GamePhase =
  | 'ROLES_ASSIGNED'
  | 'NIGHT_START'
  | 'NIGHT_ACTIONS'
  | 'MORNING_REVEAL'
  | 'DISCUSSION'
  | 'VOTE_SELECT_TARGET'
  | 'GAME_OVER'

export type NightStepKind = 'WAKE_UP' | 'SELECT'

export type Alignment = 'GOOD' | 'EVIL'

export interface MasterPlayerView {
  id: number
  nickname: string
  alive: boolean
  role: Role
  /** Afterlife mode only: what this player was before dying and becoming a ghost/angel. */
  originalRole: Role | null
  /** Afterlife mode only: permanently true once an angel has protected this player while cursed. */
  protectionBlocked: boolean
  /** Killer only: whether they've already used their once-per-game reveal-and-guess power. */
  killerRevealUsed: boolean
}

export interface MasterGameState {
  code: string
  phase: GamePhase
  roundNumber: number
  players: MasterPlayerView[]
  currentNightRole: Role | null
  currentNightStepKind: NightStepKind | null
  pendingNightActionTargetId: number | null
  /** Afterlife mode only: the ghosts' second curse target, alongside pendingNightActionTargetId as the first. */
  secondPendingNightActionTargetId: number | null
  nightActionResult: Alignment | null
  /** Whether nightActionResult was flipped because the target is currently cursed by the ghosts. */
  nightActionResultCursed: boolean
  /** Guardian's turn only: the player they protected last round, who can't be selected again this round. */
  guardianBlockedPlayerId: number | null
  lastNightVictimIds: number[]
  pendingVoteVictimId: number | null
  winner: Alignment | null
  winningRole: Role | null
  remoteJoin: boolean
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

export function selectNightTarget(code: string, masterToken: string, playerId: number): Promise<MasterGameState> {
  return selectTarget('select-night-target', code, masterToken, playerId)
}

export function selectVoteVictim(
  code: string,
  masterToken: string,
  playerId: number | null,
): Promise<MasterGameState> {
  return selectTarget('select-vote-victim', code, masterToken, playerId)
}

export interface KillerGuessResult {
  correct: boolean
  gameState: MasterGameState
}

/** The killer's once-per-game power: reveal themselves and guess targetPlayerId's exact role, in one shot. */
export async function revealKillerAndGuess(
  code: string,
  masterToken: string,
  targetPlayerId: number,
  guessedRole: Role,
): Promise<KillerGuessResult> {
  const response = await fetch(`${API_BASE_URL}/api/rooms/${code}/game/killer-guess`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Master-Token': masterToken,
    },
    body: JSON.stringify({ targetPlayerId, guessedRole }),
  })

  if (!response.ok) {
    await handleErrorResponse(response, 'Impossibile registrare la rivelazione del killer.')
  }

  return response.json()
}
