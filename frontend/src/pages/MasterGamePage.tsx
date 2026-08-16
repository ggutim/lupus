import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
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
import { getMasterToken } from '../api/masterToken'
import { subscribeToGame } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import VillageOverviewDialog from '../components/VillageOverviewDialog'
import { useDialog } from '../components/useDialog'
import { EyeIcon, GravediggerIcon, MoonIcon, PriestIcon, SkullIcon, SunIcon, WerewolfIcon } from '../components/icons'
import type { ReactNode } from 'react'

interface CardContent {
  icon: ReactNode
  title: string
  body: ReactNode
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

  if (currentNightStepKind === 'WAKE_UP') {
    return { icon: content.icon, title: content.wakeUpTitle, body: content.wakeUpBody }
  }

  const body =
    pendingNightActionTargetId && nightActionResult && content.renderResult
      ? content.renderResult(playerName(players, pendingNightActionTargetId), nightActionResult)
      : content.selectPrompt

  return { icon: content.icon, title: content.selectTitle, body }
}

function buildCard(state: MasterGameState): CardContent {
  const { phase, players, lastNightVictimId, winner, winningRole } = state

  switch (phase) {
    case 'ROLES_ASSIGNED':
      return {
        icon: <EyeIcon />,
        title: 'I ruoli sono stati assegnati',
        body: 'Dite a tutti i giocatori di controllare in silenzio il proprio ruolo.',
      }
    case 'NIGHT_START':
      return {
        icon: <MoonIcon />,
        title: 'Cala la notte',
        body: 'Il villaggio si addormenta. Tutti chiudano gli occhi.',
      }
    case 'NIGHT_ACTIONS':
      return buildNightActionsCard(state)
    case 'MORNING_REVEAL':
      return {
        icon: <SunIcon />,
        title: 'Il villaggio si sveglia',
        body: lastNightVictimId
          ? `Questa notte è morto/a ${playerName(players, lastNightVictimId)}.`
          : 'Questa notte nessuno è morto.',
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
 * Whether a living player still holds {@code role}, excluding whoever
 * the werewolves have already picked as this round's victim (that
 * kill isn't applied until morning) — mirrors the backend's
 * roleHasSelectableHolder so the "Avanti" button doesn't stay
 * disabled waiting for a selection nobody can make.
 */
function roleHasSelectableHolder(state: MasterGameState, role: Role): boolean {
  return state.players.some(
    (player) => player.alive && player.id !== state.lastNightVictimId && player.role === role,
  )
}

/** Roles targeting the dead rather than the living, e.g. the gravedigger. */
function roleTargetsDeadPlayers(role: Role): boolean {
  return role === 'GRAVEDIGGER'
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
  const navigate = useNavigate()
  const { showAlert } = useDialog()
  const masterToken = code ? getMasterToken(code) : null

  const [state, setState] = useState<MasterGameState | null>(null)
  const [busy, setBusy] = useState(false)
  const [villageOpen, setVillageOpen] = useState(false)

  const refresh = useCallback(() => {
    if (!code || !masterToken) return
    getGameState(code, masterToken)
      .then(setState)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 403) {
          showAlert({
            title: 'Accesso negato',
            message: 'Non risulti essere il narratore di questa stanza.',
          }).then(() => navigate('/'))
        }
      })
  }, [code, masterToken, navigate, showAlert])

  useEffect(() => {
    if (!code) return

    if (!masterToken) {
      showAlert({
        title: 'Accesso non disponibile',
        message: 'Non risulti essere il narratore di questa stanza su questo dispositivo.',
      }).then(() => navigate('/'))
      return
    }

    refresh()
    const unsubscribe = subscribeToGame(code, refresh)
    return unsubscribe
  }, [code, masterToken, navigate, showAlert, refresh])

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
  const nightSelectionRequired =
    isNightSelectStep &&
    state.currentNightRole !== null &&
    roleHasSelectableHolder(state, state.currentNightRole) &&
    hasEligibleNightTarget(state, state.currentNightRole)
  const showSelectionGrid = (isNightSelectStep && nightSelectionRequired) || isVotePhase
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
        <div className="game-card-icon">{card.icon}</div>
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
    </BoardPanel>
  )
}

export default MasterGamePage
