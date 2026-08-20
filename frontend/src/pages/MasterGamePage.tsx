import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  advancePhase,
  getGameState,
  selectNightTarget,
  selectVoteVictim,
  type Alignment,
  type MasterGameState,
  type MasterPlayerView,
} from '../api/game'
import { ApiError, type Role } from '../api/rooms'
import { subscribeToGame } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import VillageOverviewDialog from '../components/VillageOverviewDialog'
import { useDialog } from '../components/useDialog'
import { useMasterAccess } from '../components/useMasterAccess'
import { ROLE_ALIGNMENT, type RoleAlignment } from '../roleAlignment'
import { ROLE_LABELS } from '../roleLabels'
import {
  CorruptedJudgeIcon,
  EyeIcon,
  GravediggerIcon,
  MoonIcon,
  PriestIcon,
  SkullIcon,
  SunIcon,
  WerewolfIcon,
} from '../components/icons'
import type { ReactNode } from 'react'

interface CardContent {
  icon: ReactNode
  title: string
  body: ReactNode
  /** Colors the card icon by the acting role's side; omitted for narration-only cards (moon, sun, skull, eye). */
  align?: RoleAlignment
}

interface NightRoleContent {
  icon: ReactNode
  wakeUpTitle: string
  wakeUpBody: string
  selectTitle: string
  selectPrompt: string
  /** Renders the immediate result once a target and result exist (e.g. the priest's alignment reveal). */
  renderResult?: (targetName: string, result: Alignment) => ReactNode
}

/** Narration copy per role's night turn. Adding a role's night behavior means adding an entry here. */
const NIGHT_ROLE_CONTENT: Partial<Record<Role, NightRoleContent>> = {
  WEREWOLF: {
    icon: <WerewolfIcon />,
    wakeUpTitle: 'I lupi mannari si svegliano',
    wakeUpBody: 'I lupi aprono gli occhi e decidono in silenzio chi sbranare.',
    selectTitle: 'Chi hanno scelto i lupi?',
    selectPrompt: 'Seleziona dalla tavola il giocatore scelto dai lupi mannari.',
  },
  PRIEST: {
    icon: <PriestIcon />,
    wakeUpTitle: 'Il sacerdote si sveglia',
    wakeUpBody: 'Il sacerdote apre gli occhi e sceglie chi vedere.',
    selectTitle: 'Chi vuole vedere il sacerdote?',
    selectPrompt: 'Seleziona dalla tavola il giocatore scelto dal sacerdote.',
    renderResult: (targetName, result) => (
      <>
        <p>
          {targetName} è <strong>{result === 'EVIL' ? 'malvagio' : 'buono'}</strong>.
        </p>
        <p className="game-card-hint">Comunicalo in silenzio al sacerdote.</p>
      </>
    ),
  },
  GRAVEDIGGER: {
    icon: <GravediggerIcon />,
    wakeUpTitle: 'Il becchino si sveglia',
    wakeUpBody: 'Il becchino apre gli occhi e sceglie un morto da esaminare, se ce n\'è uno.',
    selectTitle: 'Chi vuole esaminare il becchino?',
    selectPrompt: 'Seleziona dalla tavola un giocatore morto, oppure avanza se non vuole scegliere.',
    renderResult: (targetName, result) => (
      <>
        <p>
          {targetName} era <strong>{result === 'EVIL' ? 'malvagio' : 'buono'}</strong>.
        </p>
        <p className="game-card-hint">Comunicalo in silenzio al becchino.</p>
      </>
    ),
  },
  CORRUPTED_JUDGE: {
    icon: <CorruptedJudgeIcon />,
    wakeUpTitle: 'Il giudice corrotto si sveglia',
    wakeUpBody: 'Nessuno è stato eliminato oggi: il giudice corrotto apre gli occhi e decide se colpire.',
    selectTitle: 'Chi ha scelto il giudice corrotto?',
    selectPrompt: 'Seleziona dalla tavola il giocatore scelto, oppure avanza se non vuole scegliere.',
  },
}

function playerName(players: MasterPlayerView[], id: number | null): string {
  if (id === null) return '—'
  return players.find((player) => player.id === id)?.nickname ?? '—'
}

