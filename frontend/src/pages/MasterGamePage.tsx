import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  advancePhase,
  getGameState,
  selectPriestTarget,
  selectVoteVictim,
  selectWerewolfVictim,
  type GamePhase,
  type MasterGameState,
  type MasterPlayerView,
} from '../api/game'
import { ApiError } from '../api/rooms'
import { getMasterToken } from '../api/masterToken'
import { subscribeToGame } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import VillageOverviewDialog from '../components/VillageOverviewDialog'
import { useDialog } from '../components/useDialog'
import { EyeIcon, MoonIcon, PriestIcon, SkullIcon, SunIcon, WerewolfIcon } from '../components/icons'
import type { ReactNode } from 'react'

interface CardContent {
  icon: ReactNode
  title: string
  body: ReactNode
}

function playerName(players: MasterPlayerView[], id: number | null): string {
  if (id === null) return '—'
  return players.find((player) => player.id === id)?.nickname ?? '—'
}

function buildCard(state: MasterGameState): CardContent {
  const { phase, players, lastNightVictimId, priestCheckResult, pendingPriestTargetId, winner } = state

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
    case 'WEREWOLVES_WAKE_UP':
      return {
        icon: <WerewolfIcon />,
        title: 'I lupi mannari si svegliano',
        body: 'I lupi aprono gli occhi e decidono in silenzio chi sbranare.',
      }
    case 'WEREWOLVES_SELECT_VICTIM':
      return {
        icon: <WerewolfIcon />,
        title: 'Chi hanno scelto i lupi?',
        body: 'Seleziona dalla tavola il giocatore scelto dai lupi mannari.',
      }
    case 'PRIEST_WAKE_UP':
      return {
        icon: <PriestIcon />,
        title: 'Il sacerdote si sveglia',
        body: 'Il sacerdote apre gli occhi e sceglie chi vedere.',
      }
    case 'PRIEST_SELECT_TARGET':
      return {
        icon: <PriestIcon />,
        title: 'Chi vuole vedere il sacerdote?',
        body: pendingPriestTargetId ? (
          <>
            <p>
              {playerName(players, pendingPriestTargetId)} è{' '}
              <strong>{priestCheckResult === 'EVIL' ? 'malvagio' : 'buono'}</strong>.
            </p>
            <p className="game-card-hint">Comunicalo in silenzio al sacerdote.</p>
          </>
        ) : (
          'Seleziona dalla tavola il giocatore scelto dal sacerdote.'
        ),
      }
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
      return {
        icon: <SkullIcon />,
        title: 'Partita conclusa',
        body: winner === 'EVIL' ? 'Hanno vinto i lupi mannari!' : 'Ha vinto il villaggio!',
      }
  }
}

const SELECTION_PHASES: GamePhase[] = ['WEREWOLVES_SELECT_VICTIM', 'PRIEST_SELECT_TARGET', 'VOTE_SELECT_TARGET']

function pendingSelectionId(state: MasterGameState): number | null {
  switch (state.phase) {
    case 'WEREWOLVES_SELECT_VICTIM':
      return state.pendingWerewolfVictimId
    case 'PRIEST_SELECT_TARGET':
      return state.pendingPriestTargetId
    case 'VOTE_SELECT_TARGET':
      return state.pendingVoteVictimId
    default:
      return null
  }
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
  const isSelectionPhase = SELECTION_PHASES.includes(state.phase)
  const selectedId = pendingSelectionId(state)
  const isVotePhase = state.phase === 'VOTE_SELECT_TARGET'
  const canAdvance = !isSelectionPhase || selectedId !== null || isVotePhase

  const handleSelect = async (playerId: number) => {
    if (!code || !masterToken || busy) return
    setBusy(true)
    try {
      if (state.phase === 'WEREWOLVES_SELECT_VICTIM') {
        setState(await selectWerewolfVictim(code, masterToken, playerId))
      } else if (state.phase === 'PRIEST_SELECT_TARGET') {
        setState(await selectPriestTarget(code, masterToken, playerId))
      } else if (state.phase === 'VOTE_SELECT_TARGET') {
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

      {isSelectionPhase && (
        <div className="player-tokens game-selection-grid">
          {state.players
            .filter((player) => player.alive)
            .map((player) => (
              <button
                key={player.id}
                type="button"
                className={
                  'game-selectable-token' + (selectedId === player.id ? ' is-selected' : '')
                }
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
