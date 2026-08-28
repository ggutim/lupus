import { apiFetch, type Role } from './rooms'

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
  /** Whether this player currently holds the mayor status — orthogonal to role, since a successor keeps their own. */
  mayor: boolean
  /** Only meaningful when mayor is true: whether they've announced themselves to the table. */
  mayorRevealed: boolean
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
  /** Set once the current mayor has died and someone else is alive to inherit the card — see assignMayorSuccessor. */
  pendingMayorSuccessionPlayerId: number | null
}

export function getGameState(code: string, masterToken: string): Promise<MasterGameState> {
  return apiFetch<MasterGameState>(`/api/rooms/${code}/game`, 'Impossibile recuperare lo stato della partita.', {
    headers: { 'X-Master-Token': masterToken },
  })
}

export function advancePhase(code: string, masterToken: string): Promise<MasterGameState> {
  return apiFetch<MasterGameState>(`/api/rooms/${code}/game/advance`, 'Impossibile avanzare alla fase successiva.', {
    method: 'POST',
    headers: { 'X-Master-Token': masterToken },
  })
}

function selectTarget(
  path: string,
  code: string,
  masterToken: string,
  playerId: number | null,
): Promise<MasterGameState> {
  return apiFetch<MasterGameState>(`/api/rooms/${code}/game/${path}`, 'Impossibile registrare la selezione.', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Master-Token': masterToken,
    },
    body: JSON.stringify({ playerId }),
  })
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
export function revealKillerAndGuess(
  code: string,
  masterToken: string,
  targetPlayerId: number,
  guessedRole: Role,
): Promise<KillerGuessResult> {
  return apiFetch<KillerGuessResult>(
    `/api/rooms/${code}/game/killer-guess`,
    'Impossibile registrare la rivelazione del killer.',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Master-Token': masterToken,
      },
      body: JSON.stringify({ targetPlayerId, guessedRole }),
    },
  )
}

/** The mayor's optional day-time reveal: a one-way switch announcing who currently holds the card. */
export function revealMayor(code: string, masterToken: string): Promise<MasterGameState> {
  return apiFetch<MasterGameState>(
    `/api/rooms/${code}/game/mayor-reveal`,
    'Impossibile registrare la rivelazione del sindaco.',
    { method: 'POST', headers: { 'X-Master-Token': masterToken } },
  )
}

/** Resolves a pending mayor succession: the dead mayor hands their card to successorPlayerId. */
export function assignMayorSuccessor(
  code: string,
  masterToken: string,
  successorPlayerId: number,
): Promise<MasterGameState> {
  return apiFetch<MasterGameState>(
    `/api/rooms/${code}/game/mayor-succession`,
    'Impossibile registrare il nuovo sindaco.',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Master-Token': masterToken,
      },
      body: JSON.stringify({ successorPlayerId }),
    },
  )
}