function buildNightActionsCard(state: MasterGameState): CardContent {
  const { players, currentNightRole, currentNightStepKind, pendingNightActionTargetId, nightActionResult } = state
  const content = currentNightRole ? NIGHT_ROLE_CONTENT[currentNightRole] : undefined

  if (!currentNightRole || !content) {
    return { icon: <MoonIcon />, title: 'Notte', body: 'Attendere…' }
  }

  const align = ROLE_ALIGNMENT[currentNightRole]

  if (currentNightStepKind === 'WAKE_UP') {
    return { icon: content.icon, title: content.wakeUpTitle, body: content.wakeUpBody, align }
  }

  if (!roleHasSelectableHolder(state, currentNightRole)) {
    return {
      icon: content.icon,
      title: content.selectTitle,
      align,
      body: (
        <p className="game-card-hint">
          Non c'è più nessun {ROLE_LABELS[currentNightRole].toLowerCase()} in vita: il potere non può essere usato
          questa notte.
        </p>
      ),
    }
  }

  const body =
    pendingNightActionTargetId && nightActionResult && content.renderResult
      ? content.renderResult(playerName(players, pendingNightActionTargetId), nightActionResult)
      : content.selectPrompt

  return { icon: content.icon, title: content.selectTitle, body, align }
}

function buildCard(state: MasterGameState): CardContent {
  const { phase, players, lastNightVictimIds, winner, winningRole, remoteJoin } = state

  switch (phase) {
    case 'ROLES_ASSIGNED':
      return {
        icon: <EyeIcon />,
        title: 'I ruoli sono stati assegnati',
        body: remoteJoin
          ? 'Dite a tutti i giocatori di controllare in silenzio il proprio ruolo.'
          : 'Apri il Villaggio e comunica in privato a ciascun giocatore il proprio ruolo.',
      }
    case 'NIGHT_START':
      return {
        icon: <MoonIcon />,
        title: 'Cala la notte',
        body: 'Il villaggio si addormenta. Tutti chiudano gli occhi.',
      }
    case 'NIGHT_ACTIONS':
      return buildNightActionsCard(state)
    case 'MORNING_REVEAL': {
      // lastNightVictimIds is who was targeted, not necessarily who died (e.g. the
      // survivor's extra life can absorb a werewolf hit) — only narrate actual deaths.
      const victimNames = lastNightVictimIds
        .filter((id) => players.find((player) => player.id === id)?.alive === false)
        .map((id) => playerName(players, id))
      let body: string
      if (victimNames.length === 0) {
        body = 'Questa notte nessuno è morto.'
      } else if (victimNames.length === 1) {
        body = `Questa notte è morto/a ${victimNames[0]}.`
      } else {
        body = `Questa notte sono morti/e ${victimNames.join(' e ')}.`
      }
      return { icon: <SunIcon />, title: 'Il villaggio si sveglia', body }
    }
    case 'DISCUSSION':
      return {
        icon: <SunIcon />,
        title: 'Discussione',
        body: 'Il villaggio discute su chi potrebbe essere un lupo mannaro.',
      }
    case 'VOTE_SELECT_TARGET':
      return {
        icon: <SunIcon />,
        title: 'Chi ha votato il villaggio?',
        body: 'Seleziona dalla tavola il giocatore votato, oppure avanza se nessuno è stato eliminato.',
      }
    case 'GAME_OVER':
      // winningRole is only ever 'IDIOT' today — if a second solo-win role is
      // added, this should become a small lookup table instead of one check.
      if (winningRole === 'IDIOT') {
        return {
          icon: <SkullIcon />,
          title: 'Partita conclusa',
          body: 'Il villaggio ha votato ed eliminato l\'idiota: vince da solo!',
        }
      }
      return {
        icon: <SkullIcon />,
        title: 'Partita conclusa',
        body: winner === 'EVIL' ? 'Hanno vinto i lupi mannari!' : 'Ha vinto il villaggio!',
      }
  }
}

/**
 * Whether a living player still holds {@code role}. A deferred kill
 * (werewolves' or the corrupted judge's) only takes effect the following
 * day, so a pending target is still fully able to act on their own turn
 * tonight — mirrors the backend's roleHasSelectableHolder.
 */
function roleHasSelectableHolder(state: MasterGameState, role: Role): boolean {
  return state.players.some((player) => player.alive && player.role === role)
}

/** Roles targeting the dead rather than the living, e.g. the gravedigger. */
function roleTargetsDeadPlayers(role: Role): boolean {
  return role === 'GRAVEDIGGER'
}

/** Roles whose power is optional even when a target is available — mirrors the backend's requiresSelection. */
function roleRequiresSelection(role: Role): boolean {
  return role !== 'CORRUPTED_JUDGE'
}

/**
 * Whether {@code role} has anyone it could select at all right now.
 * Roles targeting the living always do (there's always a table to
 * choose from); a dead-target role has nothing to select until at
 * least one player has died — mirrors the backend's roleHasEligibleTarget.
 */
function hasEligibleNightTarget(state: MasterGameState, role: Role): boolean {
  if (!roleTargetsDeadPlayers(role)) return true
  return state.players.some((player) => !player.alive)
}

