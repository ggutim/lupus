import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  advancePhase,
  assignMayorSuccessor,
  getGameState,
  revealKillerAndGuess,
  revealMayor,
  selectNightTarget,
  selectVoteVictim,
  type MasterGameState,
} from '../api/game'
import { ApiError, type Role } from '../api/rooms'
import { subscribeToGame } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import KillerGuessDialog from '../components/KillerGuessDialog'
import MayorSuccessionDialog from '../components/MayorSuccessionDialog'
import SpecialPowersDialog, { type SpecialPowerAction } from '../components/SpecialPowersDialog'
import VillageOverviewDialog from '../components/VillageOverviewDialog'
import { useDialog } from '../components/useDialog'
import { useMasterAccess } from '../components/useMasterAccess'
import { ROLE_LABELS } from '../roleLabels'
import {
  buildCard,
  hasEligibleNightTarget,
  playerName,
  revealableKiller,
  revealableMayor,
  roleHasSelectableHolder,
  roleRequiresSelection,
  selectablePlayers,
  toRomanNumeral,
} from '../masterGameSelectors'

function MasterGamePage() {
  const { code } = useParams<{ code: string }>()
  const { showAlert, showConfirm } = useDialog()
  const { masterToken, handleForbidden } = useMasterAccess(code)

  const [state, setState] = useState<MasterGameState | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [villageOpen, setVillageOpen] = useState(false)
  const [killerGuessOpen, setKillerGuessOpen] = useState(false)
  const [specialPowersOpen, setSpecialPowersOpen] = useState(false)

  const refresh = useCallback(() => {
    if (!code || !masterToken) return
    getGameState(code, masterToken)
      .then((data) => {
        setState(data)
        setLoadError(null)
      })
      .catch((err) => {
        handleForbidden(err)
        // Only surfaced while still on the initial "Caricamento…" screen (see the
        // `!state` branch below) — a background refresh failing mid-game shouldn't
        // discard an already-rendered state, since the next WS push retries anyway.
        if (!(err instanceof ApiError && err.status === 403)) {
          setLoadError(err instanceof ApiError ? err.message : 'Impossibile recuperare lo stato della partita.')
        }
      })
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
        {loadError ? (
          <>
            <p className="error">{loadError}</p>
            <button type="button" className="button" onClick={refresh}>
              Riprova
            </button>
          </>
        ) : (
          <p>Caricamento…</p>
        )}
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
  const isGhostCurseTurn = isNightSelectStep && state.currentNightRole === 'GHOST'
  const selectedId = isNightSelectStep
    ? state.pendingNightActionTargetId
    : isVotePhase
      ? state.pendingVoteVictimId
      : null
  const selectedIds = isGhostCurseTurn
    ? [state.pendingNightActionTargetId, state.secondPendingNightActionTargetId].filter(
        (id): id is number => id !== null,
      )
    : selectedId !== null
      ? [selectedId]
      : []
  const ghostCurseRequired = isGhostCurseTurn ? Math.min(2, selectablePlayers(state).length) : 0
  const canAdvance =
    state.pendingMayorSuccessionPlayerId === null &&
    (isVotePhase ||
      !nightSelectionRequired ||
      (isGhostCurseTurn ? selectedIds.length >= ghostCurseRequired : selectedId !== null))

  const handleSelect = async (playerId: number) => {
    if (!code || !masterToken || busy) return
    setBusy(true)
    try {
      if (isNightSelectStep) {
        setState(await selectNightTarget(code, masterToken, playerId))
      } else if (isVotePhase) {
        setState(await selectVoteVictim(code, masterToken, selectedId === playerId ? null : playerId))
      }
    } catch (err) {
      showAlert(err instanceof ApiError ? err.message : 'Impossibile registrare la selezione. Riprova.')
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

  const handleKillerGuess = async (targetPlayerId: number, guessedRole: Role) => {
    if (!code || !masterToken || busy) return
    setBusy(true)
    try {
      const targetName = playerName(state.players, targetPlayerId)
      const result = await revealKillerAndGuess(code, masterToken, targetPlayerId, guessedRole)
      setState(result.gameState)
      setKillerGuessOpen(false)
      await showAlert({
        title: result.correct ? 'Il killer indovina!' : 'Il killer sbaglia!',
        message: result.correct
          ? `Il ruolo di ${targetName} era proprio ${ROLE_LABELS[guessedRole]}: muore.`
          : `${targetName} non era ${ROLE_LABELS[guessedRole]}: il killer muore al suo posto.`,
      })
    } catch (err) {
      showAlert(err instanceof ApiError ? err.message : 'Impossibile registrare la rivelazione. Riprova.')
    } finally {
      setBusy(false)
    }
  }

  const handleMayorReveal = async () => {
    if (!code || !masterToken || busy) return
    const confirmed = await showConfirm({
      title: 'Il sindaco si rivela',
      message: 'Da questo momento tutti sapranno chi è il sindaco, e il suo voto conterà doppio. Confermi?',
      confirmLabel: 'Rivela',
      cancelLabel: 'Annulla',
    })
    if (!confirmed) return

    setBusy(true)
    try {
      setState(await revealMayor(code, masterToken))
    } catch (err) {
      showAlert(err instanceof ApiError ? err.message : 'Impossibile registrare la rivelazione. Riprova.')
    } finally {
      setBusy(false)
    }
  }

  const handleMayorSuccession = async (successorPlayerId: number) => {
    if (!code || !masterToken || busy) return
    setBusy(true)
    try {
      setState(await assignMayorSuccessor(code, masterToken, successorPlayerId))
    } catch (err) {
      showAlert(err instanceof ApiError ? err.message : 'Impossibile registrare il nuovo sindaco. Riprova.')
    } finally {
      setBusy(false)
    }
  }

  const killer = revealableKiller(state)
  const mayor = revealableMayor(state)
  const specialPowerActions: SpecialPowerAction[] = [
    killer ? { key: 'killer', label: 'Il killer si rivela…', onSelect: () => setKillerGuessOpen(true) } : null,
    mayor ? { key: 'mayor', label: 'Il sindaco si rivela…', onSelect: handleMayorReveal } : null,
  ].filter((action): action is SpecialPowerAction => action !== null)

  return (
    <BoardPanel>
      <h1>Round {toRomanNumeral(state.roundNumber)}</h1>

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
          originalRole: player.originalRole,
          mayor: player.mayor,
        }))}
      />

      <div className="game-card">
        <div className={'game-card-icon' + (card.align ? ' align-' + card.align.toLowerCase() : '')}>{card.icon}</div>
        <h2 className="game-card-title">{card.title}</h2>
        <div className="game-card-body">{card.body}</div>
      </div>

      {showSelectionGrid && (
        <div className="player-tokens game-selection-grid">
          {selectablePlayers(state).map((player) => {
            const isSelected = selectedIds.includes(player.id)
            const ghostSlotsFull = isGhostCurseTurn && selectedIds.length >= 2
            return (
              <button
                key={player.id}
                type="button"
                className={'game-selectable-token' + (isSelected ? ' is-selected' : '')}
                onClick={() => handleSelect(player.id)}
                disabled={busy || (ghostSlotsFull && !isSelected)}
              >
                <span className="player-token-name">{player.nickname}</span>
              </button>
            )
          })}
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

      {specialPowerActions.length > 0 && (
        <button type="button" className="special-powers-button" onClick={() => setSpecialPowersOpen(true)}>
          Poteri speciali…
        </button>
      )}
      <SpecialPowersDialog
        open={specialPowersOpen}
        onClose={() => setSpecialPowersOpen(false)}
        actions={specialPowerActions}
      />
      <KillerGuessDialog
        open={killerGuessOpen}
        onClose={() => setKillerGuessOpen(false)}
        busy={busy}
        targets={killer ? state.players.filter((player) => player.alive && player.id !== killer.id) : []}
        onConfirm={handleKillerGuess}
      />

      <MayorSuccessionDialog
        open={state.pendingMayorSuccessionPlayerId !== null}
        deadMayorName={playerName(state.players, state.pendingMayorSuccessionPlayerId)}
        busy={busy}
        targets={state.players.filter((player) => player.alive)}
        onConfirm={handleMayorSuccession}
      />
    </BoardPanel>
  )
}

export default MasterGamePage