function selectablePlayers(state: MasterGameState): MasterPlayerView[] {
  if (state.phase === 'NIGHT_ACTIONS' && state.currentNightRole && roleTargetsDeadPlayers(state.currentNightRole)) {
    return state.players.filter((player) => !player.alive)
  }
  const alive = state.players.filter((player) => player.alive)
  if (state.phase === 'NIGHT_ACTIONS' && state.currentNightRole === 'WEREWOLF') {
    return alive.filter((player) => player.role !== 'WEREWOLF')
  }
  return alive
}

function MasterGamePage() {
  const { code } = useParams<{ code: string }>()
  const { showAlert } = useDialog()
  const { masterToken, handleForbidden } = useMasterAccess(code)

  const [state, setState] = useState<MasterGameState | null>(null)
  const [busy, setBusy] = useState(false)
  const [villageOpen, setVillageOpen] = useState(false)

  const refresh = useCallback(() => {
    if (!code || !masterToken) return
    getGameState(code, masterToken).then(setState).catch(handleForbidden)
  }, [code, masterToken, handleForbidden])

  useEffect(() => {
    if (!code || !masterToken) return

    refresh()
    const unsubscribe = subscribeToGame(code, refresh)
    return unsubscribe
  }, [code, masterToken, refresh])

  if (!state) {
    return (
      <BoardPanel>
        <h1>Partita in corso</h1>
        <p>Caricamento…</p>
      </BoardPanel>
    )
  }

  const card = buildCard(state)
  const isNightSelectStep = state.phase === 'NIGHT_ACTIONS' && state.currentNightStepKind === 'SELECT'
  const isVotePhase = state.phase === 'VOTE_SELECT_TARGET'
  const canSelectTonight =
    isNightSelectStep &&
    state.currentNightRole !== null &&
    roleHasSelectableHolder(state, state.currentNightRole) &&
    hasEligibleNightTarget(state, state.currentNightRole)
  const nightSelectionRequired =
    canSelectTonight && state.currentNightRole !== null && roleRequiresSelection(state.currentNightRole)
  const showSelectionGrid = canSelectTonight || isVotePhase
  const selectedId = isNightSelectStep
    ? state.pendingNightActionTargetId
    : isVotePhase
      ? state.pendingVoteVictimId
      : null
  const canAdvance = isVotePhase || !nightSelectionRequired || selectedId !== null

  const handleSelect = async (playerId: number) => {
    if (!code || !masterToken || busy) return
    setBusy(true)
    try {
      if (isNightSelectStep) {
        setState(await selectNightTarget(code, masterToken, playerId))
      } else if (isVotePhase) {
        setState(await selectVoteVictim(code, masterToken, selectedId === playerId ? null : playerId))
      }
    } catch {
      showAlert('Impossibile registrare la selezione. Riprova.')
    } finally {
      setBusy(false)
    }
  }

  const handleAdvance = async () => {
    if (!code || !masterToken || busy) return
    setBusy(true)
    try {
      setState(await advancePhase(code, masterToken))
    } catch (err) {
      showAlert(err instanceof ApiError ? err.message : 'Impossibile avanzare. Riprova.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <BoardPanel>
      <h1>Partita in corso</h1>
      <p className="game-round">Round {state.roundNumber}</p>

      <button type="button" className="button" onClick={() => setVillageOpen(true)}>
        Villaggio
      </button>
      <VillageOverviewDialog
        open={villageOpen}
        onClose={() => setVillageOpen(false)}
        players={state.players.map((player) => ({
          id: player.id,
          nickname: player.nickname,
          alive: player.alive,
          role: player.role,
        }))}
      />

      <div className="game-card">
        <div className={'game-card-icon' + (card.align ? ' align-' + card.align.toLowerCase() : '')}>{card.icon}</div>
        <h2 className="game-card-title">{card.title}</h2>
        <div className="game-card-body">{card.body}</div>
      </div>

      {showSelectionGrid && (
        <div className="player-tokens game-selection-grid">
          {selectablePlayers(state).map((player) => (
            <button
              key={player.id}
              type="button"
              className={'game-selectable-token' + (selectedId === player.id ? ' is-selected' : '')}
              onClick={() => handleSelect(player.id)}
              disabled={busy}
            >
              <span className="player-token-name">{player.nickname}</span>
            </button>
          ))}
        </div>
      )}

      <div className="game-actions">
        {state.phase !== 'GAME_OVER' && (
          <button type="button" className="button button-primary" onClick={handleAdvance} disabled={!canAdvance || busy}>
            {busy ? 'Attendere…' : 'Avanti'}
          </button>
        )}

        {state.phase === 'GAME_OVER' && (
          <Link to="/" className="button button-primary">
            Torna alla home
          </Link>
        )}
      </div>
    </BoardPanel>
  )
}

export default MasterGamePage
